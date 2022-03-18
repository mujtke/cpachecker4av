// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.por.cpdpor;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.FluentIterable.from;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.sosy_lab.cpachecker.cfa.ast.AExpression;
import org.sosy_lab.cpachecker.cfa.ast.AFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.AIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.AStatement;
import org.sosy_lab.cpachecker.cfa.model.AStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.bdd.BDDState;
import org.sosy_lab.cpachecker.cpa.por.EdgeType;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.dependence.DGNode;
import org.sosy_lab.cpachecker.util.dependence.conditional.CondDepConstraints;
import org.sosy_lab.cpachecker.util.dependence.conditional.ConditionalDepGraph;

public class CPDPORPrecisionAdjustment implements PrecisionAdjustment {

  private final ConditionalDepGraph condDepGraph;
  private final Map<Integer, Integer> nExploredChildCache;
  private final ICComputer icComputer;
  private final CPDPORStatistics statistics;

  private static final Function<ARGState, Set<ARGState>> gvaEdgeFilter =
      (s) -> from(s.getChildren()).filter(
          cs -> AbstractStates.extractStateByType(cs, CPDPORState.class)
              .getTransferInEdgeType()
              .equals(EdgeType.GVAEdge))
          .toSet();
  private static final Function<ARGState, Set<ARGState>> nEdgeFilter =
      (s) -> from(s.getChildren()).filter(
          cs -> AbstractStates.extractStateByType(cs, CPDPORState.class)
              .getTransferInEdgeType()
              .equals(EdgeType.NEdge))
          .toSet();
  private static final Function<ARGState, Set<ARGState>> naEdgeFilter =
      (s) -> from(s.getChildren()).filter(
          cs -> AbstractStates.extractStateByType(cs, CPDPORState.class)
              .getTransferInEdgeType()
              .equals(EdgeType.NAEdge))
          .toSet();

  public CPDPORPrecisionAdjustment(
      ConditionalDepGraph pCondDepGraph,
      ICComputer pIcComputer,
      CPDPORStatistics pStatistics) {
    condDepGraph = checkNotNull(pCondDepGraph);
    nExploredChildCache = new HashMap<>();
    icComputer = pIcComputer;
    statistics = pStatistics;
  }

  @Override
  public Optional<PrecisionAdjustmentResult> prec(
      AbstractState pState,
      Precision pPrecision,
      UnmodifiableReachedSet pStates,
      Function<AbstractState, AbstractState> pStateProjection,
      AbstractState pFullState)
      throws CPAException, InterruptedException {
    // we need to know the parent state of the current state.
    if (pFullState instanceof ARGState) {
      ARGState argCurState = (ARGState) pFullState,
          argParState = argCurState.getParents().iterator().next();
      // we need to clean up the caches for some refinement based algorithms.
      if (argParState.getStateId() == 0) {
        nExploredChildCache.clear();
      }

      CPDPORState cpdporCurState =
          AbstractStates.extractStateByType(argCurState, CPDPORState.class),
          cpdporParState = AbstractStates.extractStateByType(argParState, CPDPORState.class);
      int argParStateId = argParState.getStateId();

      // get all the type of successors of the argParState.
      Set<ARGState> gvaSuccessors = gvaEdgeFilter.apply(argParState);
      Set<ARGState> naSuccessors = naEdgeFilter.apply(argParState);
      Set<ARGState> nSuccessors = nEdgeFilter.apply(argParState);

      // get the precursor & successor nodes of the transfer-in edge of cpdporCurState.
      CFAEdge cpdporCurStateInEdge = cpdporCurState.getCurrentTransferInEdge();
      int curStateInEdgePreNode = cpdporCurStateInEdge.getPredecessor().getNodeNumber();

      // explore this 'normal' successor.
      if (!nSuccessors.isEmpty()) {
        if (nSuccessors.contains(argCurState) && !nExploredChildCache.containsKey(argParStateId)) {
          // it's the first time we explore the normal successor of argParState.
          nExploredChildCache.put(argParStateId, curStateInEdgePreNode);
          // update sleep set.
          cpdporCurState.setSleepSet(cpdporParState.getSleepSet());
          return Optional.of(
              PrecisionAdjustmentResult
                  .create(pState, pPrecision, PrecisionAdjustmentResult.Action.CONTINUE));
        } else {
          // we need not to explore other normal successors.
          statistics.avoidExplorationTimes.inc();
          return Optional.empty();
        }
      }
      assert nSuccessors.isEmpty();

      // explore this 'assume normal' successor.
      if (!naSuccessors.isEmpty()) {
        if (naSuccessors.contains(argCurState)
            && (!nExploredChildCache.containsKey(argParStateId)
                || nExploredChildCache.get(argParStateId).equals(curStateInEdgePreNode))) {
          // explore another assume successor which have the same precursor with the explored one.
          nExploredChildCache.put(argParStateId, curStateInEdgePreNode);
          // update sleep set.
          cpdporCurState.setSleepSet(cpdporParState.getSleepSet());
          return Optional.of(
              PrecisionAdjustmentResult
                  .create(pState, pPrecision, PrecisionAdjustmentResult.Action.CONTINUE));
        } else {
          // a common assume branches have already explored, we need not to explore other assume
          // branches (i.e., we have explored the assume branches of a thread).
          statistics.avoidExplorationTimes.inc();
          return Optional.empty();
        }
      }
      assert naSuccessors.isEmpty();

      // explore this 'global access' successor.
      if (!gvaSuccessors.isEmpty()) {
        // firstly, we need to update all the sleep set of all gvaSuccessors.
        if (!cpdporParState.isUpdated()) {
          if (gvaSuccessors.size() > 1) {
            ImmutableList<CPDPORState> updateGVASuccessors =
                from(gvaSuccessors)
                    .transform(s -> AbstractStates.extractStateByType(s, CPDPORState.class))
                    .toList();
            // obtain parent computation state to determine the dependency of successor transitions.
            AbstractState parComputeState =
                AbstractStates.extractStateByType(argParState, BDDState.class);

            for (int i = 0; i < updateGVASuccessors.size() - 1; ++i) {
              CPDPORState cpdporAState = updateGVASuccessors.get(i);
              CFAEdge cpdporAStateEdge = cpdporAState.getCurrentTransferInEdge();
              int cpdporAStateThrdId = cpdporAState.getCurrentTransferInEdgeThreadId();

              for (int j = i + 1; j < updateGVASuccessors.size(); ++j) {
                CPDPORState cpdporBState = updateGVASuccessors.get(j);
                CFAEdge cpdporBStateEdge = cpdporBState.getCurrentTransferInEdge();

                // determine whether the transfer-info of A-state is independent with the
                // transfer-info of B-state.
                if (canSkip(cpdporAStateEdge, cpdporBStateEdge, (BDDState) parComputeState)) {
                  // the transfer-info of A-state can avoid.
                  cpdporBState
                      .addThreadInfoSleep(Pair.of(cpdporAStateThrdId, cpdporAStateEdge.hashCode()));
                }
              }
            }
          }
          cpdporParState.setAsUpdated();
        }

        // check whether current transfer-in edge is in the sleep set of parent state.
        int curTransInThreadId = cpdporCurState.getCurrentTransferInEdgeThreadId();
        Pair<Integer, Integer> curTransInfo =
            Pair.of(
                curTransInThreadId,
                cpdporCurStateInEdge.hashCode());
        if (cpdporParState.isInSleepSet(curTransInfo)) {
          statistics.realRedundantTimes.inc();
          statistics.avoidExplorationTimes.inc();
          return Optional.empty();
        } else {
          // we need to explore this edge.
          return Optional.of(
              PrecisionAdjustmentResult
                  .create(pState, pPrecision, PrecisionAdjustmentResult.Action.CONTINUE));
        }
      }

    } else {
      throw new AssertionError("CPDPOR need utilize the information of parent ARGState!");
    }

    return Optional.of(
        PrecisionAdjustmentResult
            .create(pState, pPrecision, PrecisionAdjustmentResult.Action.CONTINUE));
  }

  private boolean canSkip(CFAEdge pCheckEdge, CFAEdge pCurEdge, BDDState pComputeState) {
    statistics.checkSkipTimes.inc();
    DGNode depCheckNode = condDepGraph.getDGNode(pCheckEdge.hashCode()),
        depCurNode = condDepGraph.getDGNode(pCurEdge.hashCode());

    // we do cannot determine the dependency of thread creation edges.
    boolean containThreadCreationEdge =
        (isThreadCreationEdge(pCheckEdge) || isThreadCreationEdge(pCurEdge));
    // compute conditional independence of the two nodes.
    CondDepConstraints ics = (CondDepConstraints) condDepGraph.dep(depCheckNode, depCurNode);
    
    //
    if(ics == null) {
      // they are unconditional independent.
      if (!containThreadCreationEdge
          && !pCheckEdge.getSuccessor().isLoopStart()
          && !pCurEdge.getSuccessor().isLoopStart()) { // TODO: loop start point should be carefully
                                                      // processed.
        statistics.checkSkipUnIndepTimes.inc();
        return true;
      } else {
        statistics.checkSkipOtherCaseTimes.inc();
        return false;
      }
    } else {
      // unconditional independent, we cannot skip.
      if (ics.isUnCondDep()) {
        statistics.checkSkipUnDepTimes.inc();
        return false;
      } else {
        // they are conditional independent, we need to use the constraints to check whether they
        // are really independent.
        boolean isCondDep = icComputer == null ? true : icComputer.computeDep(ics, pComputeState);

        // they are conditional independent at the give state.
        if (!isCondDep) {
          if (!containThreadCreationEdge
              && !pCheckEdge.getSuccessor().isLoopStart()
              && !pCurEdge.getSuccessor().isLoopStart()) {// TODO: loop start point should be
                                                          // carefully processed.
            statistics.checkSkipCondIndepTimes.inc();
            return true;
          } else {
            statistics.checkSkipOtherCaseTimes.inc();
            return false;
          }
        } else {
          statistics.checkSkipCondDepTimes.inc();
          return false;
        }
      }
    }
  }

  public boolean isThreadCreationEdge(final CFAEdge pEdge) {
    switch (pEdge.getEdgeType()) {
      case StatementEdge: {
        AStatement statement = ((AStatementEdge) pEdge).getStatement();
        if (statement instanceof AFunctionCall) {
          AExpression functionNameExp =
              ((AFunctionCall) statement).getFunctionCallExpression().getFunctionNameExpression();
          if (functionNameExp instanceof AIdExpression) {
            return ((AIdExpression) functionNameExp).getName().contains("pthread_create");
          }
        }
        return false;
      }
      default:
        return false;
    }
  }

}
