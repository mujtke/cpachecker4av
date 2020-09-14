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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Predicates.instanceOf;
import static com.google.common.base.Predicates.not;
import static com.google.common.base.Predicates.or;
import static com.google.common.collect.FluentIterable.from;
import static org.sosy_lab.cpachecker.util.CFAUtils.allEnteringEdges;
import static org.sosy_lab.cpachecker.util.CFAUtils.allLeavingEdges;
import static org.sosy_lab.cpachecker.util.CFAUtils.enteringEdges;
import static org.sosy_lab.cpachecker.util.CFAUtils.leavingEdges;

import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.sosy_lab.cpachecker.cfa.ast.FileLocation;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdgeType;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.FunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CLabelNode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractQueryableState;
import org.sosy_lab.cpachecker.core.interfaces.AbstractStateWithLocation;
import org.sosy_lab.cpachecker.core.interfaces.Partitionable;
import org.sosy_lab.cpachecker.exceptions.InvalidQueryException;

public class LocationsState
    implements AbstractStateWithLocation, AbstractQueryableState, Partitionable, Serializable {

  private final static Predicate<CFAEdge> NOT_FUNCTIONCALL =
      not(or(instanceOf(FunctionReturnEdge.class), instanceOf(FunctionCallEdge.class)));

  /** Record the location of each thread. */
  // [(thread_id, thread_location), ...]
  private transient Map<String, CFANode> locationsNode;
  /** Each thread is assigned to an integer for identification of cloned functions. */
  private transient Map<String, Integer> threadNums;
  /** Record the thread that transfered to this locations. */
  private final String transThread;
  private final boolean followFunctionCalls;

  /** Here, we do not provide the backward analysis state. */

  public LocationsState(
      Map<String, CFANode> pLocationsNode,
      Map<String, Integer> pThreadNums,
      String pTransThread,
      boolean pFollowFunctionCalls) {
    assert (pLocationsNode.containsKey(pTransThread)) : "No such thread '"
        + pTransThread
        + "' in the locations node.";

    locationsNode = pLocationsNode;
    threadNums = pThreadNums;
    transThread = pTransThread;
    followFunctionCalls = pFollowFunctionCalls;
  }

  public LocationsState(final LocationsState other) {
    assert other != null;
    locationsNode = Maps.newHashMap(other.locationsNode);
    threadNums = Maps.newHashMap(other.threadNums);
    transThread = new String(other.transThread);
    followFunctionCalls = other.followFunctionCalls;
  }

  @Override
  public Iterable<CFANode> getLocationNodes() {
    // TODO Auto-generated method stub
    assert locationsNode.containsKey(transThread);
    return Collections.singleton(locationsNode.get(transThread));
  }

  @Override
  public Iterable<CFAEdge> getOutgoingEdges() {
    List<CFAEdge> outEdges = new ArrayList<>();

    for (CFANode loc : locationsNode.values()) {
      if (followFunctionCalls) {
        FluentIterable<CFAEdge> locEdges = leavingEdges(loc);
        for (CFAEdge edge : locEdges) {
          outEdges.add(edge);
        }
      } else {
        FluentIterable<CFAEdge> locEdges = allLeavingEdges(loc).filter(NOT_FUNCTIONCALL);
        for (CFAEdge edge : locEdges) {
          outEdges.add(edge);
        }
      }
    }

    return Collections.unmodifiableCollection(outEdges);
  }

  @Override
  public Iterable<CFAEdge> getIngoingEdges() {
    List<CFAEdge> inEdges = new ArrayList<>();

    for (CFANode loc : locationsNode.values()) {
      if (followFunctionCalls) {
        FluentIterable<CFAEdge> locEdges = enteringEdges(loc);
        for (CFAEdge edge : locEdges) {
          inEdges.add(edge);
        }
      } else {
        FluentIterable<CFAEdge> locEdges = allEnteringEdges(loc).filter(NOT_FUNCTIONCALL);
        for (CFAEdge edge : locEdges) {
          inEdges.add(edge);
        }
      }
    }

    return Collections.unmodifiableCollection(inEdges);
  }

  @Override
  public Object getPartitionKey() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String getCPAName() {
    // TODO Auto-generated method stub
    return "locations";
  }

  @Override
  public CFANode getLocationNode() {
    // TODO Auto-generated method stub
    assert locationsNode.containsKey(transThread);
    return locationsNode.get(transThread);
  }

  @Override
  public String toString() {
    // TODO Auto-generated method stub
    String res = "( ";
    for (String thrd : locationsNode.keySet()) {
      res += thrd + "[" + (thrd.equals(transThread) ? "*" : "") + locationsNode.get(thrd) + "] ";
    }
    res += ")";

    return res;
  }

  @Override
  public int hashCode() {
    // TODO Auto-generated method stub
    int stateHash = 0;
    for (CFANode loc : locationsNode.values()) {
      stateHash += loc.hashCode();
    }

    return stateHash;
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
    // TODO Auto-generated method stub
    if (pObj != null && pObj instanceof LocationsState) {
      LocationsState locOther = (LocationsState) pObj;

      if (this.locationsNode.equals(locOther.locationsNode)) {
        return true;
      } else {
        return false;
      }
    }
    return false;
  }

  public String getThreadName(CFANode pLoc) {
    checkNotNull(pLoc);

    ImmutableSet<String> res =
        from(locationsNode.keySet()).filter(t -> locationsNode.get(t).equals(pLoc)).toSet();
    assert res.size() <= 1 : "multiple threads are located at the same location '"
        + pLoc
        + "': "
        + locationsNode;

    return res.size() > 0 ? res.iterator().next() : null;
  }

  public Map<String, CFANode> getLocationsNode() {
    return locationsNode;
  }

  public Map<String, Integer> getThreadNums() {
    return threadNums;
  }

  public String getTransThread() {
    return transThread;
  }

  public boolean isFollowFunctionCalls() {
    return followFunctionCalls;
  }

  public boolean isContainThread(String pThreadId) {
    if (pThreadId != null) {
      return locationsNode.containsKey(pThreadId);
    }

    return false;
  }

  public void removeThreadId(String pThreadId) {
    if (pThreadId != null) {
      locationsNode.remove(pThreadId);
      threadNums.remove(pThreadId);
    }
  }

  @Override
  public boolean checkProperty(String pProperty) throws InvalidQueryException {
    List<String> parts = Splitter.on("==").trimResults().splitToList(pProperty);
    if (parts.size() != 2) {
      throw new InvalidQueryException("The Query \"" + pProperty
          + "\" is invalid. Could not split the property string correctly.");
    } else {
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
        return from(this.locationsNode.values())
            .filter(l -> l.getFunctionName().equals(parts.get(1)))
            .toList()
            .size() != 0;
      case "label":
        return from(this.locationsNode.values()).filter(
            l -> (l instanceof CLabelNode
                ? ((CLabelNode) l).getLabel().equals(parts.get(1))
                : false))
            .toList()
            .size() != 0;
      case "nodenumber":
        try {
          int queryNumber = Integer.parseInt(parts.get(1));
          return from(this.locationsNode.values()).filter(l -> l.getNodeNumber() == queryNumber)
              .toList()
              .size() != 0;
        } catch (NumberFormatException nfe) {
          throw new InvalidQueryException(
              "The Query \""
                  + pProperty
                  + "\" is invalid. Could not parse the integer \""
                  + parts.get(1)
                  + "\"");
        }
      case "mainentry":
        for (CFANode loc : locationsNode.values()) {
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
    if (pProperty.equalsIgnoreCase("lineno")) {
      for (CFANode loc : locationsNode.values()) {
        if (loc.getNumEnteringEdges() > 0) {
          return loc.getEnteringEdge(0).getLineNumber();
        }
      }
      return 0; // DUMMY
    } else {
      return checkProperty(pProperty);
    }
  }

}
