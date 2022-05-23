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
package org.sosy_lab.cpachecker.cpa.por.bppor;

import static com.google.common.collect.FluentIterable.from;

import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.ast.AExpression;
import org.sosy_lab.cpachecker.cfa.ast.AFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.AIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.AStatement;
import org.sosy_lab.cpachecker.cfa.model.AStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.defaults.SingleEdgeTransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.cpa.locations.LocationsCPA;
import org.sosy_lab.cpachecker.cpa.locations.LocationsState;
import org.sosy_lab.cpachecker.cpa.locations.LocationsTransferRelation;
import org.sosy_lab.cpachecker.cpa.por.ppor.PeepholeState;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.dependence.DGNode;
import org.sosy_lab.cpachecker.util.dependence.conditional.ConditionalDepGraph;
import org.sosy_lab.cpachecker.util.dependence.conditional.ConditionalDepGraphBuilder;

@Options(prefix = "cpa.por.bppor")
public class BPPORTransferRelation extends SingleEdgeTransferRelation {

  private final LocationsTransferRelation locTransferRelation;
  private final ConditionalDepGraphBuilder builder;
  private final ConditionalDepGraph condDepGraph;

  @Option(
      secure = true,
      description =
          "With this option enabled, we just need the first edge of the determined block to get "
              + "the core dependent-related edge")
  private boolean useDetBlockCache = false;

  private final Map<Integer, PeepholeState> detBlockGVACache;

  public static final String THREAD_START = "pthread_create";
  public static final String THREAD_JOIN = "pthread_join";
  private static final String THREAD_EXIT = "pthread_exit";
  private static final String THREAD_MUTEX_LOCK = "pthread_mutex_lock";
  private static final String THREAD_MUTEX_UNLOCK = "pthread_mutex_unlock";
  private static final String VERIFIER_ATOMIC_BEGIN = "__VERIFIER_atomic_begin";
  private static final String VERIFIER_ATOMIC_END = "__VERIFIER_atomic_end";

  private static final ImmutableSet<String> THREAD_FUNCTIONS =
      ImmutableSet.of(
          THREAD_START,
          THREAD_MUTEX_LOCK,
          THREAD_MUTEX_UNLOCK,
          THREAD_JOIN,
          THREAD_EXIT,
          VERIFIER_ATOMIC_BEGIN,
          VERIFIER_ATOMIC_END);

  public BPPORTransferRelation(Configuration pConfig, LogManager pLogger, CFA pCfa)
      throws InvalidConfigurationException {
    pConfig.inject(this);
    locTransferRelation =
        (LocationsTransferRelation)
            LocationsCPA.create(pConfig, pLogger, pCfa).getTransferRelation();
    builder = new ConditionalDepGraphBuilder(pCfa, pConfig, pLogger);
    condDepGraph = builder.build();
    detBlockGVACache = new HashMap<>();
  }

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessorsForEdge(
      AbstractState pState, Precision pPrecision, CFAEdge pCfaEdge)
      throws CPATransferException, InterruptedException {
    BPPORState curState = (BPPORState) pState;
    PeepholeState preBlockState = curState.getPreBlockState(),
        curTransState = curState.getCurTransState();

    // for some refinement-based algorithm.
    if (preBlockState == null) {
      detBlockGVACache.clear();
    }

    // compute new locations.
    Collection<? extends AbstractState> newStates =
        locTransferRelation.getAbstractSuccessorsForEdge(
            curTransState.getThreadLocs(), pPrecision, pCfaEdge);
    assert newStates.size() <= 1;

    if (newStates.isEmpty()) {
      // no successor.
      return ImmutableSet.of();
    } else {
      // get some information.
      int curBlockInitEdgeHash =
          isBlockEndState(curTransState) ? pCfaEdge.hashCode() : curState.getCurBlockInitEdgeHash();
      boolean isContainAssumeEdge =
          curState.isContainAssumeEdge() ? true : (pCfaEdge instanceof AssumeEdge);

      // generate the new transfer state.
      PeepholeState newTransState =
          genNewTransState(curTransState, (LocationsState) newStates.iterator().next(), pCfaEdge);

      if (isBlockEndState(newTransState)) {
        if (useDetBlockCache && !isContainAssumeEdge) {
          detBlockGVACache.put(curBlockInitEdgeHash, newTransState);
        }

        if (canSkip(preBlockState, newTransState)) {
          return ImmutableSet.of();
        } else {
          // if we could not skip to explore the new transfer state, then we need to pass on the
          // newTransState to preBlockState.
          preBlockState = newTransState;
          return ImmutableSet.of(
              new BPPORState(
                  preBlockState, curBlockInitEdgeHash, newTransState, isContainAssumeEdge));
        }
      } else {
        if (useDetBlockCache && detBlockGVACache.containsKey(curBlockInitEdgeHash)) {
          PeepholeState detCurBlockState = detBlockGVACache.get(curBlockInitEdgeHash);

          // if could not skip the cached deterministic block, we do nothing.
          if (canSkip(preBlockState, detCurBlockState)) {
            return ImmutableSet.of();
          }
        }

        return ImmutableSet.of(
            new BPPORState(
                preBlockState, curBlockInitEdgeHash, newTransState, isContainAssumeEdge));
      }
    }
  }

  private PeepholeState genNewTransState(
      final PeepholeState pOldTransState, final LocationsState pNewLocs, final CFAEdge pEdge) {
    int oldThreadCounter = pOldTransState.getThreadCounter();
    Map<String, Integer> oldThreadIdNumbers = pOldTransState.getThreadIdNumbers();

    Pair<Integer, Map<String, Integer>> newThreadIdInfo =
        updateThreadIdNumber(oldThreadCounter, oldThreadIdNumbers, pNewLocs);
    int newThreadCounter = newThreadIdInfo.getFirst();
    Map<String, Integer> newThreadIdNumbers = newThreadIdInfo.getSecond();

    return new PeepholeState(newThreadCounter, pEdge, pNewLocs, newThreadIdNumbers);
  }

  private Pair<Integer, Map<String, Integer>> updateThreadIdNumber(
      int pOldThreadCounter,
      final Map<String, Integer> pOldThreadIdNumbers,
      final LocationsState pNewLocs) {
    Map<String, Integer> newThreadIdNumbers = new HashMap<>(pOldThreadIdNumbers);

    // remove exited threads.
    Set<String> newThreadIds = pNewLocs.getMultiThreadState().getThreadIds();
    ImmutableSet<String> removeIds =
        from(newThreadIdNumbers.keySet()).filter(t -> !newThreadIds.contains(t)).toSet();
    removeIds.forEach(t -> newThreadIdNumbers.remove(t));

    // add new thread id.
    ImmutableSet<String> addIds =
        from(newThreadIds).filter(t -> !newThreadIdNumbers.containsKey(t)).toSet();
    assert addIds.size() <= 1;
    if (!addIds.isEmpty()) {
      newThreadIdNumbers.put(addIds.iterator().next(), ++pOldThreadCounter);
    }

    return Pair.of(pOldThreadCounter, newThreadIdNumbers);
  }

  private boolean isBlockEndState(final PeepholeState pState) {
    CFAEdge procEdge = pState.getProcEdge();
    return (condDepGraph.getBlockDGNode(procEdge.hashCode()) != null)
        || isImportantForThreading(procEdge)
        || isReturnEdge(procEdge);
  }

  public boolean canSkip(final PeepholeState pPreState, final PeepholeState pCurState) {
    if (pPreState != null) {
      CFAEdge preEdge = pPreState.getProcEdge(), curEdge = pCurState.getProcEdge();
      DGNode depPreNode = condDepGraph.getBlockDGNode(preEdge.hashCode()),
          depCurNode = condDepGraph.getBlockDGNode(curEdge.hashCode());
      int preTid = pPreState.getProcessEdgeThreadId(), curTid = pCurState.getProcessEdgeThreadId();
      boolean isPreReturnEdge = isReturnEdge(preEdge);

      if (!isPreReturnEdge
          && (curTid < preTid)
          && (condDepGraph.dep(depPreNode, depCurNode) == null)
          && !preEdge.getSuccessor().isLoopStart()) {
        return true;
      }
      return false;
    }

    // pPreState is null, we should not skip the exploration of pCurState.
    return false;
  }

  private static boolean isImportantForThreading(CFAEdge pCfaEdge) {
    switch (pCfaEdge.getEdgeType()) {
      case StatementEdge:
        {
          AStatement statement = ((AStatementEdge) pCfaEdge).getStatement();
          if (statement instanceof AFunctionCall) {
            AExpression functionNameExp =
                ((AFunctionCall) statement).getFunctionCallExpression().getFunctionNameExpression();
            if (functionNameExp instanceof AIdExpression) {
              return THREAD_FUNCTIONS.contains(((AIdExpression) functionNameExp).getName());
            }
          }
          return false;
        }
      case FunctionCallEdge:
        // @Deprecated, for old benchmark tasks
        return pCfaEdge.getSuccessor().getFunctionName().startsWith(VERIFIER_ATOMIC_BEGIN);
      case FunctionReturnEdge:
        // @Deprecated, for old benchmark tasks
        return pCfaEdge.getPredecessor().getFunctionName().startsWith(VERIFIER_ATOMIC_END);
      default:
        return false;
    }
  }

  private static boolean isReturnEdge(CFAEdge pEdge) {
    switch (pEdge.getEdgeType()) {
      case FunctionReturnEdge:
      case ReturnStatementEdge:
      case CallToReturnEdge:
        return true;
      case BlankEdge:
        if (pEdge.getDescription().contains("default return")) {
          return true;
        } else {
          return false;
        }
      default:
        return false;
    }
  }

  public Statistics getCondDepGraphBuildStatistics() {
    return builder.getCondDepGraphBuildStatistics();
  }
}
