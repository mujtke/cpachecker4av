// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.por.cbippor;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.locations.LocationsState;
import org.sosy_lab.cpachecker.cpa.por.EdgeType;
import org.sosy_lab.cpachecker.cpa.por.ppor.PeepholeWithComputeState;
import org.sosy_lab.cpachecker.util.Pair;

public class CBIPPORState implements AbstractState {

  private PeepholeWithComputeState preGVAState;
  private PeepholeWithComputeState curState;
  private EdgeType transferInEdgeType;
  // {<thread_id, transfer_edgehash>, ...}
  private Set<Pair<Integer, Integer>> sleepSet;
  private boolean sleepSetUpdate;

  public static CBIPPORState
      getInitialInstance(CFANode pInitNode, String pMainThreadId, boolean pIsFollowFunCalls) {
    assert pInitNode.getNumLeavingEdges() == 1;

    PeepholeWithComputeState tmpCurState =
        PeepholeWithComputeState.getInitialInstance(pInitNode, pMainThreadId, pIsFollowFunCalls);

    return new CBIPPORState(null, tmpCurState, EdgeType.NEdge, new HashSet<>(), false);
  }

  public CBIPPORState(
      PeepholeWithComputeState pPreGVAState,
      PeepholeWithComputeState pCurState,
      EdgeType pTransferInEdgeType,
      Set<Pair<Integer, Integer>> pSleep,
      boolean pSleepSetUpdate) {
    preGVAState = pPreGVAState;
    curState = checkNotNull(pCurState);
    transferInEdgeType = checkNotNull(pTransferInEdgeType);
    sleepSet = new HashSet<>(pSleep);
    sleepSetUpdate = pSleepSetUpdate;
  }

  @Override
  public int hashCode() {
    return preGVAState.hashCode() + curState.hashCode() + sleepSet.hashCode();
  }

  @Override
  public boolean equals(Object pObj) {
    return true;
  }

  public boolean equalsToOther(Object pObj) {
    if (pObj == this) {
      return true;
    }

    if (pObj != null && pObj instanceof CBIPPORState) {
      CBIPPORState other = (CBIPPORState) pObj;
      if ((preGVAState == null && other.preGVAState == null)
          || (preGVAState != null
              && other.preGVAState != null
              && preGVAState.equals(other.preGVAState))) {
        if (curState.equals(other.curState) && sleepSet.equals(other.sleepSet)) {
          return true;
        } else {
          return false;
        }
      } else {
        return false;
      }
    }

    return false;
  }

  @Override
  public String toString() {
    return "(pre: "
        + preGVAState.toString()
        + ", cur: "
        + curState.toString()
        + ", "
        + sleepSet
        + ")";
  }

  public PeepholeWithComputeState getPreGVAState() {
    return preGVAState;
  }

  public void setPreGVAState(PeepholeWithComputeState pPreGVAState) {
    preGVAState = pPreGVAState;
  }

  public PeepholeWithComputeState getCurState() {
    return curState;
  }

  public EdgeType getTransferInEdgeType() {
    return transferInEdgeType;
  }

  public int getCurrentThreadCounter() {
    return curState.getThreadCounter();
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

  public Set<Pair<Integer, Integer>> getSleepSet() {
    return sleepSet;
  }

  public boolean isSleepSetUpdate() {
    return sleepSetUpdate;
  }

  public void setSleepSetUpdate(boolean pSleepSetUpdate) {
    sleepSetUpdate = pSleepSetUpdate;
  }

  public void addThreadIntoSleep(Pair<Integer, Integer> pThreadHashPair) {
    sleepSet.add(pThreadHashPair);
  }

  public void addThreadIntoSleep(Integer pThreadId, Integer pEdgeHash) {
    sleepSet.add(Pair.of(pThreadId, pEdgeHash));
  }

  public boolean containsSleepThreadHash(Pair<Integer, Integer> pThreadHashPair) {
    return sleepSet.contains(pThreadHashPair);
  }

  public boolean containsSleepThreadHash(Integer pThreadId, Integer pEdgeHash) {
    return sleepSet.contains(Pair.of(pThreadId, pEdgeHash));
  }

  public int getThreadIdNumber(String pThreadName) {
    Map<String, Integer> threadNumbers = curState.getThreadIdNumbers();
    assert threadNumbers.containsKey(pThreadName);
    return threadNumbers.get(pThreadName);
  }

  public int getCurrentTransferInEdgeThreadId() {
    return curState.getProcessEdgeThreadId();
  }

}
