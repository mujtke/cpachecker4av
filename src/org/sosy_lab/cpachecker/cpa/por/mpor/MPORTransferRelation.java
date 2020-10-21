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

import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
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
import org.sosy_lab.cpachecker.cpa.locationss.LocationsCPA;
import org.sosy_lab.cpachecker.cpa.locationss.LocationsState;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.dependence.DGNode;
import org.sosy_lab.cpachecker.util.dependence.conditional.ConditionalDepGraph;
import org.sosy_lab.cpachecker.util.dependence.conditional.ConditionalDepGraphBuilder;

public class MPORTransferRelation extends SingleEdgeTransferRelation {

  private final LocationsCPA locationsCPA;
  private final ConditionalDepGraphBuilder builder;
  private final ConditionalDepGraph condDepGraph;

  public MPORTransferRelation(Configuration pConfig, LogManager pLogger, CFA pCfa)
      throws InvalidConfigurationException {
    locationsCPA = LocationsCPA.create(pConfig, pLogger, pCfa);
    builder = new ConditionalDepGraphBuilder(pCfa, pConfig, pLogger);
    condDepGraph = builder.build();
  }

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessorsForEdge(
      AbstractState pState, Precision pPrecision, CFAEdge pCfaEdge)
      throws CPATransferException, InterruptedException {
    MPORState curState = (MPORState) pState;

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

      Map<String, Integer> oldThreadIdNumbers = curState.getThreadIdNumbers();
      if (!oldThreadIdNumbers.containsKey(transThreadId)) {
        // new thread is created.
        int newThreadIdNumber = curState.getThreadCounter() + 1;
        CFAEdge newTransInEdge = pCfaEdge;

        if (canSkip(oldThreadIdNumber, oldTransferEdge, newThreadIdNumber, newTransInEdge)) {
          return ImmutableSet.of();
        }

        // update the map of thread id number.
        Map<String, Integer> newThreadIdNumbers = new HashMap<>(oldThreadIdNumbers);
        newThreadIdNumbers.put(transThreadId, newThreadIdNumber);

        return ImmutableSet.of(
            new MPORState(
                curState.getThreadCounter() + 1, newTransInEdge, newLocs, newThreadIdNumbers));
      } else {
        int newThreadIdNumber = oldThreadIdNumbers.get(transThreadId);
        CFAEdge newTransInEdge = pCfaEdge;

        if (canSkip(oldThreadIdNumber, oldTransferEdge, newThreadIdNumber, newTransInEdge)) {
          return ImmutableSet.of();
        }

        return ImmutableSet.of(
            new MPORState(
                curState.getThreadCounter(), newTransInEdge, newLocs, oldThreadIdNumbers));
      }
    }
  }

  public boolean canSkip(int pPreTid, CFAEdge pPreEdge, int pSucTid, CFAEdge pSucEdge) {
    DGNode depPreNode = condDepGraph.getDGNode(pPreEdge), depSucNode = condDepGraph.getDGNode(pSucEdge);

    if ((pSucTid < pPreTid)
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
