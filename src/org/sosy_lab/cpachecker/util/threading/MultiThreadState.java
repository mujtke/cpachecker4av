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
package org.sosy_lab.cpachecker.util.threading;


import static com.google.common.base.Predicates.instanceOf;
import static com.google.common.base.Predicates.not;
import static com.google.common.base.Predicates.or;
import static com.google.common.collect.FluentIterable.from;
import static org.sosy_lab.cpachecker.util.CFAUtils.allEnteringEdges;
import static org.sosy_lab.cpachecker.util.CFAUtils.allLeavingEdges;
import static org.sosy_lab.cpachecker.util.CFAUtils.enteringEdges;
import static org.sosy_lab.cpachecker.util.CFAUtils.leavingEdges;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.FunctionReturnEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractStateWithLocation;

/**
 * This class provides the informations that ThreadOperator acquired.
 */
public class MultiThreadState implements AbstractStateWithLocation {

  private final static Predicate<CFAEdge> NOT_FUNCTIONCALL =
      not(or(instanceOf(FunctionReturnEdge.class), instanceOf(FunctionCallEdge.class)));

  // String :: the thread identifier.
  // SingleThreadState (LocationState + FuncNumber) :: the thread state.
  private final Map<String, SingleThreadState> locations;
  // Record the thread that transfered to this locations.
  private String transThread;
  private final boolean followFunctionCalls;


  public MultiThreadState(
      Map<String, SingleThreadState> pLocations,
      String pTransThread,
      boolean pFollowFunctionCalls) {
    assert pLocations.containsKey(pTransThread) : "No such thread '"
        + pTransThread
        + "' in the locations node: "
        + pLocations;

    locations = pLocations;
    transThread = pTransThread;
    followFunctionCalls = pFollowFunctionCalls;
  }

  public MultiThreadState(final MultiThreadState pOther) {
    assert pOther != null;

    locations = Maps.newHashMap(pOther.locations);
    transThread = pOther.transThread;
    followFunctionCalls = pOther.followFunctionCalls;
  }

  @Override
  public Iterable<CFANode> getLocationNodes() {
    assert locations.containsKey(transThread);
    return Collections.singleton(locations.get(transThread).getLocationNode());
  }

  @Override
  public Iterable<CFAEdge> getOutgoingEdges() {
    List<CFAEdge> outEdges = new ArrayList<>();

    for (SingleThreadState locState : locations.values()) {
      CFANode loc = locState.getLocationNode();
      if (followFunctionCalls) {
        FluentIterable<CFAEdge> locEdges = leavingEdges(loc);
        outEdges.addAll(locEdges.toList());
      } else {
        FluentIterable<CFAEdge> locEdges = allLeavingEdges(loc).filter(NOT_FUNCTIONCALL);
        outEdges.addAll(locEdges.toList());
      }
    }

    return Collections.unmodifiableCollection(outEdges);
  }

  @Override
  public Iterable<CFAEdge> getIngoingEdges() {
    List<CFAEdge> inEdges = new ArrayList<>();

    for (SingleThreadState locState : locations.values()) {
      CFANode loc = locState.getLocationNode();
      if (followFunctionCalls) {
        FluentIterable<CFAEdge> locEdges = enteringEdges(loc);
        inEdges.addAll(locEdges.toList());
      } else {
        FluentIterable<CFAEdge> locEdges = allEnteringEdges(loc).filter(NOT_FUNCTIONCALL);
        inEdges.addAll(locEdges.toList());
      }
    }

    return Collections.unmodifiableCollection(inEdges);
  }

  @Override
  public CFANode getLocationNode() {
    assert locations.containsKey(transThread);
    return locations.get(transThread).getLocationNode();
  }

  public Map<String, SingleThreadState> getThreadLocations() {
    return locations;
  }

  public Collection<SingleThreadState> getThreadStates() {
    return locations.values();
  }

  public Set<String> getThreadIds() {
    return locations.keySet();
  }

  public boolean isFollowFunctionCalls() {
    return followFunctionCalls;
  }

  public String getThreadName(CFANode pLoc) {
    assert pLoc != null;

    ImmutableSet<String> threads =
        from(locations.keySet()).filter(t -> locations.get(t).getLocationNode().equals(pLoc))
            .toSet();
    Preconditions.checkState(
        threads.size() <= 1,
        "multiple threads are located at the same location '" + pLoc + "': " + locations);

    return threads.size() > 0 ? threads.iterator().next() : null;
  }

  public CFANode getThreadLocation(String pThrdName) {
    if (locations.containsKey(pThrdName)) {
      return locations.get(pThrdName).getLocationNode();
    }

    // not contain the given thread.
    return null;
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
    if (pObj != null && pObj instanceof MultiThreadState) {
      MultiThreadState locOther = (MultiThreadState) pObj;

      if (this.locations.equals(locOther.locations)) {
        return true;
      } else {
        return false;
      }
    }
    return false;
  }

  @Override
  public int hashCode() {
    int stateHash = 0;
    for (SingleThreadState loc : locations.values()) {
      stateHash += loc.getLocationNode().hashCode();
    }
    return stateHash;
  }

  @Override
  public String toString() {
    String res = "( ";
    for (String thread : locations.keySet()) {
      res += thread + "[" + (thread.equals(transThread) ? "*" : "") + locations.get(thread) + "] ";
    }
    res += ")";

    return res;
  }

  public String getTransThread() {
    return transThread;
  }

  public void setTransThread(String pThread) {
    assert locations.containsKey(pThread);
    transThread = pThread;
  }

  public void removeThreadId(String pThreadId) {
    if (pThreadId != null) {
      locations.remove(pThreadId);
    }
  }

  public void addNewThreadInfo(String pThreadId, CFANode pLoc, int pNum) {
    Preconditions.checkState(
        (locations != null && !locations.containsKey(pThreadId)),
        "the given thread-id '"
            + pThreadId
            + "' is already in the thread location map: "
            + locations);
    locations.put(pThreadId, new SingleThreadState(pLoc, pNum));
  }

}