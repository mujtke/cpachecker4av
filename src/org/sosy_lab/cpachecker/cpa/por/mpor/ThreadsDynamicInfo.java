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
import static com.google.common.collect.FluentIterable.from;

import com.google.common.collect.ImmutableSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.Triple;

public class ThreadsDynamicInfo {

  private Map<String, Pair<Integer, CFAEdge>> threadsDynamicInfo;

  public static ThreadsDynamicInfo empty() {
    return new ThreadsDynamicInfo();
  }

  public ThreadsDynamicInfo() {
    threadsDynamicInfo = new HashMap<>();
  }

  public ThreadsDynamicInfo(ThreadsDynamicInfo pOther) {
    threadsDynamicInfo = new HashMap<>(Map.copyOf(pOther.threadsDynamicInfo));
  }

  public void addThreadDynamicInfo(Triple<String, Integer, CFAEdge> pInfo) {
    int threadNumber = pInfo.getSecond();
    assert threadNumber >= 0;
    String threadId = checkNotNull(pInfo.getFirst());
    CFAEdge threadInEdge = checkNotNull(pInfo.getThird());

    threadsDynamicInfo.put(threadId, Pair.of(threadNumber, threadInEdge));
  }

  public void removeThreadDynamicInfo(String pThreadId) {
    if (pThreadId != null && !pThreadId.isEmpty()) {
      threadsDynamicInfo.remove(pThreadId);
    }
  }

  public void updateThreadDynamicInfo(Triple<String, Integer, CFAEdge> pInfo) {
    addThreadDynamicInfo(pInfo);
  }

  public void updateThreadNumber(String pThreadId, int pThreadNumber) {
    if (threadsDynamicInfo.containsKey(pThreadId)) {
      Pair<Integer, CFAEdge> threadInfo = threadsDynamicInfo.get(pThreadId);
      threadsDynamicInfo.put(pThreadId, Pair.of(pThreadNumber, threadInfo.getSecond()));
    } else {
      throw new AssertionError(
          "no such thread '" + pThreadId + "' in the thread dynamic information map.");
    }
  }

  public void updateThreadInEdge(String pThreadId, CFAEdge pInEdge) {
    if (threadsDynamicInfo.containsKey(pThreadId)) {
      Pair<Integer, CFAEdge> threadInfo = threadsDynamicInfo.get(pThreadId);
      threadsDynamicInfo.put(pThreadId, Pair.of(threadInfo.getFirst(), pInEdge));
    } else {
      throw new AssertionError(
          "no such thread '" + pThreadId + "' in the thread dynamic information map.");
    }
  }

  public boolean containThread(String pThreadId) {
    return threadsDynamicInfo.containsKey(pThreadId);
  }

  public CFAEdge getThreadEdgeByNumber(int pThreadNumber) {
    ImmutableSet<String> targetThreads =
        from(threadsDynamicInfo.keySet())
            .filter(t -> (threadsDynamicInfo.get(t).getFirst() == pThreadNumber))
            .toSet();
    assert targetThreads.size() <= 1;

    return !targetThreads.isEmpty()
        ? threadsDynamicInfo.get(targetThreads.iterator().next()).getSecond()
        : null;
  }

  public Set<Integer> getBiggerThreadNumbers(int pNumber) {
    return from(threadsDynamicInfo.keySet())
        .filter(t -> threadsDynamicInfo.get(t).getFirst() > pNumber)
        .transform(
            t -> {
              return threadsDynamicInfo.get(t).getFirst();
            })
        .toSet();
  }

  public Set<Integer> getSmallerThreadNumbers(int pNumber) {
    return from(threadsDynamicInfo.keySet())
        .filter(t -> threadsDynamicInfo.get(t).getFirst() < pNumber)
        .transform(
            t -> {
              return threadsDynamicInfo.get(t).getFirst();
            })
        .toSet();
  }

  public Set<String> getThreadIds() {
    return threadsDynamicInfo.keySet();
  }

  public Pair<Integer, CFAEdge> getThreadDynamicInfo(String pThreadId) {
    return threadsDynamicInfo.get(pThreadId);
  }

  public Map<String, Pair<Integer, CFAEdge>> getThreadsDynamicInfo() {
    return threadsDynamicInfo;
  }

  @Override
  public int hashCode() {
    return threadsDynamicInfo.hashCode();
  }

  @Override
  public boolean equals(Object pObj) {
    if (pObj == this) {
      return true;
    }

    if (pObj != null && pObj instanceof ThreadsDynamicInfo) {
      ThreadsDynamicInfo other = (ThreadsDynamicInfo) pObj;
      return threadsDynamicInfo.equals(other.threadsDynamicInfo);
    }

    return false;
  }

  @Override
  public String toString() {
    String result = "{ ";

    for (String thread : threadsDynamicInfo.keySet()) {
      Pair<Integer, CFAEdge> threadInfo = threadsDynamicInfo.get(thread);
      result +=
          "(" + thread + "::" + threadInfo.getFirst() + ") <--> " + threadInfo.getSecond() + ") ";
    }
    result += "}";

    return result;
  }
}
