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
package org.sosy_lab.cpachecker.cpa.por.ippor2;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.FluentIterable.from;

import com.google.common.base.Function;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.por.EdgeType;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.Triple;
import org.sosy_lab.cpachecker.util.dependence.DGNode;
import org.sosy_lab.cpachecker.util.dependence.conditional.ConditionalDepGraph;

public class IPPOR2PrecisionAdjustment implements PrecisionAdjustment {

  private final ConditionalDepGraph condDepGraph;
  private final Map<Integer, Integer> nExploredChildCache;
  private final Map<Integer, Triple<Set<ARGState>, Set<ARGState>, Set<ARGState>>>
      parentTypedChildCache;

  private static final Function<ARGState, Set<ARGState>> gvaEdgeFilter =
      (s) ->
          from(s.getChildren())
              .filter(
                  cs ->
                      AbstractStates.extractStateByType(cs, IPPOR2State.class)
                          .getTransferInEdgeType()
                          .equals(EdgeType.GVAEdge))
              .toSet();
  private static final Function<ARGState, Set<ARGState>> nEdgeFilter =
      (s) ->
          from(s.getChildren())
              .filter(
                  cs ->
                      AbstractStates.extractStateByType(cs, IPPOR2State.class)
                          .getTransferInEdgeType()
                          .equals(EdgeType.NEdge))
              .toSet();
  private static final Function<ARGState, Set<ARGState>> naEdgeFilter =
      (s) ->
          from(s.getChildren())
              .filter(
                  cs ->
                      AbstractStates.extractStateByType(cs, IPPOR2State.class)
                          .getTransferInEdgeType()
                          .equals(EdgeType.NAEdge))
              .toSet();

  public IPPOR2PrecisionAdjustment(ConditionalDepGraph pCondDepGraph) {
    condDepGraph = checkNotNull(pCondDepGraph);
    nExploredChildCache = new HashMap<>();
    parentTypedChildCache = new HashMap<>();
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
      // we need ot clean up the caches for some refinement based algorithms.
      if (argParState.getStateId() == 0) {
        nExploredChildCache.clear();
        parentTypedChildCache.clear();
      }

      IPPOR2State
          ippor2CurState = AbstractStates.extractStateByType(argCurState, IPPOR2State.class),
          ippor2ParState = AbstractStates.extractStateByType(argParState, IPPOR2State.class);
      int argParStateId = argParState.getStateId();

      // checkout whether all the successor edges of argParState are GVAEdge.
      Set<ARGState> gvaSuccessors, naSuccessors, nSuccessors;
      if (parentTypedChildCache.containsKey(argParStateId)) {
        Triple<Set<ARGState>, Set<ARGState>, Set<ARGState>> typedChildCache =
            parentTypedChildCache.get(argParStateId);
        gvaSuccessors = typedChildCache.getFirst();
        naSuccessors = typedChildCache.getSecond();
        nSuccessors = typedChildCache.getThird();
      } else {
        gvaSuccessors = new HashSet<>(gvaEdgeFilter.apply(argParState));
        naSuccessors = new HashSet<>(naEdgeFilter.apply(argParState));
        nSuccessors = new HashSet<>(nEdgeFilter.apply(argParState));
        parentTypedChildCache.put(
            argParStateId, Triple.of(gvaSuccessors, naSuccessors, nSuccessors));
      }

      if (!ippor2ParState.isPporPoint()) {
        // get the precursor node of the transfered in edge.
        int curStateInEdgePreNode =
            ippor2CurState.getTransferInEdge().getPredecessor().getNodeNumber();
        // explore this 'normal' successor.
        if (!nSuccessors.isEmpty()) {
          return handleNEdgeSuccessor(
              nSuccessors, argCurState, argParStateId, curStateInEdgePreNode, pState, pPrecision);
        }
        assert nSuccessors.isEmpty();

        // explore this 'assume normal' successor.
        if (!naSuccessors.isEmpty()) {
          return handleNAEdgeSuccessor(
              naSuccessors, argCurState, argParStateId, curStateInEdgePreNode, pState, pPrecision);
        }
        assert naSuccessors.isEmpty();

        // explore all the 'global variable access' successors.
        return Optional.of(
            PrecisionAdjustmentResult.create(
                pState, pPrecision, PrecisionAdjustmentResult.Action.CONTINUE));
      } else {
        // explore all the 'global variable access' successors.
        if (!gvaSuccessors.isEmpty()) {
          if (gvaSuccessors.contains(argCurState)) {
            return handleGVAEdgeSuccessor(
                gvaSuccessors, argCurState, ippor2ParState, ippor2CurState, pState, pPrecision);
          }
        }
        assert gvaSuccessors.isEmpty();

        // get the precursor node of the transfered in edge.
        int curStateInEdgePreNode =
            ippor2CurState.getTransferInEdge().getPredecessor().getNodeNumber();
        // explore this 'normal' successor.
        if (!nSuccessors.isEmpty()) {
          return handleNEdgeSuccessor(
              nSuccessors, argCurState, argParStateId, curStateInEdgePreNode, pState, pPrecision);
        }
        assert nSuccessors.isEmpty();

        // explore this 'assume normal' successor.
        if (!naSuccessors.isEmpty()) {
          return handleNAEdgeSuccessor(
              naSuccessors, argCurState, argParStateId, curStateInEdgePreNode, pState, pPrecision);
        }
        assert naSuccessors.isEmpty();
      }
    }

    return Optional.empty();

    //    return Optional.of(
    //        PrecisionAdjustmentResult.create(
    //            pState, pPrecision, PrecisionAdjustmentResult.Action.CONTINUE));
  }
  
  
  public Optional<PrecisionAdjustmentResult> handleNEdgeSuccessor(
      Set<ARGState> pNSuccessors,
      ARGState pCurState,
      int pParStateId,
      int pCurStateInEdgePreNode,
      AbstractState pState,
      Precision pPrecision) {
    // explore this 'normal' successor.
    if (pNSuccessors.contains(pCurState) && !nExploredChildCache.containsKey(pParStateId)) {
      // it's the first time we explore the normal successor of argParState.
      nExploredChildCache.put(pParStateId, pCurStateInEdgePreNode);
      return Optional.of(
          PrecisionAdjustmentResult.create(
              pState, pPrecision, PrecisionAdjustmentResult.Action.CONTINUE));
    } else {
      // we need not to explore other normal successors.
      return Optional.empty();
    }
  }

  public Optional<PrecisionAdjustmentResult> handleNAEdgeSuccessor(
      Set<ARGState> pNASuccessors,
      ARGState pCurState,
      int pParStateId,
      int pCurStateInEdgePreNode,
      AbstractState pState,
      Precision pPrecision) {
    // explore this 'assume normal' successor.
    if (pNASuccessors.contains(pCurState)
        && (!nExploredChildCache.containsKey(pParStateId) // have not explore this assume successor.
            || nExploredChildCache.get(pParStateId).equals(pCurStateInEdgePreNode))) {
      // explore another assume successor which have the same precursor with the explored one.
      nExploredChildCache.put(pParStateId, pCurStateInEdgePreNode);
      return Optional.of(
          PrecisionAdjustmentResult.create(
              pState, pPrecision, PrecisionAdjustmentResult.Action.CONTINUE));
    } else {
      // a common assume branches have already explored, we need not explore other assume
      // branches (i.e., we have explored the assume branches of a thread).
      return Optional.empty();
    }
  }

  public Optional<PrecisionAdjustmentResult> handleGVAEdgeSuccessor(
      Set<ARGState> pGVASuccessors,
      ARGState pCurArgState,
      IPPOR2State pParState,
      IPPOR2State pCurState,
      AbstractState pState,
      Precision pPrecision) {
    // explore all the 'global variable access' successors.
    int oldThreadIdNumber = pParState.getTransferInEdgeThreadId(),
        newThreadIdNumber = pCurState.getTransferInEdgeThreadId();
    CFAEdge oldTransferEdge = pParState.getTransferInEdge(),
        newTransferEdge = pCurState.getTransferInEdge();
    boolean isThreadCreatedOrExited =
        pParState.getThreadIdNumbers().keySet().size()
            != pCurState.getThreadIdNumbers().keySet().size();

    if (canSkip(
        oldThreadIdNumber,
        oldTransferEdge,
        newThreadIdNumber,
        newTransferEdge,
        isThreadCreatedOrExited)) {
      // remove the skipped 'global variable access' successor.
      // test case: pthread-lit/qw2004_false-unreach-call.i
      pGVASuccessors.remove(pCurArgState);
      return Optional.empty();
    }

    return Optional.of(
        PrecisionAdjustmentResult.create(
            pState, pPrecision, PrecisionAdjustmentResult.Action.CONTINUE));
  }

  public boolean canSkip(
      int pPreTid,
      CFAEdge pPreEdge,
      int pSucTid,
      CFAEdge pSucEdge,
      boolean pThreadCreatedOrExited) {
    DGNode depPreNode = condDepGraph.getDGNode(pPreEdge.hashCode()),
        depSucNode = condDepGraph.getDGNode(pSucEdge.hashCode());

    if (!pThreadCreatedOrExited
        && !((pSucEdge.getPredecessor() instanceof FunctionEntryNode)
            || (pPreEdge.getPredecessor() instanceof FunctionEntryNode))
        && (pSucTid < pPreTid)
        && (condDepGraph.dep(depPreNode, depSucNode) == null)
        && !pPreEdge.getSuccessor().isLoopStart()) {
      return true;
    }
    return false;
  }
}
