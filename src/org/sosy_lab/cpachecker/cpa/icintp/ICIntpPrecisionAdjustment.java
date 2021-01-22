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
 *
 *
 *  CPAchecker web page:
 *    http://cpachecker.sosy-lab.org
 */
package org.sosy_lab.cpachecker.cpa.icintp;

import static com.google.common.collect.FluentIterable.from;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult;
import org.sosy_lab.cpachecker.core.interfaces.WrapperCPA;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.ARGUtils;
import org.sosy_lab.cpachecker.cpa.arg.path.ARGPath;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateAbstractState;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateCPA;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;
import org.sosy_lab.cpachecker.util.predicates.AbstractionManager;
import org.sosy_lab.cpachecker.util.predicates.AbstractionPredicate;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormula;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.pathformula.SSAMap;
import org.sosy_lab.cpachecker.util.predicates.pathformula.pointeraliasing.PointerTargetSet;
import org.sosy_lab.cpachecker.util.predicates.regions.RegionCreator;
import org.sosy_lab.cpachecker.util.predicates.smt.FormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.smt.Solver;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.BooleanFormulaManager;
import org.sosy_lab.java_smt.api.InterpolatingProverEnvironment;
import org.sosy_lab.java_smt.api.SolverException;

@Options(prefix = "cpa.icintp")
public class ICIntpPrecisionAdjustment implements PrecisionAdjustment {

  @Option(
      secure = true,
      description =
          "With this option enabled, the optimizaiton strategy, namely "
              + "Incremental C-Intp (IC-Intp), is applied in the process of generating conditional interpolations.")
  private boolean useIncCIntp = false;

  // IC-Intp solving environment.
  private Solver solver;
  private FormulaManagerView fmgr;
  private PathFormulaManager pfmgr;
  private AbstractionManager amgr;
  private BooleanFormulaManager bfmgr;
  private RegionCreator rmgr;

  // basic predicates and formulas.
  private BooleanFormula trueFormula, falseFormula;
  private AbstractionPredicate truePred, falsePred;
  private PathFormula emptyPathFormula;

  // for IC-Intp.
  private AbstractState evalRootState;

  // others.
  private final LogManager logger;
  private final ShutdownNotifier shutdownNotifier;
  private final ICIntpStatistics statistics;

  // ================== IC-Intp main contents ==================
  /* for every path instance, we preserve the c-intp predicates to avoid the repeat computation. */
  // [<path_instance, {pred_1, ...}>, ...]
  private static Map<Integer, Set<AbstractionPredicate>> icintpCache = new HashMap<>();
  /* for each formula, we need to preserve is corresponding c-intp predicate. */
  // [<formula, predicate>, ...]
  private static Map<BooleanFormula, AbstractionPredicate> formulaPredicateMap = new HashMap<>();
  /* we preserve the branches that need not to perform the IC-Intp. */
  // [path_instance, ...]
  private static Set<Integer> noComputeCache = new HashSet<>();
  /* for each edge, we need to preserve the evaluation information to avoid the dynamic computation of this information. */
  // [<edge_hash, {eval_1, ...}>, ...]
  private Map<Integer, Set<?>> edgeEvalInfo;
  /* for each edge in a path, we preserve it's edge formula (in SSA form). */
  // [<path_instance, path_instance_formula>, ...]
  private Map<Integer, PathFormula> cachedPathInstanceFormula;
  //
  private static Set<AbstractionPredicate> curICIntpPreds;

  public ICIntpPrecisionAdjustment(
      Configuration pConfig,
      LogManager pLogger,
      ShutdownNotifier pShutdownNotifier,
      ICIntpStatistics pStatistics,
      CFA pCfa)
      throws InvalidConfigurationException {
    pConfig.inject(this);

    logger = pLogger;
    shutdownNotifier = pShutdownNotifier;
    statistics = pStatistics;

    solver = null;
    evalRootState = null;

    edgeEvalInfo =
        useIncCIntp ? EdgeEvaluationExtractor.extractCFAEvalInfo(pLogger, pCfa) : new HashMap<>();
    cachedPathInstanceFormula = new HashMap<>();
  }

  private void setupSolver() {
    Preconditions.checkArgument((solver == null), "the solver environment have been setup.");

    try {
      Optional<ConfigurableProgramAnalysis> cpas = GlobalInfo.getInstance().getCPA();
      if (cpas.isPresent()) {
        PredicateCPA pcpa = retrivePredicateCPA(cpas.get(), PredicateCPA.class);

        // get solving environment.
        solver = pcpa.getSolver();
        fmgr = solver.getFormulaManager();
        pfmgr = pcpa.getPathFormulaManager();
        amgr = pcpa.getAbstractionManager();
        bfmgr = fmgr.getBooleanFormulaManager();
        rmgr = amgr.getRegionCreator();

        // create basic predicates and formulas.
        trueFormula = bfmgr.makeTrue();
        falseFormula = bfmgr.makeFalse();
        truePred = amgr.makePredicate(trueFormula);
        falsePred = amgr.makeFalsePredicate();
        emptyPathFormula =
            new PathFormula(
                trueFormula, SSAMap.emptySSAMap(), PointerTargetSet.emptyPointerTargetSet(), 0);

        cachedPathInstanceFormula.put(-1, emptyPathFormula);
      }
    } catch (InvalidConfigurationException e) {
      e.printStackTrace();
      logger.log(Level.SEVERE, "something error happend when setting up the solver");
    }
  }

  @SuppressWarnings("unchecked")
  private <T extends ConfigurableProgramAnalysis> T retrivePredicateCPA(
      final ConfigurableProgramAnalysis pCPA, Class<T> pClass)
      throws InvalidConfigurationException {
    if (pCPA.getClass().equals(pClass)) {
      return (T) pCPA;
    } else if (pCPA instanceof WrapperCPA) {
      WrapperCPA wCPAs = (WrapperCPA) pCPA;
      T result = wCPAs.retrieveWrappedCpa(pClass);

      if (result != null) {
        return result;
      }
    }
    throw new InvalidConfigurationException("could not find the CPA " + pClass + " from " + pCPA);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Override
  public Optional<PrecisionAdjustmentResult> prec(
      AbstractState pState,
      Precision pPrecision,
      UnmodifiableReachedSet pStates,
      Function<AbstractState, AbstractState> pStateProjection,
      AbstractState pFullState)
      throws CPAException, InterruptedException {
    if (solver == null) {
      setupSolver();
    }
    statistics.cintpOverallTimer.start();

    ICIntpState icintpState = (ICIntpState) pState;
    curICIntpPreds = new HashSet<>();
    Integer curPathInst = icintpState.getPathInstance();

    assert pFullState instanceof ARGState;
    ARGState argCurrent = (ARGState) pFullState;

    // update the path instance formula of current ARG state.
    updateCurStatePathFormula(argCurrent, curPathInst);

    //// compute the conditional interpolants for each successor.
    // obtain all the locations of each thread.
    //    LocationState curLocState =
    //        AbstractStates.extractStateByType(pFullState, LocationState.class);
    //    Iterator<CFANode> curLocsIter = curLocState.getLocationNodes().iterator();
    Iterable<CFANode> curLocsNodes = AbstractStates.extractLocations(pFullState);
    Iterator<CFANode> curLocsIter = curLocsNodes.iterator();

    // if all the thread have no assume edge successor, then we skip this state.
    if (!needComputeCIntp(curLocsNodes, curPathInst)) {
      statistics.cintpOverallTimer.stop();
      noComputeCache.add(curPathInst);
      icintpCache.put(curPathInst, new HashSet<>());
      return Optional.of(
          PrecisionAdjustmentResult.create(
              icintpState, pPrecision, PrecisionAdjustmentResult.Action.CONTINUE));
    }

    // if some predicates have already computed (before the previous refinement), we just use the
    // cached predicates.
    if (icintpCache.containsKey(curPathInst)) {
      statistics.numUsedCache.inc();
      statistics.cintpOverallTimer.stop();
      return Optional.of(
          PrecisionAdjustmentResult.create(
              icintpState, pPrecision, PrecisionAdjustmentResult.Action.CONTINUE));
    }
    statistics.cintpTimer.start();

    // perform the conditional interpolation.
    try (InterpolatingProverEnvironment<?> prover =
        solver.newProverEnvironmentWithInterpolation()) {
      // build the interpolation path formula.
      Pair<Boolean, List<BooleanFormula>> pathFormulasInfo = null;

      // iterate all the threads.
      while (curLocsIter.hasNext()) {
        // we start to analysis a thread location.
        CFANode curThreadLoc = curLocsIter.next();

        // if the current location does not contain any conditional successors, then we need not to
        // perform the conditional interpolation.
        if (!needComputeCIntp(curThreadLoc)) {
          continue;
        }

        // re-compute the interpolation path formulas.
        statistics.pathGenerationTimer.start();
        pathFormulasInfo = reConstructIntpPathFormulas(argCurrent, curThreadLoc, pathFormulasInfo);
        statistics.pathGenerationTimer.stop();

        // if no error occurred when constructing the interpolation path formula, then we perform
        // the conditional interpolation.
        if (pathFormulasInfo.getFirst()) {
          // push these formulas into the prover.
          List<BooleanFormula> pathFormulas = pathFormulasInfo.getSecond();
          List handles = new ArrayList<>();
          for (BooleanFormula f : pathFormulas) {
            handles.add(prover.push(f));
          }
          //          pathFormulas.forEach(f -> handles.add(prover.push(f)));

          // iterate all the assume edge successors.
          for (int i = 0; i < curThreadLoc.getNumLeavingEdges(); ++i) {
            // compute the edge formula of this assume edge.
            CFAEdge childAsmEdge = curThreadLoc.getLeavingEdge(i);
            assert childAsmEdge instanceof CAssumeEdge;

            BooleanFormula asmEdgeFormula = computeAssumeEdgeFormula(curPathInst, childAsmEdge);
            handles.add(prover.push(asmEdgeFormula));

            // if unsatisfiable, we perform the conditional interpolation.
            boolean unsat = prover.isUnsat();
            if (unsat) {
              statistics.numTotalIntpPathLength.setNextValue(pathFormulas.size());
              statistics.numUnsatIntpTimes.inc();
              List<BooleanFormula> itps = genPathFormulaIntps(prover, handles);
              Pair<AbstractionPredicate, Set<AbstractionPredicate>> preds = genPathIntpPreds(itps);
              Set<AbstractionPredicate> newPreds = preds.getSecond();

              // get the conditional interpolant set generated by other branches.
              Set<AbstractionPredicate> genPreds = icintpCache.get(curPathInst);
              if (genPreds == null) {
                icintpCache.put(curPathInst, newPreds);
              } else {
                icintpCache.put(curPathInst, Sets.union(genPreds, newPreds));
              }
              statistics.numGeneratedPredicates.setNextValue(newPreds.size());
              break;
            } else {
              if (i == 0) {
                // we need to try another assume edge (notice that, one location can only have
                // exactly two assume edge successors).
                handles.remove(handles.size() - 1);
                prover.pop();
              }
            }
          } // iterate assume edge successors.

          // clear the prover stack.
          clearProverStack(prover, handles);
        } // good interpolation path.
      } // thread iterate.
    } catch (Exception e) {
      logger.log(Level.WARNING, "exception occured when computing the conditional interpolants.");
      e.printStackTrace();
    }
    statistics.cintpTimer.stop();
    statistics.cintpOverallTimer.stop();

    // no conditional interpolant is generated.
    if (!icintpCache.containsKey(curPathInst)) {
      noComputeCache.add(curPathInst);
      icintpCache.put(curPathInst, new HashSet<>());
    } else {
      curICIntpPreds = icintpCache.get(curPathInst);
    }

    return Optional.of(
        PrecisionAdjustmentResult.create(
            icintpState, pPrecision, PrecisionAdjustmentResult.Action.CONTINUE));
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private void clearProverStack(InterpolatingProverEnvironment<?> pProver, List pHandles) {
    if (useIncCIntp) {
      // remove all the formulas in the prove stack.
      pHandles.forEach(e -> pProver.pop());
    } else {
      pProver.pop();
      pHandles.remove(pHandles.size() - 1);
    }
  }

  private Pair<Boolean, List<BooleanFormula>> reConstructIntpPathFormulas(
      final ARGState pCurState,
      final CFANode pThreadLoc,
      final Pair<Boolean, List<BooleanFormula>> pFormulaInfo) {
    if (useIncCIntp) {
      Pair<Boolean, List<BooleanFormula>> pathFormulasInfo =
          generateICIntpFormulaChain(pCurState, pThreadLoc.getLeavingEdge(0));

      if (pathFormulasInfo.getFirst()) {
        statistics.numSucICIntpPathGen.inc();
        return pathFormulasInfo;
      } else {
        // if the construction of the interpolation path formula chain is failed, we should not
        // perform the conditional interpolation by re-building a full path (i.e., call the function
        // 'generatePathFormulas(...)'), since the variables in the assume edge may be
        // un-initialized or non-deterministic, and the SMT solver will always return UnSAT (i.e.,
        // it's useless and even time consuming).
        logger.log(
            Level.FINE,
            "cannot construct the interpolation path formula at state s"
                + pCurState.getStateId()
                + ": "
                + pThreadLoc);
        statistics.numFailICIntpPathGen.inc();
        return pathFormulasInfo;
      }
    } else {
      if (pFormulaInfo == null) {
        return Pair.of(true, genPathFormulas(pCurState));
      } else {
        return pFormulaInfo;
      }
    }
  }

  private Pair<AbstractionPredicate, Set<AbstractionPredicate>> genPathIntpPreds(
      final List<BooleanFormula> pFormulas) {
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

  @SuppressWarnings({"unchecked", "rawtypes"})
  private List<BooleanFormula> genPathFormulaIntps(
      InterpolatingProverEnvironment<?> pProver, List handles)
      throws SolverException, InterruptedException {
    assert handles.size() > 1;

    List<BooleanFormula> results = new ArrayList<>();

    // perform the conditional interpolation.
    for (int i = 0; i < handles.size(); ++i) {
      BooleanFormula itp = pProver.getInterpolant(handles.subList(0, i + 1));
      results.add(itp);
    }

    return results;
  }

  private BooleanFormula computeAssumeEdgeFormula(
      final Integer pCurPathInstance, final CFAEdge pEdge) {
    Preconditions.checkArgument(
        ((pEdge instanceof CAssumeEdge) && cachedPathInstanceFormula.containsKey(pCurPathInstance)),
        "could not get the SSA map according to the given path instance!");

    try {
      SSAMap curPathFormulaSSA = cachedPathInstanceFormula.get(pCurPathInstance).getSsa();
      PathFormula tmpPathFormula = pfmgr.makeNewPathFormula(emptyPathFormula, curPathFormulaSSA);

      PathFormula resultPathFormula = pfmgr.makeAnd(tmpPathFormula, pEdge);
      return resultPathFormula.getFormula();
    } catch (Exception e) {
      logger.log(
          Level.SEVERE,
          "something error occured when generating the path formula of the edge: " + pEdge);
      e.printStackTrace();
    }

    return null;
  }

  private List<BooleanFormula> genPathFormulas(final ARGState pCurState) {
    // construct the path starting from the root to current state.
    ARGPath path = ARGUtils.getOnePathTo(pCurState);
    // extract all the states in this path (remove the root state).
    final List<ARGState> pathStates = from(path.asStatesList()).skip(1).toList();

    List<BooleanFormula> edgeFormulas = new ArrayList<>();
    for (int i = 1; i < pathStates.size(); ++i) {
      Integer childPathInst =
          AbstractStates.extractStateByType(pathStates.get(i), ICIntpState.class).getPathInstance();

      // get the edge formula and filter true formula.
      BooleanFormula formula = cachedPathInstanceFormula.get(childPathInst).getFormula();

      if (!bfmgr.isTrue(formula)) {
        edgeFormulas.add(formula);
      }
    }

    return edgeFormulas;
  }

  private boolean needComputeCIntp(final Iterable<CFANode> pCurStateLocs, Integer pCurPathInst) {
    if (noComputeCache.contains(pCurPathInst)) {
      return false;
    }

    Iterator<CFANode> curStateLocsIter = pCurStateLocs.iterator();
    while (curStateLocsIter.hasNext()) {
      CFANode threadLoc = curStateLocsIter.next();
      if (threadLoc.getNumLeavingEdges() > 0
          && threadLoc.getLeavingEdge(0) instanceof CAssumeEdge) {
        return true;
      }
    }
    return false;
  }

  private boolean needComputeCIntp(final CFANode pLoc) {
    return ((pLoc.getNumLeavingEdges() > 0) && (pLoc.getLeavingEdge(0) instanceof CAssumeEdge));
  }

  private void updateCurStatePathFormula(final ARGState pCurState, final Integer pCurPathInst) {
    if (!cachedPathInstanceFormula.containsKey(pCurPathInst)) {
      PathFormula pathFormula =
          AbstractStates.extractStateByType(pCurState, PredicateAbstractState.class)
              .getPathFormula();
      cachedPathInstanceFormula.put(pCurPathInst, pathFormula);
    }
  }

  /**
   * This function computes the shortest conditional interpolation path.
   *
   * @param pState The precursor state of pAssumeEdge.
   * @param pAssumeEdge The conditional edge that need to perform the conditional interpolation.
   * @return It returns two elements, the first one indicates whether this function have
   *     successfully generates the shortest conditional interpolation path; and the second element
   *     is the shortest conditional interpolation path.
   */
  @SuppressWarnings("unchecked")
  private Pair<Boolean, List<BooleanFormula>> generateICIntpFormulaChain(
      ARGState pState, CFAEdge pAssumeEdge) {
    assert pAssumeEdge instanceof AssumeEdge
        && pAssumeEdge.getPredecessor().equals(AbstractStates.extractLocation(pState));

    List<BooleanFormula> pathFormulas = new ArrayList<>();

    // create the initial evaluation information.
    Set<String> leftEvalInfo =
        new HashSet<>((Set<String>) edgeEvalInfo.get(pAssumeEdge.hashCode()));
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
      Set<?> phi1 = edgeEvalInfo.get(curEdge.hashCode());
      //      System.out.print(curEdge);
      // ignore the assume edges, since they are helpless to the interpolation chain.
      if (!(curEdge instanceof AssumeEdge)) {
        // skip some empty evaluation edges.
        if (phi1.size() > 0) {
          // remove the conditional predicates added before.
          removeCIntpPredFormula(
              pathFormulas, (Set<Pair<Set<String>, Set<String>>>) phi1, predFormulasInfo);

          // perform the inverse derivation step.
          Pair<Boolean, Pair<Set<String>, Set<String>>> invDeriResult =
              inverseFormulaDerivation(phi1, phi2, isPhi2Assume);
          // get the derivation result, and the this result should always be an assume evaluation
          // information.
          phi2 = invDeriResult.getSecond();
          isPhi2Assume = true;

          if (invDeriResult.getFirst()) {
            //            System.out.println("\t <--");
            Integer pathInstance =
                AbstractStates.extractStateByType(curState, ICIntpState.class).getPathInstance();
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
              AbstractStates.extractStateByType(curState, ICIntpState.class).getPathInstance();
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
              ICIntpState icintpState =
                  AbstractStates.extractStateByType(argParent, ICIntpState.class);
              Set<AbstractionPredicate> preds = icintpCache.get(icintpState.getPathInstance());

              if (!preds.isEmpty()) {
                // we only get the first conditional predicate for convenient.
                BooleanFormula predFormula = preds.iterator().next().getSymbolicAtom();
                SSAMap ssa = cachedPathInstanceFormula.get(icintpState.getPathInstance()).getSsa();
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
   * @implNote If the left evaluation variable (the left-value) of pPhi1 appears in a conditional
   *     interpolation predicate (we assume that all the conditional predicates are assume form,
   *     e.g. "x < 3"), we will remove it from the IC-Intp formula chain, since a further derivation
   *     should be performed.
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
   *     information.
   * @return The result contains two information, 1) whether the formula corresponding to pPhi1
   *     should be in IC-Intp formula chain, and 2) the derivation result.
   */
  @SuppressWarnings("unchecked")
  private Pair<Boolean, Pair<Set<String>, Set<String>>> inverseFormulaDerivation(
      Set<?> pPhi1, Pair<Set<String>, Set<String>> pPhi2, Boolean pIsPhi2Assume) {
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

  public static Map<BooleanFormula, AbstractionPredicate> getFormulaPredicateMap() {
    return formulaPredicateMap;
  }

  public static Set<AbstractionPredicate> getCurICIntpPredicates() {
    return curICIntpPreds;
  }
}
