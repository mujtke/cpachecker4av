// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.por.pcdpor;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.locations.LocationsState;
import org.sosy_lab.cpachecker.cpa.por.EdgeType;
import org.sosy_lab.cpachecker.cpa.por.ppor.PeepholeState;
import org.sosy_lab.cpachecker.util.Pair;

public class PCDPORState implements AbstractState {

  private PeepholeState curState;
  private EdgeType transferInEdgeType;
  // {<thread_id, transfer_edgehash>, ...}
  private Set<Pair<Integer, Integer>> sleepSet;
  private boolean isUpdated;

  public static PCDPORState
      getInitialInstance(CFANode pInitNode, String pMainThreadId, boolean pIsFollowFunCalls) {
    assert pInitNode.getNumLeavingEdges() == 1;

    PeepholeState tmpCurState =
        PeepholeState.getInitialInstance(pInitNode, pMainThreadId, pIsFollowFunCalls);

    return new PCDPORState(tmpCurState, EdgeType.NEdge, new HashSet<>(), false);
  }

  public PCDPORState(
      PeepholeState pCurState,
      EdgeType pTransferInEdgeType,
      Set<Pair<Integer, Integer>> pSleepSet,
      boolean pIsUpdated) {
    curState = pCurState;
    transferInEdgeType = pTransferInEdgeType;
    sleepSet = pSleepSet;
    isUpdated = pIsUpdated;
  }

  @Override
  public int hashCode() {
    return curState.hashCode() + transferInEdgeType.hashCode() + sleepSet.hashCode();
  }

  @Override
  public boolean equals(Object pObj) {
    return true;
  }

  public boolean equalsToOther(Object pObj) {
    if (pObj == this) {
      return true;
    }

    if (pObj != null && pObj instanceof PCDPORState) {
      PCDPORState other = (PCDPORState) pObj;
      if ((curState == null && other.curState == null)
          || (curState != null && other.curState != null && curState.equals(other.curState))) {
        if (transferInEdgeType.equals(other.transferInEdgeType)
            && sleepSet.equals(other.sleepSet)) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public String toString() {
    return "(cur: "
        + curState.toString()
        + ", inEdgeType: "
        + transferInEdgeType.toString()
        + ", sleepSet: "
        + sleepSet
        + ")";
  }

  public boolean isUpdated() {
    return isUpdated;
  }

  public void setAsUpdated() {
    isUpdated = true;
  }

  public PeepholeState getCurState() {
    return curState;
  }

  public EdgeType getTransferInEdgeType() {
    return transferInEdgeType;
  }

  public boolean isInSleepSet(Pair<Integer, Integer> pTransEdgeInfo) {
    return sleepSet.contains(pTransEdgeInfo);
  }

  public Set<Pair<Integer, Integer>> getSleepSet() {
    return sleepSet;
  }

  public void setSleepSet(Set<Pair<Integer, Integer>> pSleepSet) {
    sleepSet = pSleepSet;
  }

  public void addThreadInfoSleep(Pair<Integer, Integer> pThreadHashPair) {
    sleepSet.add(pThreadHashPair);
  }

  public void removeFromSleepSet(Pair<Integer, Integer> pInEdgeInfo) {
    sleepSet.remove(pInEdgeInfo);
  }

  public CFAEdge getCurrentTransferInEdge() {
    return curState.getProcEdge();
  }

  public LocationsState getCurrentThreadLocs() {
    return curState.getThreadLocs();
  }

  public Map<String, Integer> getCurrentThreadNumbers() {
    return curState.getThreadIdNumbers();
  }

  public int getCurrentTransferInEdgeThreadId() {
    return curState.getProcessEdgeThreadId();
  }

  public int getCurrentThreadCounter() {
    return curState.getThreadCounter();
  }

}
