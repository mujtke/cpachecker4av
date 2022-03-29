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

import static com.google.common.collect.FluentIterable.from;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.ast.AExpression;
import org.sosy_lab.cpachecker.cfa.ast.AFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.AIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.AStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression;
import org.sosy_lab.cpachecker.cfa.model.AStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdgeType;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.postprocessing.global.CFACloner;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCodeException;
import org.sosy_lab.cpachecker.util.Pair;

public class ThreadOperator {

  private final boolean forConcurrent;
  private final boolean allowMultipleLHS;
  private final int maxNumberOfThreads;
  private final boolean useClonedFunctions;
  private final boolean useAllPossibleClones;
  private final boolean useIncClonedFunc;
  private final String mainThreadId;
  private CFA cfa;

  public final static int MIN_THREAD_NUM = 0;
  // we only use these two strings that related to the generation process of a thread.
  public static final String THREAD_START = "pthread_create";
  private static final String THREAD_ID_SEPARATOR = "__CPAchecker__";
  // we use this string to remove exited threads.
  private static final String THREAD_EXIT = "pthread_exit";
  // this string is used for cloned thread.
  public static final String SEPARATOR = "__cloned_function__";

  public ThreadOperator(
      boolean pForConcurrent,
      boolean pAllowMultipleLHS,
      int pMaxNumberOfThreads,
      boolean pUseClonedFunctions,
      boolean pUseAllPossibleClones,
      boolean pUseIncClonedFunc,
      String pMainThreadId,
      CFA pCfa) {
    forConcurrent = pForConcurrent;
    allowMultipleLHS = pAllowMultipleLHS;
    maxNumberOfThreads = pMaxNumberOfThreads;
    useClonedFunctions = pUseClonedFunctions;
    useAllPossibleClones = pUseAllPossibleClones;
    useIncClonedFunc = pUseIncClonedFunc;
    mainThreadId = pMainThreadId;
    cfa = pCfa;
  }

  /**
   * This function determines the thread-id of the edge of pCfaEdge.
   *
   * @param pState The old abstract state which contains the thread location map that is used for
   *        indicating the location of each thread.
   * @param pCfaEdge The edge used for generating thread-id if it is a thread creating edge.
   * @return The thread-id of pCfaEdge.
   */
  public String getThreadIdThroughEdge(final MultiThreadState pState, final CFAEdge pCfaEdge) {
    // short cut.
    if (!forConcurrent) {
      return mainThreadId;
    }

    CFANode edgePreLoc = pCfaEdge.getPredecessor();
    Map<String, SingleThreadState> threadLocMap = pState.getThreadLocations();

    // try to get the thread id through the precursor of pCfaEdge.
    ImmutableSet<String> edgeThread =
        from(threadLocMap.keySet())
            .filter(tl -> threadLocMap.get(tl).getLocationNode().equals(edgePreLoc))
            .toSet();
    // only one thread is located at edgePreLoc.
    assert edgeThread.size() <= 1
        : "multiple active threads are not allowed: " + pState + "\tedge: " + pCfaEdge;
    // then either the same function is called in different threads -> not supported.
    // (or CompositeCPA and ThreadingCPA do not work together)

    return edgeThread.isEmpty() ? null : edgeThread.iterator().next();
  }

  /**
   * This function generates a new thread transfer info if a new thread is created.
   *
   * @param pState The old abstract state that contains the thread location map.
   * @param pCfaEdge The edge transfer the from pState.
   * @return If pCfaEdge is an thread creation edge, a new thread transfer information will be
   *         created, otherwise, null will be returned to indicate that no new thread is created.
   * @throws CPATransferException If we could not determine the entry location according to the
   *         given parameters, a CPATransferException will be throw.
   */
  public Pair<String, SingleThreadState>
      genNewThreadTransferInfo(final MultiThreadState pState, final CFAEdge pCfaEdge)
          throws CPATransferException {
    // thread creation.
    if (pCfaEdge.getEdgeType().equals(CFAEdgeType.StatementEdge)) {
      AStatement statement = ((AStatementEdge) pCfaEdge).getStatement();
      if (statement instanceof AFunctionCall) {
        AExpression functionNameExp =
            ((AFunctionCall) statement).getFunctionCallExpression().getFunctionNameExpression();
        if (functionNameExp instanceof AIdExpression) {
          final String functionName = ((AIdExpression) functionNameExp).getName();
          if (functionName.equals(THREAD_START)) {
            return genNewThreadLocationInstance(pState, (AFunctionCall) statement);
          }
        }
      }
    }

    return null;
  }

  /**
   * A new thread location instance of the newly created thread will be generated according to the
   * given parameters.
   *
   * @param pState The old state that contains the thread location maps.
   * @param pStmt The thread creation edge.
   * @return The new thread location instance.
   * @throws CPATransferException This exception will be throw if we could not determine the entry
   *         location of the newly created thread.
   */
  private Pair<String, SingleThreadState>
      genNewThreadLocationInstance(final MultiThreadState pState, final AFunctionCall pStmt)
          throws CPATransferException {
    // first check for some possible errors and unsupported parts
    List<? extends AExpression> params =
        pStmt.getFunctionCallExpression().getParameterExpressions();
    if (!(params.get(0) instanceof CUnaryExpression)) {
      throw new UnrecognizedCodeException("unsupported thread assignment", params.get(0));
    }
    if (!(params.get(2) instanceof CUnaryExpression)) {
      throw new UnrecognizedCodeException("unsupported thread function call", params.get(2));
    }
    CExpression expr0 = ((CUnaryExpression) params.get(0)).getOperand();
    CExpression expr2 = ((CUnaryExpression) params.get(2)).getOperand();
    if (!(expr0 instanceof CIdExpression)) {
      throw new UnrecognizedCodeException("unsupported thread assignment", expr0);
    }
    if (!(expr2 instanceof CIdExpression)) {
      throw new UnrecognizedCodeException("unsupported thread function call", expr2);
    }

    // now create the thread-id.
    SingleThreadState newThreadState = createNewThreadState(pState, expr2.toString());
    String newThreadId = createNewThreadIdWithNumber(pState, ((CIdExpression) expr0).getName());

    return Pair.of(newThreadId, newThreadState);
  }

  private SingleThreadState createNewThreadState(final MultiThreadState pState, String pThreadFunc)
      throws CPATransferException {
    // get thread location map and generate the collection of cloned function number.
    Map<String, SingleThreadState> threadLocMap = pState.getThreadLocations();
    ImmutableList<Integer> usedFuncNumbers =
        from(threadLocMap.values()).transform(SingleThreadState::getNum).toList();

    if (useAllPossibleClones) {
      for (int i = MIN_THREAD_NUM; i < maxNumberOfThreads; ++i) {
        if (!usedFuncNumbers.contains(i)) {
          if (useClonedFunctions) {
            pThreadFunc = pThreadFunc + SEPARATOR + i;
          }
          return new SingleThreadState(cfa.getFunctionHead(pThreadFunc), i);
        }
      }
    } else {
      int num = 0;
      if (useIncClonedFunc) {
        num = getNewThreadNum(pState, pThreadFunc);
      } else {
        num = getSmallestMissingThreadNum(usedFuncNumbers);
      }
      if (useClonedFunctions) {
        pThreadFunc = pThreadFunc + SEPARATOR + num;
      }
      return new SingleThreadState(cfa.getFunctionHead(pThreadFunc), num);
    }

    throw new CPATransferException(
        "could not determine the entry location of function '" + pThreadFunc + "' at" + pState);
  }

  private int getNewThreadNum(final MultiThreadState pState, String pFuncName) {
    Map<String, Integer> thrdNumMap = new HashMap<>();
    for (SingleThreadState ts : pState.getThreadStates()) {
      String[] funcInfo = ts.getLocation().getFunctionName().split(CFACloner.SEPARATOR);
      if (!thrdNumMap.containsKey(funcInfo[0])) {
        thrdNumMap.put(funcInfo[0], 1);
      } else {
        thrdNumMap.put(funcInfo[0], thrdNumMap.get(funcInfo[0]) + 1);
      }
    }

    if (thrdNumMap.containsKey(pFuncName)) {
      return thrdNumMap.get(pFuncName) + 1;
    } else {
      return 1;
    }
  }

  private int getSmallestMissingThreadNum(ImmutableList<Integer> pUsedFuncNumbers) {
    int num = MIN_THREAD_NUM;
    while (pUsedFuncNumbers.contains(num)) {
      ++num;
    }
    return num;
  }

  private String createNewThreadIdWithNumber(final MultiThreadState pState, String pThreadVar)
      throws UnrecognizedCodeException {
    // get all the thread identifiers.
    Set<String> threadIds = pState.getThreadIds();

    String newThreadId = pThreadVar;
    if (!allowMultipleLHS && threadIds.contains(newThreadId)) {
      throw new UnrecognizedCodeException(
          "multiple thread assignments to the same LHS is not supported: " + threadIds,
          null,
          null);
    }

    int index = 0;
    while (threadIds.contains(newThreadId) && (index < maxNumberOfThreads)) {
      index++;
      newThreadId = pThreadVar + THREAD_ID_SEPARATOR + index;
    }

    return newThreadId;
  }

  /**
   * This function removes any threads that reached the end-point of its corresponding cfa.
   *
   * @param pState The full abstract state that contains the thread location map.
   * @param pClass The real class of pState, in this function a new state will be created.
   * @return The new created abstract state which contains no exited thread.
   *
   * @implNote A new abstract state corresponding to pState will be created, and the original class
   *           of pClass should contains a copy constructor.
   */
  public AbstractState
      exitThreads(final AbstractState pState, Class<? extends AbstractState> pClass) {
    try {
      AbstractState tmpState = pClass.getConstructor(pClass).newInstance(pState);

      // get the thread location information.
      Map<String, SingleThreadState> threadLocMap =
          ((ThreadInfoProvider) pState).getMultiThreadState().getThreadLocations();
      // clean up exited threads.
      // this is done before applying any other step.
      ImmutableSet<String> threadsName = ImmutableSet.copyOf(threadLocMap.keySet());
      for (String thread : threadsName) {
        if (isLastNodeOfThread(threadLocMap.get(thread).getLocationNode())) {
          ((ThreadInfoProvider) tmpState).removeThreadId(thread);
        }
      }

      return tmpState;
    } catch (Exception e) {
      e.printStackTrace();
    }

    // exception occur.
    return null;
  }

  private boolean isLastNodeOfThread(final CFANode pNode) {
    if (0 == pNode.getNumLeavingEdges()) {
      return true;
    }

    if (1 == pNode.getNumEnteringEdges()) {
      return isThreadExit(pNode.getEnteringEdge(0));
    }

    return false;
  }

  private static boolean isThreadExit(final CFAEdge pCfaEdge) {
    if (CFAEdgeType.StatementEdge == pCfaEdge.getEdgeType()) {
      AStatement statement = ((AStatementEdge) pCfaEdge).getStatement();
      if (statement instanceof AFunctionCall) {
        AExpression functionNameExp =
            ((AFunctionCall) statement).getFunctionCallExpression().getFunctionNameExpression();
        if (functionNameExp instanceof AIdExpression) {
          return THREAD_EXIT.equals(((AIdExpression) functionNameExp).getName());
        }
      }
    }
    return false;
  }

}
