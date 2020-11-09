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
package org.sosy_lab.cpachecker.cpa.por.ipor;

import static com.google.common.collect.FluentIterable.from;

import com.google.common.base.Function;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

public class IPORPrecisionAdjustment implements PrecisionAdjustment {

  private final Map<Integer, Integer> nExploredChildCache;
  private final Map<Integer, Triple<Set<ARGState>, Set<ARGState>, Set<ARGState>>>
      parentTypedChildCache;

  private static final Function<ARGState, Set<ARGState>> gvaEdgeFilter =
      (s) ->
          from(s.getChildren())
              .filter(
                  cs ->
                      AbstractStates.extractStateByType(cs, IPORState.class)
                          .getTransferInEdgeType()
                          .equals(EdgeType.GVAEdge))
              .toSet();
  private static final Function<ARGState, Set<ARGState>> nEdgeFilter =
      (s) ->
          from(s.getChildren())
              .filter(
                  cs ->
                      AbstractStates.extractStateByType(cs, IPORState.class)
                          .getTransferInEdgeType()
                          .equals(EdgeType.NEdge))
              .toSet();
  private static final Function<ARGState, Set<ARGState>> naEdgeFilter =
      (s) ->
          from(s.getChildren())
              .filter(
                  cs ->
                      AbstractStates.extractStateByType(cs, IPORState.class)
                          .getTransferInEdgeType()
                          .equals(EdgeType.NAEdge))
              .toSet();

  public IPORPrecisionAdjustment() {
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
      // we need to clean up the caches for some refinement based algorithms.
      if (argParState.getStateId() == 0) {
        nExploredChildCache.clear();
        parentTypedChildCache.clear();
      }

      IPORState iporCurState = AbstractStates.extractStateByType(argCurState, IPORState.class),
          iporParState = AbstractStates.extractStateByType(argParState, IPORState.class);
      int argParStateId = argParState.getStateId();

      // get all the three kinds of successors.
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

      //// explore the three kinds of successors respectively.
      // now, we firstly explore the normal successors.
      if (!nSuccessors.isEmpty()) {
        if (nSuccessors.contains(argCurState) && !nExploredChildCache.containsKey(argParStateId)) {
          int curStateInEdgePreNode =
              iporCurState.getTransferInEdge().getPredecessor().getNodeNumber();
          // it's the first time we explore the normal successor of argParState.
          nExploredChildCache.put(argParStateId, curStateInEdgePreNode);
          return Optional.of(
              PrecisionAdjustmentResult.create(
                  pState, pPrecision, PrecisionAdjustmentResult.Action.CONTINUE));
        } else {
          // we need not to explore other normal successors.
          return Optional.empty();
        }
      }
      assert nSuccessors.isEmpty();

      // now, we explore the normal assume successors.
      if (!naSuccessors.isEmpty()) {
        int curStateInEdgePreNode =
            iporCurState.getTransferInEdge().getPredecessor().getNodeNumber();
        if (naSuccessors.contains(argCurState)
            && (!nExploredChildCache.containsKey(argParStateId)
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

      // now, we explore the global variable access successors.
      return Optional.of(
          PrecisionAdjustmentResult.create(
              pState, pPrecision, PrecisionAdjustmentResult.Action.CONTINUE));
    }

    return Optional.of(
        PrecisionAdjustmentResult.create(
            pState, pPrecision, PrecisionAdjustmentResult.Action.CONTINUE));
  }
}
