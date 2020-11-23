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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.HashMap;
import java.util.Map;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.locationss.LocationsState;
import org.sosy_lab.cpachecker.cpa.por.ppor.PeepholeState;

public class BPPORState implements AbstractState {

  private PeepholeState preBlockState;
  // we need not to store the instance of an edge here, edge hash is enough.
  private Integer curBlockInitEdgeHash;
  private PeepholeState curTransState;
  private boolean containAssumeEdge;

  public static BPPORState getInitialInstance(
      CFANode pInitNode, String pMainThreadId, boolean pIsFollowFunCalls) {
    assert pInitNode.getNumLeavingEdges() == 1;
    CFAEdge initEdge = pInitNode.getLeavingEdge(0);
    int initThreadCounter = 0;

    LocationsState initThreadLocs =
        LocationsState.getInitialInstance(pInitNode, pMainThreadId, pIsFollowFunCalls);

    Map<String, Integer> initThreadIdNumbers = new HashMap<>();
    initThreadIdNumbers.put(pMainThreadId, initThreadCounter);

    PeepholeState initCurState =
        new PeepholeState(initThreadCounter, initEdge, initThreadLocs, initThreadIdNumbers);

    return new BPPORState(null, initEdge.hashCode(), initCurState, false);
  }

  public BPPORState(
      PeepholeState pBlockState,
      int pBlockInitEdgeHash,
      PeepholeState pTransState,
      boolean pContainAssumeEdge) {
    preBlockState = pBlockState;
    curBlockInitEdgeHash = pBlockInitEdgeHash;
    curTransState = checkNotNull(pTransState);
    containAssumeEdge = pContainAssumeEdge;
  }

  @Override
  public int hashCode() {
    return (preBlockState != null ? preBlockState.hashCode() : 0) + curTransState.hashCode();
  }

  @Override
  public boolean equals(Object pObj) {
    if (pObj == this) {
      return true;
    }

    if (pObj != null && pObj instanceof BPPORState) {
      BPPORState other = (BPPORState) pObj;
      if (curTransState.equals(other.curTransState)) {
        if (preBlockState == null && other.preBlockState == null) {
          return true;
        } else if ((preBlockState != null && other.preBlockState != null)
            && preBlockState.equals(other.preBlockState)) {
          return true;
        } else {
          return false;
        }
      }
      return false;
    }

    return false;
  }

  @Override
  public String toString() {
    return "blockState: " + preBlockState + "\tcurState: " + curTransState;
  }

  public PeepholeState getPreBlockState() {
    return preBlockState;
  }

  public Integer getCurBlockInitEdgeHash() {
    return curBlockInitEdgeHash;
  }

  public PeepholeState getCurTransState() {
    return curTransState;
  }

  public boolean isContainAssumeEdge() {
    return containAssumeEdge;
  }

  public int getPreBlockStateThreadId() {
    return preBlockState.getProcessEdgeThreadId();
  }

  public CFAEdge getPreBlockStateEdge() {
    return preBlockState.getProcEdge();
  }

  public int getPreBlockStateThreadIdNumber(String pThreadName) {
    return preBlockState.getThreadIdNumber(pThreadName);
  }

  public int getCurTransStateThreadId() {
    return curTransState.getProcessEdgeThreadId();
  }

  public CFAEdge getCurTransStateEdge() {
    return curTransState.getProcEdge();
  }

  public int getCurTransStateThreadIdNumber(String pThreadName) {
    return curTransState.getThreadIdNumber(pThreadName);
  }

}
