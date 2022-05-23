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
package org.sosy_lab.cpachecker.cpa.por.ppor;

import java.util.Map;
import java.util.Set;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.locations.LocationsState;
import org.sosy_lab.cpachecker.util.threading.MultiThreadState;

public class PPORState implements AbstractState {

  private PeepholeState stateInstance;

  public static PPORState getInitialInstance(
      CFANode pInitNode, String pMainThreadId, boolean pIsFollowFunCalls) {
    return new PPORState(
        PeepholeState.getInitialInstance(pInitNode, pMainThreadId, pIsFollowFunCalls));
  }

  public PPORState(PeepholeState pPState) {
    stateInstance = pPState;
  }

  public PPORState(
      int pThreadCounter,
      CFAEdge pEdge,
      LocationsState pLocs,
      Map<String, Integer> pThrdIdNumbers) {
    stateInstance = new PeepholeState(pThreadCounter, pEdge, pLocs, pThrdIdNumbers);
  }

  @Override
  public int hashCode() {
    return stateInstance.getProcEdge().hashCode()
        + stateInstance.getThreadLocs().hashCode()
        + stateInstance.getThreadIdNumbers().hashCode();
  }

  @Override
  public boolean equals(Object pObj) {
    if (pObj == this) {
      return true;
    }

    if (pObj != null && pObj instanceof PPORState) {
      PPORState other = (PPORState) pObj;
      return this.getTransferInEdge().equals(other.getTransferInEdge())
          && this.getThreadLocs().equals(other.getThreadLocs())
          && this.getThreadIdNumbers().equals(other.getThreadIdNumbers());
    }

    return false;
  }

  @Override
  public String toString() {
    LocationsState threadLocs = this.getThreadLocs();
    Map<String, Integer> threadIdNumbers = this.getThreadIdNumbers();

    MultiThreadState threadStates = threadLocs.getMultiThreadState();
    Set<String> threads = threadStates.getThreadIds();
    String result = "( ";

    for (String thread : threads) {
      CFANode threadLoc = threadStates.getThreadLocation(thread);
      assert threadLoc != null && threadIdNumbers.containsKey(thread);
      int threadIdNumber = threadIdNumbers.get(thread);

      result += thread + "::" + threadIdNumber + "::" + threadLoc + " ";
    }
    result += ")";

    return result;
  }

  public int getThreadCounter() {
    return stateInstance.getThreadCounter();
  }

  public CFAEdge getTransferInEdge() {
    return stateInstance.getProcEdge();
  }

  public LocationsState getThreadLocs() {
    return stateInstance.getThreadLocs();
  }

  public Map<String, Integer> getThreadIdNumbers() {
    return stateInstance.getThreadIdNumbers();
  }

  public int getThreadIdNumber(String pThrdName) {
    Map<String, Integer> threadIdNumbers = this.getThreadIdNumbers();
    assert threadIdNumbers.containsKey(pThrdName);
    return threadIdNumbers.get(pThrdName);
  }

  public int getTransferInEdgeThreadId() {
    CFAEdge transferInEdge = this.getTransferInEdge();
    LocationsState threadLocs = this.getThreadLocs();
    Map<String, Integer> threadIdNumbers = this.getThreadIdNumbers();

    CFANode inEdgeSuc = transferInEdge.getSuccessor();
    String thread = threadLocs.getThreadName(inEdgeSuc);
    thread = (thread == null) ? threadLocs.getThreadName(transferInEdge.getPredecessor()) : thread;

    assert thread != null && threadIdNumbers.containsKey(thread);
    return threadIdNumbers.get(thread);
  }
}
