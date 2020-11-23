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
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.ast.AExpression;
import org.sosy_lab.cpachecker.cfa.ast.AFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.AIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.AStatement;
import org.sosy_lab.cpachecker.cfa.model.AStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
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

public class BIPPORTransferRelation extends SingleEdgeTransferRelation {

  private final LocationsCPA locationsCPA;
  private final ConditionalDepGraphBuilder builder;
  private final ConditionalDepGraph condDepGraph;

  public BIPPORTransferRelation(Configuration pConfig, LogManager pLogger, CFA pCfa)
      throws InvalidConfigurationException {
    locationsCPA = LocationsCPA.create(pConfig, pLogger, pCfa);
    builder = new ConditionalDepGraphBuilder(pCfa, pConfig, pLogger);
    condDepGraph = builder.build();
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

      // note: here, we only generate the successor of pCfaEdge, the real BIPPOR routine is in the
      // precision adjust part.

      return ImmutableSet.of(
          new BIPPORState(
              curState.getPreGVAState(),
              new PeepholeState(newThreadCounter, pCfaEdge, newLocs, newThreadIdNumbers),
              determineEdgeType(pCfaEdge)));
    }
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
}
