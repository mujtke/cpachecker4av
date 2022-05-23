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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.locations.LocationsState;
import org.sosy_lab.cpachecker.util.Triple;

public class MPORState implements AbstractState {

  private int threadCounter;
  private LocationsState threadsLoc;
  private ThreadsDynamicInfo threadsDynamicInfo;
  private Table<Integer, Integer, Integer> threadsDepChain;

  public static MPORState getInitialInstance(
      CFANode pInitNode, String pMainThreadId, boolean pIsFollowFunCalls) {
    assert pInitNode.getNumLeavingEdges() == 1;
    int initThreadCounter = 0;
    CFAEdge initInEdge = pInitNode.getLeavingEdge(0);

    // create initial location state.
    LocationsState initThreadLocs =
        LocationsState.getInitialInstance(pInitNode, pMainThreadId, pIsFollowFunCalls);
    // create initial threads dynamic information.
    ThreadsDynamicInfo initThreadsDynamicInfo = ThreadsDynamicInfo.empty();
    initThreadsDynamicInfo.addThreadDynamicInfo(
        Triple.of(pMainThreadId, initThreadCounter, initInEdge));
    // create initial threads dependence chain.
    HashBasedTable<Integer, Integer, Integer> initThreadsDepChain = HashBasedTable.create();
    initThreadsDepChain.put(initThreadCounter, initThreadCounter, 0);

    return new MPORState(
        initThreadCounter, initThreadLocs, initThreadsDynamicInfo, initThreadsDepChain);
  }

  public MPORState(int pThreadCounter, LocationsState pLocs, ThreadsDynamicInfo pInfo, Table<Integer, Integer, Integer> pDC) {
    threadCounter = pThreadCounter;
    threadsLoc = checkNotNull(pLocs);
    threadsDynamicInfo = checkNotNull(pInfo);
    threadsDepChain = checkNotNull(pDC);
  }

  public int getThreadCounter() {
    return threadCounter;
  }

  public LocationsState getThreadsLoc() {
    return threadsLoc;
  }

  public ThreadsDynamicInfo getThreadsDynamicInfo() {
    return threadsDynamicInfo;
  }

  public Table<Integer, Integer, Integer> getThreadsDepChain() {
    return threadsDepChain;
  }

  @Override
  public int hashCode() {
    return threadsLoc.hashCode();
  }

  @Override
  public boolean equals(Object pObj) {
    if (pObj == this) {
      return true;
    }

    // here, we only need to compare the thread locations.
    if (pObj != null && pObj instanceof MPORState) {
      MPORState other = (MPORState) pObj;
      return threadsLoc.equals(other.threadsLoc);
    }

    return false;
  }
}
