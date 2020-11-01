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
package org.sosy_lab.cpachecker.cpa.por.ippor;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.locationss.LocationsState;
import org.sosy_lab.cpachecker.util.threading.MultiThreadState;
import org.sosy_lab.cpachecker.util.threading.SingleThreadState;
import org.sosy_lab.cpachecker.util.threading.ThreadOperator;

public class IPPORState implements AbstractState {

  public enum EdgeType {
    NEdge, // normal edge (not assume edge).
    NAEdge, // normal assume edge.
    GVAEdge // global variable access edge.
  }

  private int threadCounter;
  private CFAEdge transferInEdge;
  private EdgeType transferInEdgeType;
  private LocationsState threadLocs;
  private Map<String, Integer> threadIdNumbers;
  private boolean pporPoint;

  public static IPPORState getInitialInstance(
      CFANode pInitNode, String pMainThreadId, boolean pIsFollowFunCalls) {
    assert pInitNode.getNumLeavingEdges() == 1;
    int initThreadCounter = 0;
    CFAEdge initEdge = pInitNode.getLeavingEdge(0);

    SingleThreadState mainLocState =
        new SingleThreadState(pInitNode, ThreadOperator.MIN_THREAD_NUM);
    Map<String, SingleThreadState> locsMap = new HashMap<>();
    locsMap.put(pMainThreadId, mainLocState);
    LocationsState initThreadLocs = new LocationsState(locsMap, pMainThreadId, pIsFollowFunCalls);

    Map<String, Integer> initThreadIdNumbers = new HashMap<>();
    initThreadIdNumbers.put(pMainThreadId, initThreadCounter);

    return new IPPORState(
        initThreadCounter, initEdge, EdgeType.NEdge, initThreadLocs, initThreadIdNumbers, false);
  }

  public IPPORState(
      int pThreadCounter,
      CFAEdge pEdge,
      EdgeType pEdgeType,
      LocationsState pLocs,
      Map<String, Integer> pThrdIdNumbers,
      boolean pPPORPoint) {
    assert pThreadCounter >= 0;

    threadCounter = pThreadCounter;
    transferInEdge = checkNotNull(pEdge);
    transferInEdgeType = checkNotNull(pEdgeType);
    threadLocs = checkNotNull(pLocs);
    threadIdNumbers = checkNotNull(pThrdIdNumbers);
    pporPoint = pPPORPoint;
  }

  @Override
  public int hashCode() {
    return transferInEdge.hashCode() + threadLocs.hashCode() + threadIdNumbers.hashCode();
  }

  @Override
  public boolean equals(Object pObj) {
    if (pObj == this) {
      return true;
    }

    if (pObj != null && pObj instanceof IPPORState) {
      IPPORState other = (IPPORState) pObj;
      return transferInEdge.equals(other.transferInEdge)
          && threadLocs.equals(other.threadLocs)
          && threadIdNumbers.equals(other.threadIdNumbers)
          && (pporPoint == other.pporPoint);
    }

    return false;
  }

  @Override
  public String toString() {
    MultiThreadState threadStates = threadLocs.getMultiThreadState();
    Set<String> threads = threadStates.getThreadIds();
    String result = "( ";

    for (String thread : threads) {
      CFANode threadLoc = threadStates.getThreadLocation(thread);
      assert threadLoc != null && threadIdNumbers.containsKey(thread);
      int threadIdNumber = threadIdNumbers.get(thread);

      result += thread + "::" + threadIdNumber + "::" + threadLoc + " ";
    }
    result += " <--> [" + pporPoint + "])";

    return result;
  }

  public int getThreadCounter() {
    return threadCounter;
  }

  public CFAEdge getTransferInEdge() {
    return transferInEdge;
  }

  public EdgeType getTransferInEdgeType() {
    return transferInEdgeType;
  }

  public LocationsState getThreadLocs() {
    return threadLocs;
  }

  public Map<String, Integer> getThreadIdNumbers() {
    return threadIdNumbers;
  }

  public boolean isPporPoint() {
    return pporPoint;
  }

  public void setPporPoint(boolean pPporPoint) {
    pporPoint = pPporPoint;
  }

  public int getThreadIdNumber(String pThreadName) {
    assert threadIdNumbers.containsKey(pThreadName);
    return threadIdNumbers.get(pThreadName);
  }

  public int getTransferInEdgeThreadId() {
    CFANode inEdgeSuc = transferInEdge.getSuccessor();
    String thread = threadLocs.getThreadName(inEdgeSuc);
    thread = (thread == null) ? threadLocs.getThreadName(transferInEdge.getPredecessor()) : thread;

    assert thread != null && threadIdNumbers.containsKey(thread);
    return threadIdNumbers.get(thread);
  }
}
