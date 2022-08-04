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
package org.sosy_lab.cpachecker.cpa.por.bippor;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.FluentIterable.from;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
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
import org.sosy_lab.cpachecker.cpa.por.EdgeType;
import org.sosy_lab.cpachecker.cpa.por.ppor.PeepholeState;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.dependence.DGNode;
import org.sosy_lab.cpachecker.util.dependence.conditional.ConditionalDepGraph;
import org.sosy_lab.cpachecker.util.dependence.conditional.EdgeVtx;

public class BIPPORPrecisionAdjustment2 implements PrecisionAdjustment {

  private final ConditionalDepGraph condDepGraph;
  private final boolean useOptDisableStates;
  private final Map<Integer, Integer> nExploredChildCache;

  // For Optimization: KEPHRemove
  private final boolean useOptKEPHRemove;
  // explored key event paths. (shared with BIPPORTransferRelation)
  private Set<Integer> expdKEPCache;

  private static final Function<ARGState, Set<ARGState>> gvaEdgeFilter =
      (s) ->
          from(s.getChildren())
              .filter(
                  cs ->
                      AbstractStates.extractStateByType(cs, BIPPORState.class)
                          .getTransferInEdgeType()
                          .equals(EdgeType.GVAEdge))
              .toSet();
  private static final Function<ARGState, Set<ARGState>> nEdgeFilter =
      (s) ->
          from(s.getChildren())
              .filter(
                  cs ->
                      AbstractStates.extractStateByType(cs, BIPPORState.class)
                          .getTransferInEdgeType()
                          .equals(EdgeType.NEdge))
              .toSet();
  private static final Function<ARGState, Set<ARGState>> naEdgeFilter =
      (s) ->
          from(s.getChildren())
              .filter(
                  cs ->
                      AbstractStates.extractStateByType(cs, BIPPORState.class)
                          .getTransferInEdgeType()
                          .equals(EdgeType.NAEdge))
              .toSet();

  public BIPPORPrecisionAdjustment2(
      ConditionalDepGraph pCondDepGraph,
      boolean pUseOptDisableStates,
      boolean pUseOptKephRemove,
      Set<Integer> pExpdKEPCache) {
    condDepGraph = checkNotNull(pCondDepGraph);
    useOptDisableStates = pUseOptDisableStates;

    useOptKEPHRemove = pUseOptKephRemove;
    expdKEPCache = checkNotNull(pExpdKEPCache);

    nExploredChildCache = new HashMap<>();
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
        cleanUpCaches();
      }

      BIPPORState
          bipporCurState = AbstractStates.extractStateByType(argCurState, BIPPORState.class),
          bipporParState = AbstractStates.extractStateByType(argParState, BIPPORState.class);
      int argParStateId = argParState.getStateId();

      // use the disable set optimization strategy.
      if (useOptKEPHRemove) {
        if (bipporCurState.isKEPHRemovable()) {
          return Optional.empty();
        }
      }
      //
      if (useOptDisableStates) {
        if (bipporParState.isDisabled(bipporCurState.getCurrentTransferInEdgeThreadId())) {
          return Optional.empty();
        }
      }

      // get all the type of successors of the argParState.
      Set<ARGState> gvaSuccessors = gvaEdgeFilter.apply(argParState);
      Set<ARGState> naSuccessors = naEdgeFilter.apply(argParState);
      Set<ARGState> nSuccessors = nEdgeFilter.apply(argParState);

      // get the precursor node of the transfer-in edge of bipporCurState.
      int curStateInEdgePreNode =
          bipporCurState.getCurrentTransferInEdge().getPredecessor().getNodeNumber();

      // explore this 'normal' successor.
      if (!nSuccessors.isEmpty()) {
        if (nSuccessors.contains(argCurState) && !nExploredChildCache.containsKey(argParStateId)) {
          // it's the first time we explore the normal successor of argParState.
          nExploredChildCache.put(argParStateId, curStateInEdgePreNode);
          // update the KEPH state.
          if (useOptKEPHRemove)
            expdKEPCache.add(bipporCurState.getKephState().getKeyEventPathHash());
          return Optional.of(
              PrecisionAdjustmentResult.create(
                  pState, pPrecision, PrecisionAdjustmentResult.Action.CONTINUE));
        } else {
          // we need not to explore other normal successors.
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
          // update the KEPH state.
          if (useOptKEPHRemove)
            expdKEPCache.add(bipporCurState.getKephState().getKeyEventPathHash());
          return Optional.of(
              PrecisionAdjustmentResult.create(
                  pState, pPrecision, PrecisionAdjustmentResult.Action.CONTINUE));
        } else {
          // a common assume branches have already explored, we need not to explore other assume
          // branches (i.e., we have explored the assume branches of a thread).
          return Optional.empty();
        }
      }
      assert naSuccessors.isEmpty();

      //
      ImmutableSet<ARGState> gvaNotRemovedStates = from(gvaSuccessors).filter(
          s -> !AbstractStates.extractStateByType(s, BIPPORState.class)
              .getKephState()
              .isNeedRemove())
          .toSet();

      // explore this 'global access' successor.
      if (!gvaSuccessors.isEmpty()) {
        PeepholeState preGVAPState = bipporCurState.getPreGVAState(),
            curPState = bipporCurState.getCurState();

        if (preGVAPState != null) {
          int oldThreadIdNumber = preGVAPState.getProcessEdgeThreadId(),
              newThreadIdNumber = curPState.getProcessEdgeThreadId();
          CFAEdge oldTransferEdge = preGVAPState.getProcEdge(),
              newTransferEdge = curPState.getProcEdge();

          // the two blocks can swap the order only if they the same thread-ids.
          // the reason is obvious: if a thread have already exited, how can we swap the two blocks?
          boolean canSwapable =
              preGVAPState
                  .getThreadIdNumbers()
                  .keySet()
                  .equals(curPState.getThreadIdNumbers().keySet());

          // check whether we could skip the exploration of current state.
          if (canSkip(
              canSwapable,
              oldThreadIdNumber,
              oldTransferEdge,
              newThreadIdNumber,
              newTransferEdge)) {
            return Optional.empty();
          } else {
            if (useOptDisableStates) {
              // NOTICE: the thread creation edge is considered as a special write event.

              // firstly, we need to setup the disable-exploration states flag of the states
              // that are in the same level of current state.
              // NOTICE: this function will modify all the flags of BIPPORState.
              if (!bipporCurState.isSetupDisFlag()) {
                if (!gvaNotRemovedStates.isEmpty()) {
                  setupGVASuccessorsDisableFlag(gvaNotRemovedStates);
                }
              }

              // then, by using the optimization strategy, we can determine whether the current
              // state is needed to be explored.
              int curTransferThreadId = bipporCurState.getCurrentTransferInEdgeThreadId();
              if (bipporParState.isDisabled(curTransferThreadId)
                  || bipporCurState.isDisabled(curTransferThreadId)) {
                // this global-access state is redundant.
                return Optional.empty();
              } else {
                // we must explore this successor state.

                // we need to update the pre-global-access state of bipporCurState.
                bipporCurState.setPreGVAState(curPState);
                // update the KEPH state.
                if (useOptKEPHRemove)
                  expdKEPCache.add(bipporCurState.getKephState().getKeyEventPathHash());
                return Optional.of(
                    PrecisionAdjustmentResult
                        .create(pState, pPrecision, PrecisionAdjustmentResult.Action.CONTINUE));
              }
            } else {
              // we must explore this successor state.

              // we need to update the pre-global-access state of bipporCurState.
              bipporCurState.setPreGVAState(curPState);
              // update the KEPH state.
              if (useOptKEPHRemove)
                expdKEPCache.add(bipporCurState.getKephState().getKeyEventPathHash());
              return Optional.of(
                  PrecisionAdjustmentResult
                      .create(pState, pPrecision, PrecisionAdjustmentResult.Action.CONTINUE));
            }
          }

        } else {
          // we need to explore the current global access state that have no pre-global-access
          // state.

          // we need to update the pre-global-access state of bipporCurState.
          bipporCurState.setPreGVAState(curPState);

          // update the KEPH state.
          if (useOptKEPHRemove)
            expdKEPCache.add(bipporCurState.getKephState().getKeyEventPathHash());
          return Optional.of(
              PrecisionAdjustmentResult.create(
                  pState, pPrecision, PrecisionAdjustmentResult.Action.CONTINUE));
        }
      }

    } else {
      throw new AssertionError("BIPPOR need utilize the information of parent ARGState!");
    }

    return Optional.of(
        PrecisionAdjustmentResult.create(
            pState, pPrecision, PrecisionAdjustmentResult.Action.CONTINUE));
  }

  private void cleanUpCaches() {
    nExploredChildCache.clear();
  }

  /**
   * This function updates the disable-exploration flag of each state in the set.
   *
   * @implNote If this function is called by some function, it means that it's the first time to
   *           setup the disable-exploration flag of states that have the same parent state.
   *
   * @param pGVASuccessors The successors that have the same parent state.
   */
  private void setupGVASuccessorsDisableFlag(Set<ARGState> pGVASuccessors) {
    Preconditions.checkArgument((pGVASuccessors != null && !pGVASuccessors.isEmpty()), "the given parameter is invalid!");

    // firstly, we need to build the map of thread_id <--> DepNode.
    Map<Integer, Pair<EdgeVtx, BIPPORState>> stateRWVarsMap = new HashMap<>();
    for (ARGState state : pGVASuccessors) {
      BIPPORState bipporState = AbstractStates.extractStateByType(state, BIPPORState.class);
      EdgeVtx gvaNode = (EdgeVtx) condDepGraph.getDGNode(bipporState.getCurrentTransferInEdge().hashCode());

      if (gvaNode != null) {
        // this node is not belongs to a thread creation edge.
        stateRWVarsMap
            .put(bipporState.getCurrentTransferInEdgeThreadId(), Pair.of(gvaNode, bipporState));
      } else {
        bipporState.setupDisFlag();
      }
    }

    // secondly, we apply the optimization strategy which sort the state in the order of
    // the number of thread.
    // e.g., state -> (thread_id, r_w_event): s1 -> (t1, w1); s2 -> (t2, r1); s3 -> (t2, r1'); s4 ->
    // (t3, r2)
    // sort result: s1 -> (t1, w1); s4 -> (t3, r2); s2 -> (t2, r1); s3 -> (t2, r1')
    // note: thread t2 has two branches, while all the other threads have only one branch.

    // finally, we can setup the disable-exploration set of each successor.

    // check whether the events in stateRWVarsMap are mutual independent.
    // boolean isMutualIndependent = true;
    // ImmutableList<EdgeVtx> eventList =
    // from(stateRWVarsMap.values()).transform(s -> s.getFirst()).toList();
    // for (int i = 0; i < eventList.size() - 1; ++i) {
    // if (!isMutualIndependent)
    // break;
    //
    // EdgeVtx n1 = eventList.get(i);
    //
    // for (int j = 1; j < eventList.size(); ++j) {
    // if (condDepGraph.dep(n1, eventList.get(j)) != null) {
    // isMutualIndependent = false;
    // break;
    // }
    // }
    // }

    // check whether all the successors are pure read events. if true, we obtain all the successor
    // id.
    boolean allSucPureRead = true;
    for (Pair<EdgeVtx, BIPPORState> vp : stateRWVarsMap.values()) {
      if (!vp.getFirst().isPureReadVtx()) {
        allSucPureRead = false;
      }
    }
    Set<Integer> allThreadIds = stateRWVarsMap.keySet();

    // build the disable set for all the states in stateRWVarsMap.
    Set<Integer> disableSet = new HashSet<>();
    for (Entry<Integer, Pair<EdgeVtx, BIPPORState>> e : stateRWVarsMap.entrySet()) {
      BIPPORState bipporState = e.getValue().getSecond();
      EdgeVtx curDepNode = e.getValue().getFirst();

      // if all the states in stateRWVarsMap are pure read events, their disabled state set
      // can be easily obtained.
      if (allSucPureRead) {
        if (disableSet.isEmpty()) {
          bipporState.addDisabledStateSet(new HashSet<>());
        } else {
          bipporState.addDisabledStateSet(allThreadIds);
        }

      } else {
        // otherwise, we need to compute the set of threads that are dependent with curDepNode,
        // and then remove them in current disableSet.
        ImmutableSet<Integer> depThreadIds =
            from(stateRWVarsMap.entrySet())
                .filter(n -> (condDepGraph.dep(curDepNode, n.getValue().getFirst()) != null))
                .transform(n -> n.getKey())
                .toSet();
        Set<Integer> curStateDisableSet = new HashSet<>(Sets.difference(disableSet, depThreadIds));

        bipporState.addDisabledStateSet(curStateDisableSet);
      }

      // update the disable set and flag.
      disableSet.add(e.getKey());
      bipporState.setupDisFlag();
    }
  }

  public boolean isThreadCreationEdge(final CFAEdge pEdge) {
    switch (pEdge.getEdgeType()) {
      case StatementEdge:
        {
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

  private boolean canSkip(
      boolean pCanSwapable, int pPreTid, CFAEdge pPreEdge, int pSucTid, CFAEdge pSucEdge) {
    if (!pCanSwapable) {
      return false;
    }

    DGNode depPreNode = condDepGraph.getDGNode(pPreEdge.hashCode()),
        depSucNode = condDepGraph.getDGNode(pSucEdge.hashCode());

    boolean containThreadCreationEdge =
        (isThreadCreationEdge(pPreEdge) || isThreadCreationEdge(pSucEdge));

    if (!containThreadCreationEdge
        && (pSucTid < pPreTid)
        && (condDepGraph.dep(depPreNode, depSucNode) == null)
        && !pPreEdge
            .getSuccessor()
            .isLoopStart()) { // TODO: loop start point should be carefully processed.
      return true;
    }
    return false;
  }
}
