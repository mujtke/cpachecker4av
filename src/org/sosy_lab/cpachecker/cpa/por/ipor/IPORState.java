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
package org.sosy_lab.cpachecker.cpa.por.ipor;

import static com.google.common.base.Preconditions.checkNotNull;

import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.locations.LocationsState;
import org.sosy_lab.cpachecker.cpa.por.EdgeType;

public class IPORState implements AbstractState {

  private CFAEdge transferInEdge;
  private EdgeType transferInEdgeType;
  private LocationsState threadLocs;

  public static IPORState getInitialInstance(
      CFANode pInitNode, String pMainThreadId, boolean pIsFollowFunCalls) {
    assert pInitNode.getNumLeavingEdges() == 1;

    CFAEdge initEdge = pInitNode.getLeavingEdge(0);
    LocationsState initThreadLocs =
        LocationsState.getInitialInstance(pInitNode, pMainThreadId, pIsFollowFunCalls);

    return new IPORState(initEdge, EdgeType.NEdge, initThreadLocs);
  }

  public IPORState(CFAEdge pEdge, EdgeType pEdgeType, LocationsState pLocs) {
    transferInEdge = checkNotNull(pEdge);
    transferInEdgeType = checkNotNull(pEdgeType);
    threadLocs = checkNotNull(pLocs);
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

  @Override
  public int hashCode() {
    return transferInEdge.hashCode() + threadLocs.hashCode();
  }

  @Override
  public boolean equals(Object pObj) {
    if (pObj == this) {
      return true;
    }

    if (pObj != null && pObj instanceof IPORState) {
      IPORState other = (IPORState) pObj;
      return transferInEdge.equals(other.transferInEdge) && threadLocs.equals(other.threadLocs);
    }

    return false;
  }

  @Override
  public String toString() {
    return transferInEdge + " <--> " + threadLocs;
  }
}
