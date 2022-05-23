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
package org.sosy_lab.cpachecker.cpa.por.ppor;

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
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.core.defaults.SingleEdgeTransferRelation;
import org.sosy_lab.cpachecker.core.defaults.SingletonPrecision;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.cpa.locations.LocationsCPA;
import org.sosy_lab.cpachecker.cpa.locations.LocationsState;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.dependence.DGNode;
import org.sosy_lab.cpachecker.util.dependence.conditional.ConditionalDepGraph;
import org.sosy_lab.cpachecker.util.dependence.conditional.ConditionalDepGraphBuilder;

public class PPORTransferRelation extends SingleEdgeTransferRelation {

  private final LocationsCPA locationsCPA;
  private final ConditionalDepGraphBuilder builder;
  private final ConditionalDepGraph condDepGraph;

  public PPORTransferRelation(Configuration pConfig, LogManager pLogger, CFA pCfa)
      throws InvalidConfigurationException {
    locationsCPA = LocationsCPA.create(pConfig, pLogger, pCfa);
    builder = new ConditionalDepGraphBuilder(pCfa, pConfig, pLogger);
    condDepGraph = builder.build();
  }

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessorsForEdge(
      AbstractState pState, Precision pPrecision, CFAEdge pCfaEdge)
      throws CPATransferException, InterruptedException {
    PPORState curState = (PPORState) pState;

    // compute new locations.
    Collection<? extends AbstractState> newStates =
        locationsCPA
            .getTransferRelation()
            .getAbstractSuccessorsForEdge(
                curState.getThreadLocs(), SingletonPrecision.getInstance(), pCfaEdge);
    assert newStates.size() <= 1;

    if(newStates.isEmpty()) {
      // no successor,
      return ImmutableSet.of();
    } else {
      int oldThreadIdNumber = curState.getTransferInEdgeThreadId();
      CFAEdge oldTransferEdge = curState.getTransferInEdge();

      LocationsState newLocs = (LocationsState) newStates.iterator().next();
      String transThreadId = newLocs.getTransferThreadId();

      boolean isThreadCreatedOrExited =
          newLocs.getMultiThreadState().getThreadIds().size()
              != curState.getThreadLocs().getMultiThreadState().getThreadIds().size();

      Map<String, Integer> oldThreadIdNumbers = curState.getThreadIdNumbers();
      // update the map of thread id number.
      Pair<Integer, Map<String, Integer>> newThreadIdInfo =
          updateThreadIdNumber(curState.getThreadCounter(), oldThreadIdNumbers, newLocs);
      int newThreadCounter = newThreadIdInfo.getFirst();
      Map<String, Integer> newThreadIdNumbers = newThreadIdInfo.getSecond();

      if (canSkip(
          oldThreadIdNumber,
          oldTransferEdge,
          newThreadIdNumbers.get(transThreadId),
          pCfaEdge,
          isThreadCreatedOrExited)) {
        return ImmutableSet.of();
      }

      return ImmutableSet.of(
          new PPORState(newThreadCounter, pCfaEdge, newLocs, newThreadIdNumbers));
    }
  }

  /**
   * This function is used for synchronize the thread id number of pNewLocs and pOldTidNumber. If a
   * new thread is created, we update pOldThreadCounter and put the newly created thread id pair
   * into the threadIdNumbers; if a thread is exit, we remove the exited thread pair; otherwise, we
   * do nothing.
   *
   * @param pOldThreadCounter The old thread counter, if new thread is created, this counter will be
   *     added 1.
   * @param pOldTidNumber The old thread id number map.
   * @param pNewLocs The newly generated thread locations.
   * @return This function returns a pair, the first component indicates the threadCounter of the
   *     new {@link PPORState} (thread creation will modify this value), and the second component
   *     indicates the map of synchronized thread id number.
   */
  private Pair<Integer, Map<String, Integer>> updateThreadIdNumber(
      int pOldThreadCounter,
      final Map<String, Integer> pOldTidNumber,
      final LocationsState pNewLocs) {
    Map<String, Integer> newTidNumber = new HashMap<>(pOldTidNumber);

    // remove exited threads.
    Set<String> newThreadIds = pNewLocs.getMultiThreadState().getThreadIds();
    ImmutableSet<String> removeTids =
        from(newTidNumber.keySet()).filter(t -> !newThreadIds.contains(t)).toSet();
    removeTids.forEach(t -> newTidNumber.remove(t));

    // add new thread id.
    ImmutableSet<String> addTids =
        from(newThreadIds).filter(t -> !newTidNumber.containsKey(t)).toSet();
    assert addTids.size() <= 1;
    if (!addTids.isEmpty()) {
      newTidNumber.put(addTids.iterator().next(), ++pOldThreadCounter);
    }

    return Pair.of(pOldThreadCounter, newTidNumber);
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
   *     utilization of PPOR into the normal states, i.e., avoids the application of PPOR out of
   *     bounds.
   */
  public boolean canSkip(
      int pPreTid,
      CFAEdge pPreEdge,
      int pSucTid,
      CFAEdge pSucEdge,
      boolean pThreadCreatedOrExited) {
    DGNode depPreNode = condDepGraph.getDGNode(pPreEdge.hashCode()),
        depSucNode = condDepGraph.getDGNode(pSucEdge.hashCode());

    if (!pThreadCreatedOrExited
        && !(pSucEdge.getPredecessor() instanceof FunctionEntryNode)
        && (pSucTid < pPreTid)
        && (condDepGraph.dep(depPreNode, depSucNode) == null)
        && !pPreEdge.getSuccessor().isLoopStart()) {
      return true;
    }
    return false;
  }

  public Statistics getCondDepGraphBuildStatistics() {
    return builder.getCondDepGraphBuildStatistics();
  }
}
