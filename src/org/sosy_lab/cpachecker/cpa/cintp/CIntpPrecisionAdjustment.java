/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2020  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.sosy_lab.cpachecker.cpa.cintp;

import static com.google.common.collect.FluentIterable.from;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import de.uni_freiburg.informatik.ultimate.smtinterpol.util.ArrayQueue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression.BinaryOperator;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.ARGUtils;
import org.sosy_lab.cpachecker.cpa.arg.path.ARGPath;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateCPA;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.predicates.AbstractionManager;
import org.sosy_lab.cpachecker.util.predicates.AbstractionPredicate;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormula;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.pathformula.SSAMap;
import org.sosy_lab.cpachecker.util.predicates.pathformula.pointeraliasing.PointerTargetSet;
import org.sosy_lab.cpachecker.util.predicates.regions.RegionCreator;
import org.sosy_lab.cpachecker.util.predicates.smt.FormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.smt.Solver;
import org.sosy_lab.cpachecker.util.statistics.ThreadSafeTimerContainer.TimerWrapper;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.BooleanFormulaManager;
import org.sosy_lab.java_smt.api.InterpolatingProverEnvironment;
import org.sosy_lab.java_smt.api.SolverException;

@Options(prefix = "cpa.cintp")
public class CIntpPrecisionAdjustment implements PrecisionAdjustment {

  @Option(
    secure = true,
    description = "With this option enabled, the optimizaiton strategy, namely "
        + "Incremental C-Intp (IC-Intp), is applied in the process of generating conditional interpolations.")
  private boolean useIncCIntp = false;

  @Option(
    secure = true,
    name = "abstraction.type",
    toUppercase = true,
    values = {"BDD", "FORMULA"},
    description = "What to use for storing abstractions")
  private String abstractionType = "BDD";

  // C-Intp environment.
  private PredicateCPA cpa;
  private Solver solver;
  private FormulaManagerView fmgr;
  private PathFormulaManager pfmgr;
  private AbstractionManager amgr;
  private BooleanFormulaManager bfmgr;
  private RegionCreator rmgr;

  //
  private final AbstractionPredicate truePred;
  private final AbstractionPredicate falsePred;
  private final BooleanFormula trueFormula;
  private final BooleanFormula falseFormula;
  private final PathFormula emptyPathFormula;
  private final Pair<AbstractionPredicate, Set<AbstractionPredicate>> truePredPair;

  // for IC-Intp.
  private AbstractState evalRootState;

  private final LogManager logger;
  private final ShutdownNotifier shutdownNotifier;
  private final CIntpStatistics statistics;
  private final TimerWrapper concreteCIntpTime;
  private final TimerWrapper overallCIntpTime;
  private final TimerWrapper genPathTime;

  // the cached C-Intp predicates at a path instance.
  // [<path_instance, {pred_1, ...}>, ...]
  private static Map<Integer, Pair<AbstractionPredicate, Set<AbstractionPredicate>>> cintpCache;
  // the formula to predicate map, mainly used for avoid generating repeat predicates.
  // [<formula, predicate>, ...]
  private static Map<BooleanFormula, AbstractionPredicate> formulaPredicateMap = new HashMap<>();
  // the cached edge evaluation information.
  // [<edge_hash, {eval_1, ...}>, ...]
  private Map<Integer, Set<?>> edgeEvaluationInfo;
  // the cached path instance formula, mainly used for avoid calculating edge formula repeatedly.
  // [<path_instance, path_instance_formula>, ...]
  private Map<Integer, PathFormula> cachedPathInstanceFormula;
  //
  public static AbstractionPredicate curPred = null;

  public CIntpPrecisionAdjustment(
      Configuration pConfig,
      LogManager pLogger,
      ShutdownNotifier pShutdownNotifier,
      CIntpStatistics pStatistics,
      PredicateCPA pCpa,
      CFA pCfa)
      throws InvalidConfigurationException {
    pConfig.inject(this);

    logger = pLogger;
    shutdownNotifier = pShutdownNotifier;
    statistics = pStatistics;
    concreteCIntpTime = statistics.cintpTimer.getNewTimer();
    genPathTime = statistics.pathGenerationTimer.getNewTimer();
    overallCIntpTime = statistics.cintpOverallTimer.getNewTimer();

    cpa = pCpa;
    solver = pCpa.getSolver();
    fmgr = solver.getFormulaManager();
    pfmgr = cpa.getPathFormulaManager();
    amgr = cpa.getAbstractionManager();
    bfmgr = fmgr.getBooleanFormulaManager();
    rmgr = amgr.getRegionCreator();

    truePred = amgr.makePredicate(bfmgr.makeTrue());
    falsePred = amgr.makeFalsePredicate();
    trueFormula = bfmgr.makeTrue();
    falseFormula = bfmgr.makeFalse();
    emptyPathFormula =
        new PathFormula(
            trueFormula,
            SSAMap.emptySSAMap(),
            PointerTargetSet.emptyPointerTargetSet(),
            0);
    truePredPair = Pair.of(truePred, Sets.newHashSet(truePred));

    evalRootState = null;

    cintpCache = new HashMap<>();
    //    formulaPredicateMap = new HashMap<>();
    edgeEvaluationInfo = useIncCIntp ? extractEdgeEvaluationInfo(pLogger, pCfa) : new HashMap<>();
    cachedPathInstanceFormula = new HashMap<>();
    cachedPathInstanceFormula.put(0, emptyPathFormula);
  }

  private Map<Integer, Set<?>> extractEdgeEvaluationInfo(LogManager pLogger, CFA pCfa) {
    EdgeEvaluationExtractor edgeEvalInfoExtractor = new EdgeEvaluationExtractor(pLogger);
    Map<Integer, Set<?>> results = new HashMap<>();

    // get entry point of all the functions in the given program.
    Iterator<FunctionEntryNode> funcIter = pCfa.getAllFunctionHeads().iterator();
    Set<Integer> finishedEdges = new HashSet<>();
    // iteratively process each function.
    while (funcIter.hasNext()) {
      FunctionEntryNode func = funcIter.next();
      Queue<CFAEdge> edgeQueue = new ArrayQueue<>();

      // bfs strategy.
      edgeQueue.add(func.getLeavingEdge(0));
      while (!edgeQueue.isEmpty()) {
        CFAEdge edge = edgeQueue.remove();
        Integer edgeHash = edge.hashCode();

        // this edge is not processed.
        if (!finishedEdges.contains(edgeHash)) {
          // get the evaluation information.
          results.put(edgeHash, edgeEvalInfoExtractor.extractEdgeEvaluationInfo(edge));
          finishedEdges.add(edgeHash);

          // System.out.println(edge + "\t" + results.get(edgeHash));

          // process its successor edge.
          CFANode edgeSucNode = edge.getSuccessor();
          for (int i = 0; i < edgeSucNode.getNumLeavingEdges(); ++i) {
            edgeQueue.add(edgeSucNode.getLeavingEdge(i));
          }
        }
      }
    }

    return results;
  }

  @SuppressWarnings({"null", "rawtypes", "unchecked"})
  @Override
  public Optional<PrecisionAdjustmentResult> prec(
      AbstractState pState,
      Precision pPrecision,
      UnmodifiableReachedSet pStates,
      Function<AbstractState, AbstractState> pStateProjection,
      AbstractState pFullState)
      throws CPAException, InterruptedException {
    overallCIntpTime.start();
    curPred = null;

    CIntpState cintpState = (CIntpState) pState;
    Integer childPathInstance = cintpState.getPathInstance();
    CFANode curLoc = AbstractStates.extractLocation(pFullState);

    assert pFullState instanceof ARGState;
    ARGState argCurrent = (ARGState) pFullState,
        argParent = getRealParentARGState(argCurrent, cintpState.getCurEdgeHash());
    CIntpState parentCIntpState = AbstractStates.extractStateByType(argParent, CIntpState.class);
    CFAEdge curEdge = argParent.getEdgeToChild(argCurrent);

    // update the path instance formula.
    updateCachedPathFormula(argParent, childPathInstance, curEdge);
    if (argCurrent.getStateId() == 734) {
      int i = 0;
      ++i;
    }

    // if current edge is unsatisfiable, we skip it.
    // if (parentCIntpState.getUnsatEdgeHash() == curEdge.hashCode()) {
    // return Optional.empty();
    // }

    // if the current location does not contain any successors of AssumeEdge, we need not perform
    // the conditional interpolation.
    if (!needPerformCIntp(curLoc)) {
      overallCIntpTime.stop();
      cintpState.addPredicate(truePred);
      return Optional.of(
          PrecisionAdjustmentResult
              .create(pState, pPrecision, PrecisionAdjustmentResult.Action.CONTINUE));
    }

    // if some predicates have already computed (before the previous refinement), we just use the
    // cache to avoid repeated C-Intp computation.
    Pair<AbstractionPredicate, Set<AbstractionPredicate>> cachedPredicates =
        cintpCache.get(cintpState.getPathInstance());
    if (cachedPredicates != null) {
      statistics.numUsedCache.inc();
      overallCIntpTime.stop();
      curPred = cachedPredicates.getFirst(); // /////
      cintpState.addPredicate(cachedPredicates.getFirst());
      return Optional.of(
          PrecisionAdjustmentResult
              .create(cintpState, pPrecision, PrecisionAdjustmentResult.Action.CONTINUE));
    }
    //    System.out.println("\n\n\n\n\n\n");
    //// no cache, compute the conditional interpolations directly.
    concreteCIntpTime.start();

    // generate the path formula.
    genPathTime.start();
    List<BooleanFormula> pathFormulas = null;
    if (useIncCIntp) {
      Pair<Boolean, List<BooleanFormula>> pathFormulasInfo =
          generateICIntpFormulaChain(argCurrent, curLoc.getLeavingEdge(0));
      if (pathFormulasInfo.getFirst()) {
        statistics.numSucICIntpPathGen.inc();
        pathFormulas = pathFormulasInfo.getSecond();
      } else {
        // if the construction of the interpolation path formula chain is failed, we should not
        // perform the conditional interpolation by re-building a full path (i.e., call the function
        // 'generatePathFormulas(...)'), since the variables in the assume edge may be
        // un-initialized or non-deterministic, and the SMT solver will always return UnSAT (i.e.,
        // it's useless and even time consuming).
        statistics.numFailICIntpPathGen.inc();
        logger.log(
            Level.FINE,
            "cannot construct the interpolation path formula at state s"
                + argCurrent.getStateId()
                + ": "
                + argCurrent);
        cintpState.addPredicate(truePred);
        cintpCache.put(cintpState.getPathInstance(), truePredPair);

        genPathTime.stop();
        concreteCIntpTime.stop();
        overallCIntpTime.stop();
        return Optional.of(
            PrecisionAdjustmentResult.create(
                cintpState, pPrecision, PrecisionAdjustmentResult.Action.CONTINUE));
        //        pathFormulas = generatePathFormulas(argCurrent);
      }
    } else {
      pathFormulas = generatePathFormulas(argCurrent);
    }
    genPathTime.stop();
    statistics.numTotalIntpPathLength.setNextValue(pathFormulas.size() + 1);

    for (int i = 0; i < curLoc.getNumLeavingEdges(); ++i) {
      // create prove environment.
      try (InterpolatingProverEnvironment<?> prover =
          solver.newProverEnvironmentWithInterpolation()) {
        CFAEdge sucAssumeEdge = curLoc.getLeavingEdge(i);
        assert sucAssumeEdge instanceof AssumeEdge;
        //        System.out.println(sucAssumeEdge);
        // complete the interpolation path formula.
        BooleanFormula sucAssumeEdgeFormula =
            generateNewPathFormula(cachedPathInstanceFormula.get(childPathInstance), sucAssumeEdge)
                .getFormula();
        pathFormulas.add(sucAssumeEdgeFormula);

        // push these formulas into the prover.
        List handles = new ArrayList<>();
        for (int j = 0; j < pathFormulas.size(); ++j) {
          handles.add(prover.addConstraint(pathFormulas.get(j)));
        }

        boolean unsat = prover.isUnsat();

        // if unsatisfible, we perform the interpolation.
        if (unsat) {
          // System.out.println(argCurrent.getStateId() + ": " + pathFormulas);
          cintpState.setUnsatEdgeHash(sucAssumeEdge.hashCode());
          List<BooleanFormula> itps = generatePathFormulaInterpolations(prover, handles);
          Pair<AbstractionPredicate, Set<AbstractionPredicate>> preds =
              generatePathInterpolationPredicates(itps);

          //          if (bfmgr.isFalse(preds.getFirst().getSymbolicAtom())) {
          //            System.out.println(argCurrent.getStateId() + ": generated a false
          // predicate.");
          //          }

          // System.out
          // .println(argCurrent.getStateId() + "\t" + childPathInstance + "\t" + preds + "\t");
          // update the C-Intp predicate set.
          curPred = preds.getFirst();
          cintpState.addPredicate(preds.getFirst());
          cintpCache.put(childPathInstance, preds);
          statistics.numGeneratedPredicates.setNextValue(preds.getSecond().size());
          break;
        }

        // if no conditional interpolants are generated, we try another assume edge.
        pathFormulas.remove(pathFormulas.size() - 1);
      } catch (Exception e) {
        logger.log(
            Level.WARNING,
            "exception occured when interpolating the path formula: " + pathFormulas);
        e.printStackTrace();
      }
    }
    concreteCIntpTime.stop();
    overallCIntpTime.stop();

    // if (cintpState.getPredicates().size() > 0) {
    // return Optional.empty();
    // }

    if (cintpState.getPredicates().isEmpty()) {
      cintpState.addPredicate(truePred);
    }
    return Optional.of(
        PrecisionAdjustmentResult
            .create(cintpState, pPrecision, PrecisionAdjustmentResult.Action.CONTINUE));
  }

  /**
   * This function generates the conditional interpolations of the given path formula.
   *
   * @param prover The prover that is used to perform Craig interpolation.
   * @param handles A list of formula handles ?
   * @return A list of generated conditional interpolations.
   * @throws SolverException The exception caused by the interpolation prover.
   * @throws InterruptedException The exception caused by the interpolation prover.
   *
   * @implNote The process of interpolation is shown as follow:
   * @implNote Suppose that the interpolation formula list is:
   * @implNote L = [F1, F2, F3, F4, F5]
   * @implNote The interpolator calculates interpolation of the sub-formula list L[0:i], (i = 0, 1,
   *           2, 3), i.e.,
   * @implNote [F1] - [F2, F3, F4, F5]
   * @implNote [F1, F2] - [F3, F4, F5]
   * @implNote [F1, F2, F3] - [F4, F5]
   * @implNote [F1, F2, F3, F4] - [F5]
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  private List<BooleanFormula>
      generatePathFormulaInterpolations(InterpolatingProverEnvironment<?> prover, List handles)
          throws SolverException, InterruptedException {
    assert handles.size() > 1;

    List<BooleanFormula> results = new ArrayList<>();

    // perform the conditional interpolation.
    for (int i = 0; i < handles.size() - 1; ++i) {
      BooleanFormula itp = prover.getInterpolant(handles.subList(0, i + 1));
      results.add(itp);
    }

    return results;
  }

  /**
   * This function generates the conditional interpolations of the given path formula.
   *
   * @param prover The prover that is used to perform Craig interpolation.
   * @param handles A list of formula handles ?
   * @return A list of generated conditional interpolations.
   * @throws SolverException The exception caused by the interpolation prover.
   * @throws InterruptedException The exception caused by the interpolation prover.
   *
   * @implNote The process of interpolation is shown as follow:
   * @implNote Suppose that the interpolation formula list is:
   * @implNote L = [F1, F2, F3, F4, F5]
   * @implNote The interpolator only calculates interpolation of the sub-formula list L[0:i], (i =
   *           3), i.e.,
   * @implNote [F1, F2, F3, F4] - [F5]
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  private List<BooleanFormula>
      generatePathFormulaInterpolation(InterpolatingProverEnvironment<?> prover, List handles)
          throws SolverException, InterruptedException {
    assert handles.size() > 1;

    List<BooleanFormula> results = new ArrayList<>();

    // perform the conditional interpolation.
    results.add(prover.getInterpolant(handles.subList(0, handles.size() - 1)));

    return results;
  }

  private Pair<AbstractionPredicate, Set<AbstractionPredicate>> generatePathInterpolationPredicates(
      List<BooleanFormula> pFormulas) {
    ImmutableList<BooleanFormula> formulas =
        from(pFormulas).transform(f -> fmgr.uninstantiate(f)).toList();

    // filter repeat formulas.
    Set<BooleanFormula> asbFormulas =
        from(formulas)
            .filter(f -> (!bfmgr.isTrue(f)))
            .filter(f -> !formulaPredicateMap.containsKey(f))
            .toSet();

    // generate the conditional interpolants.
    Set<AbstractionPredicate> newPreds =
        from(asbFormulas)
            .transform(
                f -> {
                  AbstractionPredicate pred = amgr.makePredicate(f);
                  formulaPredicateMap.put(f, pred);
                  return pred;
                })
            .toSet();

    // get the conditional interpolation predicate at the last state of the interpolation path.
    AbstractionPredicate assumeEdgePred =
        formulaPredicateMap.get(formulas.get(formulas.size() - 1));

    return Pair.of(assumeEdgePred, newPreds);
  }

  /**
   * This function get the real parent state of the current state.
   *
   * @param pState The current ARGState.
   * @param curEdgeHash The edge hash of the edge that between current state and parent state.
   * @return The real parent of pState.
   *
   * @implNote In an ARG, pState may have many parent states.
   */
  private ARGState getRealParentARGState(ARGState pState, Integer curEdgeHash) {
    ImmutableList<ARGState> argParents =
        from(pState.getParents()).filter(s -> s.getEdgeToChild(pState).hashCode() == curEdgeHash)
            .toList();
    assert argParents.size() == 1;

    return argParents.get(0);
  }

  private boolean needPerformCIntp(final CFANode pLoc) {
    assert pLoc != null;
    return ((pLoc.getNumLeavingEdges() > 0) && (pLoc.getLeavingEdge(0) instanceof CAssumeEdge));
  }

  private List<BooleanFormula> generatePathFormulas(ARGState pCurState) {
    // generate the path from the root to current state.
    ARGPath path = ARGUtils.getLastExploredPathTo(pCurState);
    // extract all the states in this path (remove the root state)
    final List<ARGState> pathStates = from(path.asStatesList()).skip(1).toList();

    List<BooleanFormula> edgeFormulas = new ArrayList<>();
    for (int i = 1; i < pathStates.size(); ++i) {
      Integer childPathInstance =
          AbstractStates.extractStateByType(pathStates.get(i), CIntpState.class).getPathInstance();

      // get the edge formula and filter true formula.
      BooleanFormula formula = cachedPathInstanceFormula.get(childPathInstance).getFormula();
      if (!bfmgr.isTrue(formula)) {
        edgeFormulas.add(formula);
      }
    }

    return edgeFormulas;
  }

  private PathFormula
      generateNewPathFormula(final PathFormula pOldPathFormula, final CFAEdge pEdge) {
    try {
      PathFormula tmpPathFormula =
          pfmgr.makeNewPathFormula(
              emptyPathFormula,
              pOldPathFormula.getSsa(),
              pOldPathFormula.getPointerTargetSet());
      PathFormula resultPathFormula = pfmgr.makeAnd(tmpPathFormula, pEdge);
      return resultPathFormula;
    } catch (Exception e) {
      logger.log(
          Level.SEVERE,
          "something error occured when generating the path formula of the edge: " + pEdge);
      e.printStackTrace();
    }

    return null;
  }

  /**
   * Create a new path formula.
   *
   * @param pState The precursor (parent) state of the pEdge.
   * @param pPathInstance The path instance of the successor (child) state of pEdge.
   * @param pEdge The edge that need to generate the path formula.
   */
  private void
      updateCachedPathFormula(final ARGState pState, Integer pPathInstance, final CFAEdge pEdge) {
    if (!cachedPathInstanceFormula.containsKey(pPathInstance)) {
      int parentPathInstance = AbstractStates.extractStateByType(pState, CIntpState.class).getPathInstance();
      assert cachedPathInstanceFormula.containsKey(parentPathInstance);
      PathFormula newPathFormula =
          generateNewPathFormula(cachedPathInstanceFormula.get(parentPathInstance), pEdge);

      cachedPathInstanceFormula.put(pPathInstance, newPathFormula);
    }
  }

  /**
   * This function computes the shortest conditional interpolation path.
   *
   * @param pState The precursor state of pAssumeEdge.
   * @param pAssumeEdge The conditional edge that need to perform the conditional interpolation.
   * @return It returns two elements, the first one indicates whether this function have
   *         successfully generates the shortest conditional interpolation path; and the second
   *         element is the shortest conditional interpolation path.
   */
  @SuppressWarnings("unchecked")
  private Pair<Boolean, List<BooleanFormula>>
      generateICIntpFormulaChain(ARGState pState, CFAEdge pAssumeEdge) {
    assert pAssumeEdge instanceof AssumeEdge
        && pAssumeEdge.getPredecessor().equals(AbstractStates.extractLocation(pState));

    List<BooleanFormula> pathFormulas = new ArrayList<>();

    // create the initial evaluation information.
    Set<String> leftEvalInfo =
        new HashSet<>((Set<String>) edgeEvaluationInfo.get(pAssumeEdge.hashCode()));
    Set<String> rightEvalInfo = new HashSet<>();
    Pair<Set<String>, Set<String>> phi2 = Pair.of(leftEvalInfo, rightEvalInfo);

    //
    boolean isPhi2Assume = true, isDerived = false;


    //// inverse derivation from the given state.

    ARGState curState = pState;
    Set<ARGState> visitedStates = new HashSet<>();
    visitedStates.add(curState);
    // it is used for recoding the information of formulas of conditional interpolants.
    Map<String, Integer> predFormulasInfo = new HashMap<>();
    // start exploring.
    while (!curState.getParents().isEmpty() && !curState.equals(evalRootState)) {
      // if the left and right evaluation set are both empty, we can finished this process.
      if (leftEvalInfo.isEmpty() && rightEvalInfo.isEmpty()) {
        break;
      }

      // get the parent state of curState.
      Iterator<ARGState> parents = curState.getParents().iterator();
      ARGState argParent = parents.next();
      while (!visitedStates.add(argParent) && parents.hasNext()) {
        argParent = parents.next();
      }

      //// create an edge between argParent and curState.
      CFAEdge curEdge = argParent.getEdgeToChild(curState);
      // get the evaluation information.
      Set<?> phi1 = edgeEvaluationInfo.get(curEdge.hashCode());
      //      System.out.print(curEdge);
      // ignore the assume edges, since they are helpless to the interpolation chain.
      if (!(curEdge instanceof AssumeEdge)) {
        // skip some empty evaluation edges.
        if (phi1.size() > 0) {
          // remove the conditional predicates added before.
          removeCIntpPredFormula(
              pathFormulas,
              (Set<Pair<Set<String>, Set<String>>>) phi1,
              predFormulasInfo);

          // perform the inverse derivation step.
          Pair<Boolean, Pair<Set<String>, Set<String>>> invDeriResult =
              inverseFormulaDerivation(phi1, phi2, isPhi2Assume);
          // get the derivation result, and the this result should always be an assume evaluation
          // information.
          phi2 = invDeriResult.getSecond();
          isPhi2Assume = true;

          if(invDeriResult.getFirst()) {
            //            System.out.println("\t <--");
            Integer pathInstance =
                AbstractStates.extractStateByType(curState, CIntpState.class).getPathInstance();
            pathFormulas.add(cachedPathInstanceFormula.get(pathInstance).getFormula());

            // update the derivation state.
            isDerived = true;
          }
        }
      } else {
        //// special process for the assume edge. (only process '==' or '!=' assume edge)
        BinaryOperator curEdgeExpOptr =
            ((CBinaryExpression) ((AssumeEdge) curEdge).getExpression()).getOperator();
        boolean isExpEqualRelated =
            curEdgeExpOptr.equals(BinaryOperator.EQUALS)
                || curEdgeExpOptr.equals(BinaryOperator.NOT_EQUALS);
        SetView<String> commVars = Sets.intersection((Set<String>) phi1, leftEvalInfo);
        // if the operator of curEdge is '==' or '!=', we could add the formula corresponding to
        // this edge, but we will not update the left evaluation information.
        if (isExpEqualRelated && !commVars.isEmpty()) {
          Integer pathInstance =
              AbstractStates.extractStateByType(curState, CIntpState.class).getPathInstance();
          pathFormulas.add(cachedPathInstanceFormula.get(pathInstance).getFormula());
          leftEvalInfo.removeAll(commVars);
        }

        // ignore the useless derivation of other assume edges.
        if (isDerived) {
          // the curState may performed the IC-Intp, and some conditional predicates may exists in
          // this state.

          // get the first evaluation information of this edge.
          Set<String> phi1Real = (Set<String>) phi1;
          if (phi1Real.size() > 0) {
            String phi1RealFirstEvalVar = phi1Real.iterator().next();

            /// notice:
            /// 1) we have not implement the extraction process of a predicate (too
            /// complex!!), therefore, we only process the assume edge with only one variable (and
            /// according to the feature of Craig interpolation, i.e., the result of a path formula
            /// with only one variable will only produce an interpolant with only one variable (in
            /// other words, the interpolant is an atomic interpolant)).
            /// 2) we suppose that the result of an interpolant generated by Craig interpolation
            /// contains no 'equal-related' formulas. (i.e., '==' or '!=')

            // the formula corresponding to the conditional predicate of curState is an atomic
            // formula.
            if (phi1Real.size() == 1 && leftEvalInfo.contains(phi1RealFirstEvalVar)) {
              CIntpState cintpState =
                  AbstractStates.extractStateByType(argParent, CIntpState.class);
              Set<AbstractionPredicate> preds = cintpState.getPredicates();

              if (!preds.isEmpty()) {
                // we only get the first conditional predicate for convenient.
                BooleanFormula predFormula = preds.iterator().next().getSymbolicAtom();
                SSAMap ssa = cachedPathInstanceFormula.get(cintpState.getPathInstance()).getSsa();
                // instantiate this predicate.
                predFormula = fmgr.instantiate(predFormula, ssa);

                pathFormulas.add(predFormula);
                leftEvalInfo.remove(phi1RealFirstEvalVar);
                predFormulasInfo.put(phi1RealFirstEvalVar, pathFormulas.size() - 1);
              }
            }
          }
        }
      }
      //      System.out.println();

      // update the current state.
      curState = argParent;
    }

    boolean isSuccess =
        !pathFormulas.isEmpty() && (leftEvalInfo.isEmpty() && rightEvalInfo.isEmpty());
    return Pair.of(isSuccess, new ArrayList<>(Lists.reverse(pathFormulas)));
  }

  /**
   * This function removes the redundant conditional interpolation formulas in the IC-Intp formula
   * chain.
   *
   * @param pPathFormula The in progress IC-Intp formula chain. (not finish building it)
   * @param pPhi1 The evaluation information of an edge.
   * @param pPredFormulasInfo The cached information of predicate formulas.
   *
   * @implNote If the left evaluation variable (the left-value) of pPhi1 appears in a conditional
   *           interpolation predicate (we assume that all the conditional predicates are assume
   *           form, e.g. "x < 3"), we will remove it from the IC-Intp formula chain, since a
   *           further derivation should be performed.
   */
  private void removeCIntpPredFormula(
      List<BooleanFormula> pPathFormula,
      Set<Pair<Set<String>, Set<String>>> pPhi1,
      Map<String, Integer> pPredFormulasInfo) {
    for (Pair<Set<String>, Set<String>> ap : pPhi1) {
      // get the left evaluation information.
      String leftValue = ap.getFirst().iterator().next();
      if (pPredFormulasInfo.containsKey(leftValue)) {
        // remove useless conditional interpolation predicate.
        pPathFormula.remove(pPredFormulasInfo.get(leftValue).intValue());
        // remove the variable information from the conditional interpolation formula chain.
        pPredFormulasInfo.remove(leftValue);
      }
    }
  }

  /**
   * This function computes the inverse derivation of pPhi1 and pPhi2.
   *
   * @param pPhi1 The precursor evaluation information.
   * @param pPhi2 The successor evaluation information (always be an assume evaluation information).
   * @param pIsPhi2Assume The flag that is used for indicating whether pPhi2 is an assume evaluation
   *        information.
   * @return The result contains two information, 1) whether the formula corresponding to pPhi1
   *         should be in IC-Intp formula chain, and 2) the derivation result.
   */
  @SuppressWarnings("unchecked")
  private Pair<Boolean, Pair<Set<String>, Set<String>>> inverseFormulaDerivation(Set<?> pPhi1, Pair<Set<String>, Set<String>> pPhi2, Boolean pIsPhi2Assume) {
    // convert the phi1 and get the left/right evaluation set of phi2.
    Set<Pair<Set<String>, Set<String>>> tmpPhi1 = (Set<Pair<Set<String>, Set<String>>>) pPhi1;
    Set<String> leftEvalInfo = pPhi2.getFirst(), rightEvalInfo = pPhi2.getSecond();
    boolean isPhi2Assume = pIsPhi2Assume;

    // mark that whether this edge should be in the inverse derivation formula chain.
    boolean isEdgeUsed = false;
    // mark that whether this evaluation information is the first element in pPhi1.
    boolean isEvaluatedInfo = false;
    // process every evaluation information of tmpPhi1.
    Iterator<Pair<Set<String>, Set<String>>> phi1Iter = tmpPhi1.iterator();
    while (phi1Iter.hasNext()) {
      Pair<Set<String>, Set<String>> evalInfoPair = phi1Iter.next();
      Set<String> leftVarSet = evalInfoPair.getFirst(), rightVarSet = evalInfoPair.getSecond();

      isPhi2Assume = isPhi2Assume && !isEvaluatedInfo;

      //// perform the inverse derivation.
      if (isPhi2Assume) {
        // it means that phi2 is the evaluation information of an assume edge (after a single
        // inverse derivation, the result evaluation pair will be be regarded as an assume
        // derivation pair)
        Set<String> leftCommSet = new HashSet<>(Sets.intersection(leftEvalInfo, leftVarSet));

        if (leftCommSet.size() > 0) {
          leftEvalInfo.removeAll(leftCommSet);
          leftEvalInfo.addAll(rightVarSet);
          // mark that the formula corresponding to this edge should be in the IC-Intp formula
          // chain.
          isEdgeUsed = isEdgeUsed ? isEdgeUsed : true;
          // mark that some sub-evaluation information have been evaluated, i.e., the successor
          // sub-evaluation information should not perform the assume derivation.
          isEvaluatedInfo = true;
        } else {
          // the leftEvalInfo stay unchanged, and this edge may not (since the process may not
          // finished) in the IC-Intp formula chain.
          isEdgeUsed = isEdgeUsed ? isEdgeUsed : false;
        }
      } else {
        // it means that phi2 is the evaluation information corresponding to an assignment edge (or
        // after the first sub-evaluation derivation of an edge, e.g., a function call with multiple
        // parameters)
        Set<String> rightLeftCommSet = new HashSet<>(Sets.intersection(rightEvalInfo, leftVarSet));

        if (rightLeftCommSet.size() > 0) {
          rightEvalInfo.removeAll(rightLeftCommSet);
          rightEvalInfo.addAll(rightVarSet);
          leftEvalInfo = rightEvalInfo;
          // mark that the formula corresponding to this edge should be in the IC-Intp formula
          // chain.
          isEdgeUsed = isEdgeUsed ? isEdgeUsed : true;
          // mark that this sub-evaluation information have been performed the derivation step.
          isEvaluatedInfo = true;
        } else {
          // the leftEvalInfo stay unchanged.
          leftEvalInfo = rightEvalInfo;
          isEdgeUsed = isEdgeUsed ? isEdgeUsed : false;
        }
      }

      // no matter performed which kind of inverse derivation, the result rightEvalInfo should be
      // always empty, i.e., the new derivation pair is an assume evaluation information.
      rightEvalInfo = new HashSet<>();
    }

    return Pair.of(isEdgeUsed, Pair.of(leftEvalInfo, rightEvalInfo));
  }

  public static Map<Integer, Pair<AbstractionPredicate, Set<AbstractionPredicate>>>
      getCintpCache() {
    return cintpCache;
  }

  public static Map<BooleanFormula, AbstractionPredicate> getFormulaPredicateMap() {
    return formulaPredicateMap;
  }

}
