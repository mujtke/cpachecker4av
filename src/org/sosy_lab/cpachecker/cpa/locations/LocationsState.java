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
package org.sosy_lab.cpachecker.cpa.locations;

import static com.google.common.collect.FluentIterable.from;

import com.google.common.base.Splitter;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.cpachecker.cfa.ast.FileLocation;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdgeType;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.c.CLabelNode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractQueryableState;
import org.sosy_lab.cpachecker.core.interfaces.AbstractStateWithLocation;
import org.sosy_lab.cpachecker.core.interfaces.Partitionable;
import org.sosy_lab.cpachecker.exceptions.InvalidQueryException;
import org.sosy_lab.cpachecker.util.threading.MultiThreadState;
import org.sosy_lab.cpachecker.util.threading.SingleThreadState;
import org.sosy_lab.cpachecker.util.threading.ThreadInfoProvider;
import org.sosy_lab.cpachecker.util.threading.ThreadOperator;

public class LocationsState
    implements AbstractStateWithLocation, ThreadInfoProvider, AbstractQueryableState, Partitionable,
    Serializable {

  private static final long serialVersionUID = 1L;
  private MultiThreadState locations;

  /** Here, we do not provide the backward analysis state. */
  public static LocationsState getInitialInstance(
      CFANode pInitNode, String pMainThreadId, boolean pIsFollowFunCalls) {
    SingleThreadState mainLocState =
        new SingleThreadState(pInitNode, ThreadOperator.MIN_THREAD_NUM);
    Map<String, SingleThreadState> locsMap = new HashMap<>();
    locsMap.put(pMainThreadId, mainLocState);

    return new LocationsState(locsMap, pMainThreadId, pIsFollowFunCalls);
  }

  public LocationsState(
      Map<String, SingleThreadState> pLocations,
      String pTransThread,
      boolean pFollowFunctionCalls) {
    assert pLocations.containsKey(pTransThread) : "No such thread '"
        + pTransThread
        + "' in the locations node: "
        + pLocations;
    locations = new MultiThreadState(pLocations, pTransThread, pFollowFunctionCalls);
  }

  public LocationsState(final LocationsState pOther) {
    assert pOther != null;
    locations = new MultiThreadState(pOther.locations);
  }

  /**
   * Here, we only use the location of the transfered thread.
   */
  @Override
  public Iterable<CFANode> getLocationNodes() {
    return locations.getLocationNodes();
  }

  /**
   * Here, we only use the location of the transfered thread.
   */
  @Override
  public CFANode getLocationNode() {
    return locations.getLocationNode();
  }

  @Override
  public Iterable<CFAEdge> getOutgoingEdges() {
    return locations.getOutgoingEdges();
  }

  @Override
  public Iterable<CFAEdge> getIngoingEdges() {
    return locations.getIngoingEdges();
  }

  @Override
  public @Nullable Object getPartitionKey() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String getCPAName() {
    // in order to compatible with original verification process, the
    // name of this CPA will be 'location' instead of 'locations'.
    //
    // the error case of using 'locations':
    // case: pthread-divine/ring_1w1r_true-unreach-call.i
    // error output: Error: Automaton transition condition could not be evaluated: No State of CPA
    // "location" was found! (AutomatonTransferRelation.getFollowStates, SEVERE)
    // error source: org.sosy_lab.cpachecker.cpa.automaton.AutomatonBoolExpr::1213:
    // return new ResultValue<>(
    // "No State of CPA \"" + cpaName + "\" was found!", "AutomatonBoolExpr.CPAQuery");
    return "location";
  }

  @Override
  public int hashCode() {
    int stateHash = 0;
    for (SingleThreadState loc : locations.getThreadStates()) {
      stateHash += loc.getLocationNode().hashCode();
    }
    return stateHash;
  }

  public boolean isFollowFunctionCalls() {
    return locations.isFollowFunctionCalls();
  }

  public String getThreadName(CFANode pLoc) {
    return locations.getThreadName(pLoc);
  }

  public void setTransThread(String pThread) {
    locations.setTransThread(pThread);
  }

  public String getTransferThreadId() {
    return locations.getTransThread();
  }

  public CFANode getTransferedThreadNode() {
    return locations.getLocationNode();
  }

  /**
   * This function only check whether the two states are located at the same location.
   *
   * @implNote Given two locations l = [(t_1, l_1), ..., (t_n, l_n)] and l' = [(t'_1, l'_1), ...,
   *           (t'_n, l'_n)], if for \any 1 <= i <= n, we have t_i = t'_i and l_i = l'_i, then we
   *           can conclude that l = l'.
   */
  @Override
  public boolean equals(Object pObj) {
    if (pObj != null && pObj instanceof LocationsState) {
      LocationsState locOther = (LocationsState) pObj;

      if (this.locations.equals(locOther.locations)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String toString() {
    return locations.toString();
  }

  @Override
  public boolean checkProperty(String pProperty) throws InvalidQueryException {
    List<String> parts = Splitter.on("==").trimResults().splitToList(pProperty);
    if (parts.size() != 2) {
      throw new InvalidQueryException(
          "The Query \""
              + pProperty
              + "\" is invalid. Could not split the property string correctly.");
    } else {
      FluentIterable<CFANode> locs =
          from(locations.getThreadStates()).transform(ls -> ls.getLocationNode());
      switch (parts.get(0).toLowerCase()) {
        case "line":
          try {
            int queryLine = Integer.parseInt(parts.get(1));
            for (CFAEdge edge : getIngoingEdges()) {
              if (edge.getLineNumber() == queryLine) {
                return true;
              }
            }
            return false;
          } catch (NumberFormatException nfe) {
            throw new InvalidQueryException(
                "The Query \""
                    + pProperty
                    + "\" is invalid. Could not parse the integer \""
                    + parts.get(1)
                    + "\"");
          }
        case "functionname":
          return locs.filter(l -> l.getFunctionName().equals(parts.get(1))).toList().size() != 0;
        case "label":
          return locs.filter(
              l -> (l instanceof CLabelNode
                  ? ((CLabelNode) l).getLabel().equals(parts.get(1))
                  : false))
              .toList()
              .size() != 0;
        case "nodenumber":
          try {
            int queryNumber = Integer.parseInt(parts.get(1));
            return locs.filter(l -> l.getNodeNumber() == queryNumber).toList().size() != 0;
          } catch (NumberFormatException nfe) {
            throw new InvalidQueryException(
                "The Query \""
                    + pProperty
                    + "\" is invalid. Could not parse the integer \""
                    + parts.get(1)
                    + "\"");
          }
        case "mainentry":
          for (CFANode loc : locs) {
            if (loc.getNumEnteringEdges() == 1 && loc.getFunctionName().equals(parts.get(1))) {
              CFAEdge enteringEdge = loc.getEnteringEdge(0);
              if (enteringEdge.getDescription().equals("Function start dummy edge")
                  && enteringEdge.getEdgeType() == CFAEdgeType.BlankEdge
                  && FileLocation.DUMMY.equals(enteringEdge.getFileLocation())) {
                return true;
              }
            }
          }
          return false;
        default:
          throw new InvalidQueryException(
              "The Query \""
                  + pProperty
                  + "\" is invalid. \""
                  + parts.get(0)
                  + "\" is no valid keyword");
      }
    }
  }

  @Override
  public Object evaluateProperty(String pProperty) throws InvalidQueryException {
    if(pProperty.equalsIgnoreCase("lineno")) {
      ImmutableList<CFANode> locs =
          from(locations.getThreadStates()).transform(ls -> ls.getLocationNode()).toList();
      for (CFANode loc : locs) {
        if (loc.getNumEnteringEdges() > 0) {
          return loc.getEnteringEdge(0).getLineNumber();
        }
      }
      return 0; // DUMMY
    } else {
      return checkProperty(pProperty);
    }
  }


  @Override
  public MultiThreadState getMultiThreadState() {
    return locations;
  }

  @Override
  public void removeThreadId(String pThreadId) {
    locations.removeThreadId(pThreadId);
  }

}
