// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.datarace;

import static com.google.common.collect.FluentIterable.from;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.ARGUtils;
import org.sosy_lab.cpachecker.cpa.arg.path.ARGPath;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateAbstractState;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateCPA;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.dependence.DGNode;
import org.sosy_lab.cpachecker.util.dependence.conditional.ConditionalDepGraph;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormula;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.pathformula.SSAMap;
import org.sosy_lab.cpachecker.util.predicates.pathformula.pointeraliasing.PointerTargetSet;
import org.sosy_lab.cpachecker.util.predicates.smt.FormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.smt.Solver;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.BooleanFormulaManager;
import org.sosy_lab.java_smt.api.ProverEnvironment;

public class DataRacePrecisionAdjustment implements PrecisionAdjustment {

  private final ConditionalDepGraph condDepGraph;
  private int raceTime = 0;

  private LogManager logger;

  private PredicateCPA cpa;
  private Solver solver;
  private FormulaManagerView fmgr;
  private PathFormulaManager pfmgr;
  private BooleanFormulaManager bfmgr;

  private final PathFormula emptyPathFormula;

  public DataRacePrecisionAdjustment(
      Configuration pConfig,
      LogManager pLogger,
      ShutdownNotifier pShutdownNotifier,
      PredicateCPA pCpa,
      CFA pCfa) {
    condDepGraph = GlobalInfo.getInstance().getEdgeInfo().getCondDepGraph();
    logger = pLogger;
    cpa = pCpa;

    solver = cpa.getSolver();
    fmgr = solver.getFormulaManager();
    pfmgr = cpa.getPathFormulaManager();
    bfmgr = fmgr.getBooleanFormulaManager();

    emptyPathFormula =
        new PathFormula(
            fmgr.getBooleanFormulaManager().makeTrue(),
            SSAMap.emptySSAMap(),
            PointerTargetSet.emptyPointerTargetSet(),
            0);
  }

  public Optional<PrecisionAdjustmentResult> prec_(
      AbstractState pState,
      Precision pPrecision,
      UnmodifiableReachedSet pStates,
      Function<AbstractState, AbstractState> pStateProjection,
      AbstractState pFullState)
      throws CPAException, InterruptedException {

    if(pFullState instanceof ARGState) {
      ARGState argCurState = (ARGState) pFullState,
          argParState = argCurState.getParents().iterator().next();
      
      ImmutableList<Pair<DataRaceState, CFAEdge>> drBrotherStateAndEdgePairs =
          from(argParState.getChildren()).transform(
              bs -> Pair.of(
                  AbstractStates.extractStateByType(bs, DataRaceState.class),
                  argParState.getEdgeToChild(bs)))
              .toList();
      
      if (drBrotherStateAndEdgePairs.size() > 1) {
        List<BooleanFormula> parPrefixPath = genPathFormula(argParState);
        PathFormula parPathFormula =
            AbstractStates.extractStateByType(argParState, PredicateAbstractState.class)
                .getPathFormula();

        for (int i = 0; i < drBrotherStateAndEdgePairs.size() - 1; ++i) {
          Pair<DataRaceState, CFAEdge> drBrotherPairI = drBrotherStateAndEdgePairs.get(i);
          DGNode drBrotherDepNodeI = condDepGraph.getDGNode(drBrotherPairI.getSecond().hashCode());
          boolean unsatCheckedI = false, unsatI = false;

          for (int j = 1; j < drBrotherStateAndEdgePairs.size(); ++j) {
            Pair<DataRaceState, CFAEdge> drBrotherPairJ = drBrotherStateAndEdgePairs.get(j);
            DGNode drBrotherDepNodeJ =
                condDepGraph.getDGNode(drBrotherPairJ.getSecond().hashCode());

            if (condDepGraph.dep(drBrotherDepNodeI, drBrotherDepNodeJ) != null) {
              BooleanFormula fEdgeJ =
                  generateNewPathFormula(parPathFormula, drBrotherPairJ.getSecond()).getFormula();
              try (ProverEnvironment proverJ = solver.newProverEnvironment()) {
                // add
                for (int pf = 0; pf < parPrefixPath.size(); ++pf) {
                  proverJ.push(parPrefixPath.get(pf));
                }
                proverJ.push(fEdgeJ);
                if (!proverJ.isUnsat()) {
                  try (ProverEnvironment proverI = solver.newProverEnvironment()) {
                    // add
                    for (int pf = 0; pf < parPrefixPath.size(); ++pf) {
                      proverI.push(parPrefixPath.get(pf));
                    }

                    BooleanFormula fEdgeI =
                        generateNewPathFormula(parPathFormula, drBrotherPairI.getSecond())
                            .getFormula();
                    proverI.push(fEdgeI);
                    if (proverI.isUnsat()) {
                      drBrotherPairI.getFirst().updateDataRace();
                      System.out.println("update I: " + argCurState.getStateId());
                    } else {
                      drBrotherPairI.getFirst().updateDataRace();
                      drBrotherPairJ.getFirst().updateDataRace();
                      System.out.println("real data-race point!" + argCurState.getStateId());
                    }
                  }
                } else {
                  drBrotherPairJ.getFirst().updateDataRace();
                  System.out.println("update J: " + argCurState.getStateId());
                }
              } catch (Exception e) {
                e.printStackTrace();
              }
            }
          }
        }
      }
    }

    return Optional.of(
        PrecisionAdjustmentResult
            .create(pState, pPrecision, PrecisionAdjustmentResult.Action.CONTINUE));
  }

  @Override
  public Optional<PrecisionAdjustmentResult> prec(
      AbstractState pState,
      Precision pPrecision,
      UnmodifiableReachedSet pStates,
      Function<AbstractState, AbstractState> pStateProjection,
      AbstractState pFullState)
      throws CPAException, InterruptedException {

    if (pFullState instanceof ARGState) {
      ARGState argCurState = (ARGState) pFullState,
          argParState = argCurState.getParents().iterator().next();

      ImmutableList<Pair<DataRaceState, CFAEdge>> drBrotherStateAndEdgePairs =
          from(argParState.getChildren()).transform(
              bs -> Pair.of(
                  AbstractStates.extractStateByType(bs, DataRaceState.class),
                  argParState.getEdgeToChild(bs)))
              .toList();

      if (drBrotherStateAndEdgePairs.size() > 1) {
        for (int i = 0; i < drBrotherStateAndEdgePairs.size() - 1; ++i) {
          Pair<DataRaceState, CFAEdge> drBrotherPairI = drBrotherStateAndEdgePairs.get(i);
          DGNode drBrotherDepNodeI = condDepGraph.getDGNode(drBrotherPairI.getSecond().hashCode());

          for (int j = 1; j < drBrotherStateAndEdgePairs.size(); ++j) {
            Pair<DataRaceState, CFAEdge> drBrotherPairJ = drBrotherStateAndEdgePairs.get(j);
            DGNode drBrotherDepNodeJ =
                condDepGraph.getDGNode(drBrotherPairJ.getSecond().hashCode());

            if (condDepGraph.dep(drBrotherDepNodeI, drBrotherDepNodeJ) != null) {
              drBrotherPairI.getFirst().updateDataRace();
              drBrotherPairJ.getFirst().updateDataRace();
            }
          }
        }
      }
    }

    return Optional.of(
        PrecisionAdjustmentResult
            .create(pState, pPrecision, PrecisionAdjustmentResult.Action.CONTINUE));
  }

  private List<BooleanFormula> genPathFormula(ARGState pState) {
    ARGPath path = ARGUtils.getOnePathTo(pState);
    final List<ARGState> pathStates = from(path.asStatesList()).skip(1).toList();
    
    return from(pathStates)
        .transform(s -> AbstractStates.extractStateByType(s, PredicateAbstractState.class))
        .transform(PredicateAbstractState::getBlockFormula)
        .filter(f -> !bfmgr.isTrue(f) && !bfmgr.isFalse(f))
        .toList();
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

}
