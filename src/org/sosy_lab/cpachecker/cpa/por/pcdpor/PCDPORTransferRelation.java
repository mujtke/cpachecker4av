// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.por.pcdpor;

import static com.google.common.collect.FluentIterable.from;

import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
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
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.core.defaults.SingleEdgeTransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.cpa.locations.LocationsCPA;
import org.sosy_lab.cpachecker.cpa.locations.LocationsState;
import org.sosy_lab.cpachecker.cpa.por.EdgeType;
import org.sosy_lab.cpachecker.cpa.por.ppor.PeepholeState;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.dependence.conditional.ConditionalDepGraph;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;

@Options(prefix = "cpa.por.pcdpor")
public class PCDPORTransferRelation extends SingleEdgeTransferRelation {

  @Option(
    secure = true,
    description = "With this option enabled, thread creation edge will be regarded as normal edge, which may reduce redundant interleavings.")
  private boolean regardThreadCreationAsNormalEdge = false;

  private final LocationsCPA locationsCPA;
  private final ConditionalDepGraph condDepGraph;

  public PCDPORTransferRelation(Configuration pConfig, LogManager pLogger, CFA pCfa)
      throws InvalidConfigurationException {
    pConfig.inject(this);
    locationsCPA = LocationsCPA.create(pConfig, pLogger, pCfa);
    condDepGraph = GlobalInfo.getInstance().getEdgeInfo().getCondDepGraph();

  }

  @Override
  public Collection<? extends AbstractState>
      getAbstractSuccessorsForEdge(AbstractState pState, Precision pPrecision, CFAEdge pCfaEdge)
          throws CPATransferException, InterruptedException {
    PCDPORState curState = (PCDPORState) pState;

    // compute new locations.
    Collection<? extends AbstractState> newStates =
        locationsCPA.getTransferRelation()
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
          new PCDPORState(
              new PeepholeState(
                  newThreadCounter,
                  pCfaEdge,
                  newLocs,
                  newThreadIdNumbers),
              determineEdgeType(pCfaEdge),
              new HashSet<>(),
              false));
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

    if (isThreadCreationEdge(pEdge)) {
      if (regardThreadCreationAsNormalEdge) {
        return EdgeType.NEdge;
      } else {
        return EdgeType.GVAEdge;
      }
    }

    if (condDepGraph.contains(pEdge.hashCode())) {
      return EdgeType.GVAEdge;
    } else if (pEdge instanceof CAssumeEdge) {
      return EdgeType.NAEdge;
    } else {
      return EdgeType.NEdge;
    }
  }

  public boolean isThreadCreationEdge(final CFAEdge pEdge) {
    switch (pEdge.getEdgeType()) {
      case StatementEdge: {
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

}
