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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.locationss.LocationsState;
import org.sosy_lab.cpachecker.cpa.por.EdgeType;
import org.sosy_lab.cpachecker.cpa.por.ppor.PeepholeState;

public class BIPPORState implements AbstractState {

  private PeepholeState preGVAState;
  private PeepholeState curState;
  private KEPHState kephState;
  private EdgeType transferInEdgeType;

  private Set<Integer> disExpStates; // the states that are disabled to be explored.
  private boolean isSetupDisFlag; // denote whether the disable exploration set is setup.

  public static BIPPORState getInitialInstance(
      CFANode pInitNode, String pMainThreadId, boolean pIsFollowFunCalls) {
    assert pInitNode.getNumLeavingEdges() == 1;
    int initThreadCounter = 0;
    CFAEdge initEdge = pInitNode.getLeavingEdge(0);

    LocationsState initThreadLocs =
        LocationsState.getInitialInstance(pInitNode, pMainThreadId, pIsFollowFunCalls);

    Map<String, Integer> initThreadIdNumbers = new HashMap<>();
    initThreadIdNumbers.put(pMainThreadId, initThreadCounter);

    PeepholeState tmpCurState =
        new PeepholeState(initThreadCounter, initEdge, initThreadLocs, initThreadIdNumbers);
    return new BIPPORState(
        null,
        tmpCurState,
        KEPHState.getInstance(),
        EdgeType.NEdge,
        new HashSet<>(),
        false);
  }

  public BIPPORState(
      PeepholeState pPreGVAState,
      PeepholeState pCurState,
      KEPHState pKephState,
      EdgeType pEdgeType,
      Set<Integer> pDisExpStates,
      boolean pIsSetupDisFlag) {
    preGVAState = pPreGVAState;
    curState = checkNotNull(pCurState);
    kephState = checkNotNull(pKephState);
    transferInEdgeType = checkNotNull(pEdgeType);
    disExpStates = checkNotNull(pDisExpStates);
    isSetupDisFlag = pIsSetupDisFlag;
  }

  @Override
  public int hashCode() {
    return preGVAState.hashCode()
        + curState.hashCode()
        + kephState.hashCode()
        + disExpStates.hashCode();
  }

  @Override
  public boolean equals(Object pObj) {
    return true;
  }

  public boolean equalsToOther(Object pObj) {
    if (pObj == this) {
      return true;
    }

    if (pObj != null && pObj instanceof BIPPORState) {
      BIPPORState other = (BIPPORState) pObj;
      if ((preGVAState == null && other.preGVAState == null)
          || (preGVAState != null
              && other.preGVAState != null
              && preGVAState.equals(other.preGVAState))) {
        if (curState.equals(other.curState)
            && kephState.equals(other.kephState)
            && disExpStates.equals(other.disExpStates)) {
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
    return curState.toString();
  }

  public PeepholeState getPreGVAState() {
    return preGVAState;
  }

  public void setPreGVAState(PeepholeState pPreGVAState) {
    preGVAState = pPreGVAState;
  }

  public PeepholeState getCurState() {
    return curState;
  }

  public KEPHState getKephState() {
    return kephState;
  }

  public boolean isKEPHRemovable() {
    return kephState.isNeedRemove();
  }

  public void setKEPHRemovable(boolean pRemovable) {
    kephState.setNeedRemove(pRemovable);
  }

  public Set<Integer> getDisExpStates() {
    return disExpStates;
  }

  public boolean isDisabled(int pSuccessorId) {
    if (disExpStates.contains(pSuccessorId)) {
      return true;
    }
    return false;
  }

  public EdgeType getTransferInEdgeType() {
    return transferInEdgeType;
  }

  public void addDisabledState(int pStateId) {
    disExpStates.add(pStateId);
  }

  public void addDisabledStateSet(final Set<Integer> pStateIds) {
    if (pStateIds != null) {
      disExpStates.addAll(pStateIds);
    }
  }

  public boolean isSetupDisFlag() {
    return isSetupDisFlag;
  }

  public void setupDisFlag() {
    isSetupDisFlag = true;
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

  public int getThreadIdNumber(String pThreadName) {
    Map<String, Integer> threadNumbers = curState.getThreadIdNumbers();
    assert threadNumbers.containsKey(pThreadName);
    return threadNumbers.get(pThreadName);
  }

  public int getCurrentTransferInEdgeThreadId() {
    return curState.getProcessEdgeThreadId();
  }
}
