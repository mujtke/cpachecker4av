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
package org.sosy_lab.cpachecker.cpa.por.ippor;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.FluentIterable.from;

import com.google.common.base.Function;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
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

public class IPPORPrecisionAdjustment implements PrecisionAdjustment {

  private final ConditionalDepGraph condDepGraph;
  private final Map<Integer, Integer> nExploredChildCache;
  private final Map<Integer, Triple<Set<ARGState>, Set<ARGState>, Set<ARGState>>>
      parentTypedChildCache;

  private static final Function<ARGState, Set<ARGState>> gvaEdgeFilter =
      (s) ->
          from(s.getChildren())
              .filter(
                  cs ->
                      AbstractStates.extractStateByType(cs, IPPORState.class)
                          .getTransferInEdgeType()
                          .equals(EdgeType.GVAEdge))
              .toSet();
  private static final Function<ARGState, Set<ARGState>> nEdgeFilter =
      (s) ->
          from(s.getChildren())
              .filter(
                  cs ->
                      AbstractStates.extractStateByType(cs, IPPORState.class)
                          .getTransferInEdgeType()
                          .equals(EdgeType.NEdge))
              .toSet();
  private static final Function<ARGState, Set<ARGState>> naEdgeFilter =
      (s) ->
          from(s.getChildren())
              .filter(
                  cs ->
                      AbstractStates.extractStateByType(cs, IPPORState.class)
                          .getTransferInEdgeType()
                          .equals(EdgeType.NAEdge))
              .toSet();

  public IPPORPrecisionAdjustment(ConditionalDepGraph pCondDepGraph) {
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
      // we need to clean up the caches for some refinement base algorithms.
      if (argParState.getStateId() == 0) {
        cleanUpCaches();
      }

      IPPORState imporCurState = AbstractStates.extractStateByType(argCurState, IPPORState.class),
          imporParState = AbstractStates.extractStateByType(argParState, IPPORState.class);
      int argParStateId = argParState.getStateId();

      // checkout whether all the successor edges of argParState are GVAEdge, if true, we mark the
      // argCurState and argParState as a MPOR point.
      Set<ARGState> gvaSuccessors, naSuccessors, nSuccessors;
      if (parentTypedChildCache.containsKey(argParStateId)) {
        Triple<Set<ARGState>, Set<ARGState>, Set<ARGState>> typedChildCache =
            parentTypedChildCache.get(argParStateId);
        gvaSuccessors = typedChildCache.getFirst();
        naSuccessors = typedChildCache.getSecond();
        nSuccessors = typedChildCache.getThird();
      } else {
        gvaSuccessors = gvaEdgeFilter.apply(argParState);
        naSuccessors = naEdgeFilter.apply(argParState);
        nSuccessors = nEdgeFilter.apply(argParState);
        parentTypedChildCache.put(
            argParStateId, Triple.of(gvaSuccessors, naSuccessors, nSuccessors));
      }

      // update the mark of Inf/MPOR point of the current and parent states.
      boolean parStateMPORPoint =
          !gvaSuccessors.isEmpty() && naSuccessors.isEmpty() && nSuccessors.isEmpty();
      if (parStateMPORPoint && !imporCurState.isPporPoint()) {
        imporParState.setPporPoint(false);
        imporCurState.setPporPoint(true);
      }

      //
      boolean parStateInfPoint =
          gvaSuccessors.isEmpty() && !(naSuccessors.isEmpty() && nSuccessors.isEmpty());
      if (parStateInfPoint) {
        imporCurState.setPporPoint(false);
      }

      //// now, we explore the successor states according to the MPOR point mark.
      if (imporParState.isPporPoint() && imporCurState.isPporPoint()) {
        // for MPOR point.
        int oldThreadIdNumber = imporParState.getTransferInEdgeThreadId(),
            newThreadIdNumber = imporCurState.getTransferInEdgeThreadId();
        CFAEdge oldTransferEdge = imporParState.getTransferInEdge(),
            newTransferEdge = imporCurState.getTransferInEdge();
        boolean isThreadCreatedOrExited =
            imporParState.getThreadIdNumbers().keySet().size()
                != imporCurState.getThreadIdNumbers().keySet().size();

        if (canSkip(
            oldThreadIdNumber,
            oldTransferEdge,
            newThreadIdNumber,
            newTransferEdge,
            isThreadCreatedOrExited)) {
          return Optional.empty();
        }

        return Optional.of(
            PrecisionAdjustmentResult.create(
                pState, pPrecision, PrecisionAdjustmentResult.Action.CONTINUE));
      } else {
        // for not MPOR point.
        assert gvaSuccessors.isEmpty();

        // explore this 'normal' successor.
        if (!nSuccessors.isEmpty()) {
          if (nSuccessors.contains(argCurState)
              && !nExploredChildCache.containsKey(argParStateId)) {
            int curStateInEdgePreNode =
                imporCurState.getTransferInEdge().getPredecessor().getNodeNumber();
            // it's the first time we explore the normal successor of argParState.
            nExploredChildCache.put(argParStateId, curStateInEdgePreNode);
            return Optional.of(PrecisionAdjustmentResult.create(
              pState, pPrecision, PrecisionAdjustmentResult.Action.CONTINUE));
          } else {
            // we need not to explore other normal successors.
            return Optional.empty();
          }
        }
        assert nSuccessors.isEmpty();

        // explore this 'assume normal' successor.
        if (!naSuccessors.isEmpty()) {
          int curStateInEdgePreNode =
              imporCurState.getTransferInEdge().getPredecessor().getNodeNumber();
          if (naSuccessors.contains(argCurState)
              && (!nExploredChildCache.containsKey(
                      argParStateId) // have not explore this assume successor.
                  || nExploredChildCache.get(argParStateId).equals(curStateInEdgePreNode))) {
            // explore another assume successor which have the same precursor with the explored one.
            nExploredChildCache.put(argParStateId, curStateInEdgePreNode);
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
      }
    }

    return Optional.of(
        PrecisionAdjustmentResult.create(
            pState, pPrecision, PrecisionAdjustmentResult.Action.CONTINUE));
  }

  private void cleanUpCaches() {
    nExploredChildCache.clear();
    parentTypedChildCache.clear();
  }

  /**
   * This function determines whether the successor edge pSucEdge could be avoid to explore.
   *
   * @param pPreTid The thread-counter of precursor thread that executes pPreEdge.
   * @param pPreEdge The edge executed by the thread with thread-counter pPreTid.
   * @param pSucTid The thread-counter of successor thread that executes pSucEdge.
   * @param pSucEdge The edge executed by the thread with thread-counter pSucTid.
   * @param pThreadCreatedOrExited Whether the transfered edge induce an action of thread creation
   *     or exit.
   * @return Return true if: 1) pSucTid < pPreTid (monotonic property 1); 2) the two edges are
   *     independent; 3) the precursor edge pPreEdge could not induces a back edge in the thread's
   *     control flow; 4) the transfered edge does not induce thread creation or exit; 5) skip the
   *     starting edge of a created thread.
   * @implNote Notice that, the three constraints 3), 4) and 5) are used for confining the
   *     utilization of MPOR into the normal states, i.e., avoids the application of MPOR out of
   *     bounds.
   */
  public boolean canSkip(
      int pPreTid,
      CFAEdge pPreEdge,
      int pSucTid,
      CFAEdge pSucEdge,
      boolean pThreadCreatedOrExited) {
    DGNode depPreNode = condDepGraph.getDGNode(pPreEdge),
        depSucNode = condDepGraph.getDGNode(pSucEdge);

    if (!pThreadCreatedOrExited
//        && !((pSucEdge.getPredecessor() instanceof FunctionEntryNode)     // this condition could be removed.
//            || (pPreEdge.getPredecessor() instanceof FunctionEntryNode))
        && (pSucTid < pPreTid)
        && (condDepGraph.dep(depPreNode, depSucNode) == null)
        && !pPreEdge.getSuccessor().isLoopStart()) {
      return true;
    }
    return false;
  }
}
