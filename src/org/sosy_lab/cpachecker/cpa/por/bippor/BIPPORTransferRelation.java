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

import static com.google.common.collect.FluentIterable.from;

import com.google.common.collect.ImmutableSet;
import de.uni_freiburg.informatik.ultimate.smtinterpol.util.ArrayQueue;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
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
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.core.defaults.SingleEdgeTransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.cpa.locationss.LocationsCPA;
import org.sosy_lab.cpachecker.cpa.locationss.LocationsState;
import org.sosy_lab.cpachecker.cpa.por.EdgeType;
import org.sosy_lab.cpachecker.cpa.por.ppor.PeepholeState;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.dependence.conditional.ConditionalDepGraph;
import org.sosy_lab.cpachecker.util.dependence.conditional.ConditionalDepGraphBuilder;

@Options(prefix = "cpa.por.bippor")
public class BIPPORTransferRelation extends SingleEdgeTransferRelation {

  private final LocationsCPA locationsCPA;
  private final ConditionalDepGraphBuilder builder;
  private final ConditionalDepGraph condDepGraph;

  // For Optimization: KEPHRemove
  @Option(
    secure = true,
    description = "With this option enabled, the optimization strategy for removing repeated "
        + "key-event path order is used.")
  private boolean useOptKEPHRemove = true;

  // Key Event: an event is called a key event if it need to access variables.
  private Set<Integer> keyEventCache = new HashSet<>();
  // explored key event paths. (shared with BIPPORPrecisionAdjustment)
  private Set<Integer> expdKEPCache = new HashSet<>();

  public BIPPORTransferRelation(
      Configuration pConfig,
      LogManager pLogger,
      CFA pCfa)
      throws InvalidConfigurationException {
    pConfig.inject(this);
    locationsCPA = LocationsCPA.create(pConfig, pLogger, pCfa);

    builder = new ConditionalDepGraphBuilder(pCfa, pConfig, pLogger);
    condDepGraph = builder.build();

    keyEventCache = useOptKEPHRemove ? extractKeyEvents(pLogger, pCfa) : keyEventCache;
  }

  private Set<Integer> extractKeyEvents(LogManager pLogger, CFA pCfa) {
    EdgeEvaluationExtractor extractor = new EdgeEvaluationExtractor(pLogger);
    Set<Integer> result = new HashSet<>();

    // get entry point of all the functions in the given program.
    Iterator<FunctionEntryNode> funcIter = pCfa.getAllFunctionHeads().iterator();
    Set<Integer> finishedEdges = new HashSet<>();
    // iteratively process each function.
    while (funcIter.hasNext()) {
      FunctionEntryNode func = funcIter.next();
      Queue<CFAEdge> edgeQueue = new ArrayQueue<>();

      // bfs strategy.
      edgeQueue.add(func.getLeavingEdge(0));
      while (!edgeQueue.isEmpty()) {
        CFAEdge edge = edgeQueue.remove();
        Integer edgeHash = edge.hashCode();

        // this edge is not processed.
        if (!finishedEdges.contains(edgeHash)) {
          // get the evaluation information.
          // if no variable is evaluated, then it's not a key event.
          if (!extractor.extractEdgeEvaluationInfo(edge).isEmpty()) {
            result.add(edgeHash);
          }
          finishedEdges.add(edgeHash);

          // System.out.println(edge + "\t" + result.get(edgeHash));

          // process its successor edge.
          CFANode edgeSucNode = edge.getSuccessor();
          for (int i = 0; i < edgeSucNode.getNumLeavingEdges(); ++i) {
            edgeQueue.add(edgeSucNode.getLeavingEdge(i));
          }
        }
      }
    }

    return result;
  }

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessorsForEdge(
      AbstractState pState, Precision pPrecision, CFAEdge pCfaEdge)
      throws CPATransferException, InterruptedException {
    BIPPORState curState = (BIPPORState) pState;

    // compute new locations.
    Collection<? extends AbstractState> newStates =
        locationsCPA
            .getTransferRelation()
            .getAbstractSuccessorsForEdge(curState.getCurrentThreadLocs(), pPrecision, pCfaEdge);
    assert newStates.size() <= 1;

    if (newStates.isEmpty()) {
      // no successor.
      return ImmutableSet.of();
    } else {
      // we need to obtain some information to determine whether it is necessary to explore some
      // branches if PPOR could be applied at curState.

      LocationsState newLocs = (LocationsState) newStates.iterator().next();
      Map<String, Integer> oldThreadIdNumbers = curState.getCurrentThreadNumbers();
      // update the map of thread id number.
      Pair<Integer, Map<String, Integer>> newThreadIdInfo =
          updateThreadIdNumber(curState.getCurrentThreadCounter(), oldThreadIdNumbers, newLocs);
      int newThreadCounter = newThreadIdInfo.getFirst();
      Map<String, Integer> newThreadIdNumbers = newThreadIdInfo.getSecond();

      // update the KEPH state.
      KEPHState newKephState =
          useOptKEPHRemove
              ? updateKephState(curState.getKephState(), pCfaEdge)
              : KEPHState.getInstance();

      // note: here, we only generate the successor of pCfaEdge, the real BIPPOR routine is in the
      // precision adjust part.

      return ImmutableSet.of(
          new BIPPORState(
              curState.getPreGVAState(),
              new PeepholeState(newThreadCounter, pCfaEdge, newLocs, newThreadIdNumbers),
              newKephState,
              determineEdgeType(pCfaEdge),
              new HashSet<>(),
              false));
    }
  }

  private KEPHState updateKephState(final KEPHState pOldKephState, final CFAEdge pEdge) {
    KEPHState newKephState = new KEPHState(pOldKephState);
    int oldKeyEventPathHash = newKephState.getKeyEventPathHash();

    // current action is not a key event, we only inherit the key event path hash from the old one.
    if (!keyEventCache.contains(pEdge.hashCode())) {
      newKephState.setNeedRemove(false);
    } else {
      int newKeyEventPathHash =
          (String.valueOf(oldKeyEventPathHash).toString()
              + pEdge.hashCode()
              + ""
              + pEdge.toString().hashCode()).hashCode();

      if (expdKEPCache.contains(newKeyEventPathHash)) {
        // old key-event path, it should be removed.
        newKephState.setNeedRemove(true);
      } else {
        // new key-event path, we don't put it into the cache until we really explored this path.
        newKephState.setNeedRemove(false);
        newKephState.setKeyEventPathHash(newKeyEventPathHash);
      }
    }
    return newKephState;
  }

  private Pair<Integer, Map<String, Integer>> updateThreadIdNumber(
      int pOldThreadCounter,
      final Map<String, Integer> pOldThreadIdNumbers,
      final LocationsState pNewLocs) {
    Map<String, Integer> newThreadIdNumbers = new HashMap<>(pOldThreadIdNumbers);

    // remove exited threads.
    Set<String> newThreadIds = pNewLocs.getMultiThreadState().getThreadIds();
    ImmutableSet<String> removeTids =
        from(newThreadIdNumbers.keySet()).filter(t -> !newThreadIds.contains(t)).toSet();
    removeTids.forEach(t -> newThreadIdNumbers.remove(t));

    // add new thread id.
    ImmutableSet<String> addTids =
        from(newThreadIds).filter(t -> !newThreadIdNumbers.containsKey(t)).toSet();
    assert addTids.size() <= 1;
    if (!addTids.isEmpty()) {
      newThreadIdNumbers.put(addTids.iterator().next(), ++pOldThreadCounter);
    }

    return Pair.of(pOldThreadCounter, newThreadIdNumbers);
  }

  public EdgeType determineEdgeType(final CFAEdge pEdge) {
    assert pEdge != null;

    if (condDepGraph.contains(pEdge.hashCode()) || isThreadCreationEdge(pEdge)) {
      return EdgeType.GVAEdge;
    } else if (pEdge instanceof CAssumeEdge) {
      return EdgeType.NAEdge;
    } else {
      return EdgeType.NEdge;
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

  public Statistics getCondDepGraphBuildStatistics() {
    return builder.getCondDepGraphBuildStatistics();
  }

  public ConditionalDepGraph getCondDepGraph() {
    return condDepGraph;
  }

  public boolean isUseOptKEPHRemove() {
    return useOptKEPHRemove;
  }

  public Set<Integer> getExpdKEPCache() {
    return expdKEPCache;
  }

}
