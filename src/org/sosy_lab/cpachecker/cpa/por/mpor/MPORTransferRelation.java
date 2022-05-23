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
package org.sosy_lab.cpachecker.cpa.por.mpor;

import static com.google.common.collect.FluentIterable.from;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Table;
import java.util.Collection;
import java.util.Set;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.defaults.SingleEdgeTransferRelation;
import org.sosy_lab.cpachecker.core.defaults.SingletonPrecision;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.cpa.locations.LocationsCPA;
import org.sosy_lab.cpachecker.cpa.locations.LocationsState;
import org.sosy_lab.cpachecker.cpa.locations.LocationsTransferRelation;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.Triple;
import org.sosy_lab.cpachecker.util.dependence.conditional.ConditionalDepGraph;
import org.sosy_lab.cpachecker.util.dependence.conditional.ConditionalDepGraphBuilder;
import org.sosy_lab.cpachecker.util.statistics.ThreadSafeTimerContainer.TimerWrapper;

public class MPORTransferRelation extends SingleEdgeTransferRelation {

  private final MPORStatistics statistics;
  private final TimerWrapper sucGenTimer;
  private final TimerWrapper sucLocGenTimer;
  private final TimerWrapper threadsDynInfoTimer;
  private final TimerWrapper threadsDepChainTimer;
  private final TimerWrapper canScheduleCheckTimer;

  private final LocationsTransferRelation locsTransferRelation;
  private final ConditionalDepGraphBuilder builder;
  private final ConditionalDepGraph condDepGraph;

  public MPORTransferRelation(Configuration pConfig, LogManager pLogger, CFA pCfa)
      throws InvalidConfigurationException {
    statistics = new MPORStatistics();
    sucGenTimer = statistics.successorGenTimer.getNewTimer();
    sucLocGenTimer = statistics.successorLocGenTimer.getNewTimer();
    threadsDynInfoTimer = statistics.threadsDynamicInfoUpdateTimer.getNewTimer();
    threadsDepChainTimer = statistics.threadsDepChainUpdateTimer.getNewTimer();
    canScheduleCheckTimer = statistics.canScheduleCheckTimer.getNewTimer();

    locsTransferRelation =
        (LocationsTransferRelation)
            LocationsCPA.create(pConfig, pLogger, pCfa).getTransferRelation();
    builder = new ConditionalDepGraphBuilder(pCfa, pConfig, pLogger);
    condDepGraph = builder.build();
  }

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessorsForEdge(
      AbstractState pState, Precision pPrecision, CFAEdge pCfaEdge)
      throws CPATransferException, InterruptedException {
    sucGenTimer.start();
    MPORState curState = (MPORState) pState;

    // compute new locations.
    sucLocGenTimer.start();
    Collection<? extends AbstractState> newStates =
        locsTransferRelation.getAbstractSuccessorsForEdge(
            curState.getThreadsLoc(), SingletonPrecision.getInstance(), pCfaEdge);
    sucLocGenTimer.stop();
    assert newStates.size() <= 1;

    if (newStates.isEmpty()) {
      // no successor.
      sucGenTimer.stop();
      return ImmutableSet.of();
    } else {
      // get information from the old state.
      int oldThreadCounter = curState.getThreadCounter();
      ThreadsDynamicInfo oldThreadsDynamicInfo = curState.getThreadsDynamicInfo();
      Table<Integer, Integer, Integer> oldThreadsDepChain = curState.getThreadsDepChain();

      // get the new locations.
      LocationsState newLocs = (LocationsState) newStates.iterator().next();

      // get the created and exited threads.
      Pair<String, String> ceThreadPair =
          getCreatedAndExitThreadIds(curState.getThreadsLoc(), newLocs);
      String createdThread = ceThreadPair.getFirst(), exitedThread = ceThreadPair.getSecond();

      // check whether a thread is created or exit.
      boolean isThreadCreatedOrExited = (createdThread != null) || (exitedThread != null);

      // update the threads dynamic information.
      threadsDynInfoTimer.start();
      Pair<Integer, ThreadsDynamicInfo> newThreadsDynamicInfoPair =
          updateThreadsDynamicInfo(
              oldThreadCounter, oldThreadsDynamicInfo, newLocs, createdThread, pCfaEdge);
      int newThreadCounter = newThreadsDynamicInfoPair.getFirst();
      ThreadsDynamicInfo newThreadsDynamicInfo = newThreadsDynamicInfoPair.getSecond();
      threadsDynInfoTimer.stop();

      // update the dependency chain.
      threadsDepChainTimer.start();
      int transThreadNumber = newThreadsDynamicInfo.getThreadDynamicInfo(newLocs.getTransferThreadId()).getFirst();
      Table<Integer, Integer, Integer> newThreadsDepChain =
          updateThreadsDepChain(
              oldThreadsDepChain,
              oldThreadsDynamicInfo,
              newThreadsDynamicInfo,
              transThreadNumber,
              createdThread,
              exitedThread);
      statistics.sizeDepChain.setNextValue(newThreadsDepChain.rowKeySet().size());
      threadsDepChainTimer.stop();

      // determine whether we should generate the new successor.
      canScheduleCheckTimer.start();
      CFAEdge preEdge =
          curState
              .getThreadsDynamicInfo()
              .getThreadDynamicInfo(curState.getThreadsLoc().getTransferThreadId())
              .getSecond();
      if (canSchedule(
          oldThreadsDepChain,
          newThreadsDepChain,
          newThreadsDynamicInfo,
          transThreadNumber,
          isThreadCreatedOrExited,
          preEdge,
          pCfaEdge)) {
        canScheduleCheckTimer.stop();
        sucGenTimer.stop();
        return ImmutableSet.of(
            new MPORState(newThreadCounter, newLocs, newThreadsDynamicInfo, newThreadsDepChain));
      }
      canScheduleCheckTimer.stop();
    }

    sucGenTimer.stop();
    return ImmutableSet.of();
  }

  public boolean canSchedule(
      final Table<Integer, Integer, Integer> pOldDepChain,
      final Table<Integer, Integer, Integer> pNewDepChain,
      final ThreadsDynamicInfo pNewThreadsDynamicInfo,
      final int pTransThreadNumber,
      final boolean pThreadCreatedOrExited,
      final CFAEdge pPreEdge,
      final CFAEdge pSucEdge) {
    int i = pTransThreadNumber;
    Set<Integer> js = pNewThreadsDynamicInfo.getBiggerThreadNumbers(i),
        ls = pNewThreadsDynamicInfo.getSmallerThreadNumbers(i);

    boolean schConstraint = true;
    // S_i(k) = /\_{j > i} (DC_ji(k) != -1 v \/_{l < i} (DC_jl(k - 1) = 1))
    for (Integer j : js) {
      if (pNewDepChain.get(j, i) != -1) {
        // DC_ji(k) != -1
        continue;
      } else {
        // DC_ji(k) == -1
        boolean existSubDepChain = false;
        for (Integer l : ls) {
          if (pOldDepChain.get(j, l) == 1) {
            // \/_{l < i} (DC_jl(k - 1) = 1)
            existSubDepChain = true;
            break;
          }
        }

        if (!existSubDepChain) {
          schConstraint = false;
          break;
        }
      }
    }

    return schConstraint;
    //// for test, we can enable the codes bellow and disable the return statement above.
    //    if (!pThreadCreatedOrExited
    //        && !(pSucEdge.getPredecessor() instanceof FunctionEntryNode)
    //        && !schConstraint
    //        && !pPreEdge.getSuccessor().isLoopStart()) {
    //      return false;
    //    }
    //
    //    return true;
  }

  public Pair<Integer, ThreadsDynamicInfo> updateThreadsDynamicInfo(
      int pOldThreadCounter,
      final ThreadsDynamicInfo pOldThreadDynamicInfo,
      final LocationsState pNewLocs,
      final String pCreatedThreadId,
      final CFAEdge pTransferEdge) {
    Set<String> oldThreadIds = pOldThreadDynamicInfo.getThreadIds();
    ThreadsDynamicInfo newThreadsDynamicInfo = new ThreadsDynamicInfo(pOldThreadDynamicInfo);

    // remove exited threads.
    Set<String> newThreadIds = pNewLocs.getMultiThreadState().getThreadIds();
    ImmutableSet<String> removeTids =
        from(oldThreadIds).filter(t -> !newThreadIds.contains(t)).toSet();
    removeTids.forEach(t -> newThreadsDynamicInfo.removeThreadDynamicInfo(t));

    // add new thread id.
    if (pCreatedThreadId != null && !pCreatedThreadId.isEmpty()) {
      CFAEdge createdThreadInitEdge =
          pNewLocs.getMultiThreadState().getThreadLocation(pCreatedThreadId).getLeavingEdge(0);
      newThreadsDynamicInfo.addThreadDynamicInfo(
          Triple.of(pCreatedThreadId, ++pOldThreadCounter, createdThreadInitEdge));
    }

    // update the dynamic information of transfered thread.
    String transferThreadId = pNewLocs.getTransferThreadId();
    newThreadsDynamicInfo.updateThreadInEdge(transferThreadId, pTransferEdge);

    return Pair.of(pOldThreadCounter, newThreadsDynamicInfo);
  }

  public Table<Integer, Integer, Integer> updateThreadsDepChain(
      final Table<Integer, Integer, Integer> pOldThreadsDepChain,
      final ThreadsDynamicInfo pOldThreadsDynamicInfo,
      final ThreadsDynamicInfo pNewThreadsDynamicInfo,
      final int pTransTreadNumber,
      final String pCreatedThreadId,
      final String pExitedThreadId) {
    HashBasedTable<Integer, Integer, Integer> newThreadsDepChain =
        HashBasedTable.create(pOldThreadsDepChain);
    Integer i = pTransTreadNumber;

    //// update the threads dependency chain.

    // rule 1: DC_ii(k) = 1;
    newThreadsDepChain.put(i, i, 1);

    // rule 2: DC_ij(k) = -1 when j != i;
    for (Integer j : newThreadsDepChain.columnKeySet()) {
      if (j != i) {
        newThreadsDepChain.put(i, j, -1);
      }
    }

    // rule 3 and rule 4:
    for (Integer j : newThreadsDepChain.rowKeySet()) {
      if (j != i) {
        if (pOldThreadsDepChain.get(j, j) == 0) {
          // DC_ji(k) = 0 when j != i and DC_jj(k - 1) = 0;
          newThreadsDepChain.put(j, i, 0);
        } else {
          // DC_ji(k) = ... when j != i and DC_jj(k - 1) != 0;
          boolean findDepChain = false;
          for (Integer l : pOldThreadsDepChain.columnKeySet()) {
            if (pOldThreadsDepChain.get(j, l) == 1 && dep(pNewThreadsDynamicInfo, l, i)) {
              // we can extend the dependence chain of DC_jl to DC_ji.
              newThreadsDepChain.put(j, i, 1);
              findDepChain = true;
              break;
            }
          }
          if (!findDepChain) {
            newThreadsDepChain.put(j, i, -1);
          }
        }
      }
    }

    // rule 5: DC_pq(k) = DC_pq(k - 1) when p != i and q != i.
    // we do nothing for this rule.

    // update the threads dynamic information for the newly created thread.
    if (pCreatedThreadId != null) {
      assert pNewThreadsDynamicInfo.containThread(pCreatedThreadId);
      Integer createdThreadNumber =
          pNewThreadsDynamicInfo.getThreadDynamicInfo(pCreatedThreadId).getFirst();

      // add 'no execute' dependency chain.
      Set<Integer> oldThreadIds = Set.copyOf(newThreadsDepChain.rowKeySet());
      for (Integer row : oldThreadIds) {
        newThreadsDepChain.put(row, createdThreadNumber, 0);
        newThreadsDepChain.put(createdThreadNumber, row, 0);
      }
      newThreadsDepChain.put(createdThreadNumber, createdThreadNumber, 0);
    }

    // remove the dependency chain of exited threads.
    //    if (pExitedThreadId != null) {
    //      Integer removeThreadNumber =
    //          pOldThreadsDynamicInfo.getThreadDynamicInfo(pExitedThreadId).getFirst();
    //      Set<Integer> allThreadNumbers = Set.copyOf(newThreadsDepChain.rowKeySet());
    //      allThreadNumbers.forEach(t -> newThreadsDepChain.remove(t, removeThreadNumber));
    //      allThreadNumbers.forEach(t -> newThreadsDepChain.remove(removeThreadNumber, t));
    //    }

    return newThreadsDepChain;
  }

  public boolean dep(
      ThreadsDynamicInfo pThreadsDynamicInfo, Integer pThreadNumberL, Integer pThreadNumberI) {
    if (pThreadNumberL == pThreadNumberI) {
      return true;
    }

    CFAEdge threadLInEdge = pThreadsDynamicInfo.getThreadEdgeByNumber(pThreadNumberL),
        threadIInEdge = pThreadsDynamicInfo.getThreadEdgeByNumber(pThreadNumberI);

    if (threadLInEdge == null || threadIInEdge == null) {
      return false;
    } else {
      return (condDepGraph.dep(
              condDepGraph.getBlockDGNode(threadLInEdge.hashCode()),
              condDepGraph.getBlockDGNode(threadIInEdge.hashCode())))
          != null;
    }
  }

  public Pair<String, String> getCreatedAndExitThreadIds(
      LocationsState pOldLocs, LocationsState pNewLocs) {
    Set<String> oldThreadIds = pOldLocs.getMultiThreadState().getThreadIds(),
        newThreadIds = pNewLocs.getMultiThreadState().getThreadIds();

    ImmutableSet<String> createdThreads =
        from(newThreadIds).filter(t -> !oldThreadIds.contains(t)).toSet();
    ImmutableSet<String> exitedThreads =
        from(oldThreadIds).filter(t -> !newThreadIds.contains(t)).toSet();
    assert createdThreads.size() <= 1 && exitedThreads.size() <= 1;

    return Pair.of(
        createdThreads.isEmpty() ? null : createdThreads.iterator().next(),
        exitedThreads.isEmpty() ? null : exitedThreads.iterator().next());
  }

  public String getCreatedThreadId(LocationsState pOldLocs, LocationsState pNewLocs) {
    Set<String> oldThreadIds = pOldLocs.getMultiThreadState().getThreadIds(),
        newThreadIds = pNewLocs.getMultiThreadState().getThreadIds();

    ImmutableSet<String> newThreads =
        from(newThreadIds).filter(t -> !oldThreadIds.contains(t)).toSet();
    assert newThreads.size() <= 1;

    return newThreads.isEmpty() ? null : newThreads.iterator().next();
  }

  public Statistics getCondDepGraphBuildStatistics() {
    return builder.getCondDepGraphBuildStatistics();
  }

  public Statistics getMPORStatistics() {
    return statistics;
  }
}
