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

import static com.google.common.collect.FluentIterable.from;

import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.core.defaults.SingleEdgeTransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.cpa.locationss.LocationsCPA;
import org.sosy_lab.cpachecker.cpa.locationss.LocationsState;
import org.sosy_lab.cpachecker.cpa.locationss.LocationsTransferRelation;
import org.sosy_lab.cpachecker.cpa.por.EdgeType;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.dependence.conditional.ConditionalDepGraph;
import org.sosy_lab.cpachecker.util.dependence.conditional.ConditionalDepGraphBuilder;

public class IPPOR2TransferRelation extends SingleEdgeTransferRelation {

  private final LocationsTransferRelation locationsTransferRelation;
  private final ConditionalDepGraphBuilder builder;
  private final ConditionalDepGraph condDepGraph;

  public static final String THREAD_START = "pthread_create";
  public static final String THREAD_JOIN = "pthread_join";
  private static final String THREAD_EXIT = "pthread_exit";
  private static final String THREAD_MUTEX_LOCK = "pthread_mutex_lock";
  private static final String THREAD_MUTEX_UNLOCK = "pthread_mutex_unlock";
  private static final String VERIFIER_ATOMIC = "__VERIFIER_atomic_";
  private static final String VERIFIER_ATOMIC_BEGIN = "__VERIFIER_atomic_begin";
  private static final String VERIFIER_ATOMIC_END = "__VERIFIER_atomic_end";
  private static final String ATOMIC_LOCK = "__CPAchecker_atomic_lock__";
  private static final String LOCAL_ACCESS_LOCK = "__CPAchecker_local_access_lock__";
  private static final String THREAD_ID_SEPARATOR = "__CPAchecker__";

  private static final ImmutableSet<String> THREAD_FUNCTIONS =
      ImmutableSet.of(
          THREAD_START,
          THREAD_MUTEX_LOCK,
          THREAD_MUTEX_UNLOCK,
          THREAD_JOIN,
          THREAD_EXIT,
          VERIFIER_ATOMIC_BEGIN,
          VERIFIER_ATOMIC_END);

  public IPPOR2TransferRelation(Configuration pConfig, LogManager pLogger, CFA pCfa)
      throws InvalidConfigurationException {
    locationsTransferRelation =
        (LocationsTransferRelation)
            LocationsCPA.create(pConfig, pLogger, pCfa).getTransferRelation();
    builder = new ConditionalDepGraphBuilder(pCfa, pConfig, pLogger);
    condDepGraph = builder.build();
  }

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessorsForEdge(
      AbstractState pState, Precision pPrecision, CFAEdge pCfaEdge)
      throws CPATransferException, InterruptedException {
    IPPOR2State curState = (IPPOR2State) pState;

    // compute new locations.
    Collection<? extends AbstractState> newStates =
        locationsTransferRelation.getAbstractSuccessorsForEdge(
            curState.getThreadLocs(), pPrecision, pCfaEdge);
    assert newStates.size() <= 1;

    if (newStates.isEmpty()) {
      // no successor.
      return ImmutableSet.of();
    } else {
      LocationsState newLocs = (LocationsState) newStates.iterator().next();

      Pair<Integer, Map<String, Integer>> newThreadIdInfo =
          updateThreadIdNumber(curState.getThreadCounter(), curState.getThreadIdNumbers(), newLocs);
      int newThreadCounter = newThreadIdInfo.getFirst();
      Map<String, Integer> newThreadIdNumbers = newThreadIdInfo.getSecond();

      return ImmutableSet.of(
          new IPPOR2State(
              newThreadCounter,
              pCfaEdge,
              determineEdgeType(pCfaEdge),
              newLocs,
              newThreadIdNumbers));
    }
  }

  private Pair<Integer, Map<String, Integer>> updateThreadIdNumber(
      int pOldThreadCounter,
      final Map<String, Integer> pOldTidNumbers,
      final LocationsState pNewLocs) {
    Map<String, Integer> newTidNumbers = new HashMap<>(pOldTidNumbers);

    // remove exited threads;
    Set<String> newThreadIds = pNewLocs.getMultiThreadState().getThreadIds();
    ImmutableSet<String> removeTids =
        from(pOldTidNumbers.keySet()).filter(t -> !newThreadIds.contains(t)).toSet();
    removeTids.forEach(t -> newTidNumbers.remove(t));

    // add new thread id.
    ImmutableSet<String> addTids =
        from(newThreadIds).filter(t -> !pOldTidNumbers.containsKey(t)).toSet();
    assert addTids.size() <= 1;
    if (!addTids.isEmpty()) {
      newTidNumbers.put(addTids.iterator().next(), ++pOldThreadCounter);
    }

    return Pair.of(pOldThreadCounter, newTidNumbers);
  }

  public EdgeType determineEdgeType(final CFAEdge pEdge) {
    assert pEdge != null;

    if (condDepGraph.contains(pEdge.hashCode())) {
      return EdgeType.GVAEdge;
    } else if (pEdge instanceof CAssumeEdge) {
      return EdgeType.NAEdge;
    } else {
      return EdgeType.NEdge;
    }
  }

  public Statistics getCondDepGraphBuilderStatistics() {
    return builder.getCondDepGraphBuildStatistics();
  }

  public ConditionalDepGraph getCondDepGraph() {
    return condDepGraph;
  }
}
