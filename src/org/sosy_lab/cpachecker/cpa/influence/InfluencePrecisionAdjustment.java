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
package org.sosy_lab.cpachecker.cpa.influence;

import static com.google.common.collect.FluentIterable.from;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.AbstractStates;

public class InfluencePrecisionAdjustment implements PrecisionAdjustment {

  private final LogManager logger;

  private static Map<Integer, Set<Integer>> normalCache;
  private static Map<Integer, Set<Integer>> assumeCache;

  public InfluencePrecisionAdjustment(LogManager pLogger) {
    logger = pLogger;
    normalCache = new HashMap<>();
    assumeCache = new HashMap<>();
  }

  @Override
  public Optional<PrecisionAdjustmentResult> prec(
      AbstractState pState,
      Precision pPrecision,
      UnmodifiableReachedSet pStates,
      Function<AbstractState, AbstractState> pStateProjection,
      AbstractState pFullState)
      throws CPAException, InterruptedException {
    // TODO Auto-generated method stub

    // get the parent influence and ARG state.
    if(pFullState instanceof ARGState) {
      ARGState curArgState = (ARGState) pFullState;
      ARGState parArgState = ((ARGState) pFullState).getParents().iterator().next();
      InfluenceState parInfState =
          AbstractStates.extractStateByType(parArgState.getWrappedState(), InfluenceState.class);
      int curArgStateId = curArgState.getStateId(), parArgStateId = parArgState.getStateId();

      if (parArgState.getStateId() == 285) {
        int k = 0;
        ++k;
      }
      if (curArgState.getStateId() == 287) {
        int k = 0;
        ++k;
      }

      // mark the parent influence state have been updated.
      if (!normalCache.containsKey(parArgStateId)) {
        Collection<ARGState> childArgStates = parArgState.getChildren();

        // short cut.
        if(childArgStates.size() == 1) {
          parInfState.markUpdated(true);
          return Optional.of(
              PrecisionAdjustmentResult
                  .create(pState, pPrecision, PrecisionAdjustmentResult.Action.CONTINUE));
        }

        ImmutableSet<Integer> normalStates =
            from(childArgStates).filter(
                s -> AbstractStates.extractStateByType(s.getWrappedState(), InfluenceState.class)
                    .isCurStateNormal())
                .transform(s -> {
                  return (Integer) s.getStateId();
                })
                .toSet();

        // we only preserve the normal assume edges.
        ImmutableSet<Integer> assumeStates = from(childArgStates).filter(s -> {
          return (parArgState.getEdgeToChild(s) instanceof AssumeEdge);
        }).filter(s -> {
          return AbstractStates.extractStateByType(s.getWrappedState(), InfluenceState.class)
              .isCurStateNormal();
        }).transform(s -> s.getStateId()).toSet();

        assumeCache.put(parArgStateId, assumeStates);
        normalCache.put(parArgStateId, Sets.difference(normalStates, assumeStates));
        parInfState.markUpdated(true);
      }

      // System.out.println("state: " + parArgState.getStateId() + "\t\tinfluence: " + parInfState);

      //
      Set<Integer> parNormalSucs = normalCache.get(parArgStateId);
      Set<Integer> parAssumeSucs = normalCache.get(parArgStateId);

      if (!parNormalSucs.isEmpty()) {
        if (parNormalSucs.contains(curArgStateId)) {
          // normal successor (only preserve one normal successor of the parArgState).
          normalCache.put(parArgStateId, Sets.newHashSet(curArgStateId));
        } else {
          // other kind of successors.
          return Optional.empty();
        }
      } else {
        if (!parAssumeSucs.isEmpty()) {
          // normal assume successor.
          if (parAssumeSucs.contains(curArgStateId)) {
            parAssumeSucs.remove(curArgStateId);
          } else {
            // read assume successor.
            return Optional.empty();
          }
        } else {
          // r/w successor (includes r/w statement & read assume)
        }
      }
    }

    return Optional.of(
        PrecisionAdjustmentResult
            .create(pState, pPrecision, PrecisionAdjustmentResult.Action.CONTINUE));
  }

}
