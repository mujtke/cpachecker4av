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
import static com.google.common.collect.FluentIterable.from;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
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
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCodeException;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.Triple;

@Options(prefix = "cpa.locations")
public class LocationsTransferRelation implements TransferRelation {

  @Option(
    secure = true,
    description = "With this option enabled, the migration between two abstract states will apply to concurrent programs.")
  private boolean forConcurrent = true;

  @Option(
    secure = true,
    description = "With this option enabled, function calls that occur"
        + " in the CFA are followed. By disabling this option one can traverse a function"
        + " without following function calls (in this case FunctionSummaryEdges are used)")
  private boolean followFunctionCalls = true;

  @Option(
    description = "allow assignments of a new thread to the same left-hand-side as an existing thread.",
    secure = true)
  private boolean allowMultipleLHS = false;

  @Option(
    description = "the maximal number of parallel threads, -1 for infinite. "
        + "When combined with 'useClonedFunctions=true', we need at least N cloned functions. "
        + "The option 'cfa.cfaCloner.numberOfCopies' should be set to N.",
    secure = true)
  private int maxNumberOfThreads = 5;

  @Option(
    description = "do not use the original functions from the CFA, but cloned ones. "
        + "See cfa.postprocessing.CFACloner for detail.",
    secure = true)
  private boolean useClonedFunctions = true;

  @Option(
    description = "in case of witness validation we need to check all possible function calls of cloned CFAs.",
    secure = true)
  private boolean useAllPossibleClones = false;


  final static int MIN_THREAD_NUM = 0;

  // we only use these two strings that related to the generation process of a thread.
  public static final String THREAD_START = "pthread_create";
  private static final String THREAD_ID_SEPARATOR = "__CPAchecker__";
  // we use this string to remove exited threads.
  private static final String THREAD_EXIT = "pthread_exit";
  // this string is used for cloned thread.
  public static final String SEPARATOR = "__cloned_function__";

  private CFA cfa;
  private final String mainThreadId;

  public LocationsTransferRelation(Configuration config, final CFA pCfa)
      throws InvalidConfigurationException {
    config.inject(this);
    cfa = checkNotNull(pCfa);
    // we use the main function's name as thread identifier.
    mainThreadId = cfa.getMainFunction().getFunctionName();
  }

  @Override
  public Collection<? extends AbstractState>
      getAbstractSuccessors(AbstractState pState, Precision pPrecision)
          throws CPATransferException, InterruptedException {
    LocationsState locState = (LocationsState) pState;
    Iterable<CFAEdge> outEdges = locState.getOutgoingEdges();

    List<LocationsState> results = new ArrayList<>();
    for (CFAEdge edge : outEdges) {
      Collection<? extends AbstractState> sucStates =
          getAbstractSuccessorsForEdge(locState, pPrecision, edge);
      for (AbstractState state : sucStates) {
        results.add((LocationsState) state);
      }
    }

    return results;
  }

  /**
   * This function generates the successor state of pState.
   *
   * @implNote When verifying concurrent programs, the function called by each thread should be
   *           cloned to avoid the ambiguity of an edge.
   */
  @Override
  public Collection<? extends AbstractState>
      getAbstractSuccessorsForEdge(AbstractState pState, Precision pPrecision, CFAEdge pCfaEdge)
          throws CPATransferException, InterruptedException {
    // remove all exited threads.
    LocationsState loc = exitThreads((LocationsState) pState);
    CFANode edgPreLoc = pCfaEdge.getPredecessor(), edgSucLoc = pCfaEdge.getSuccessor();

    // get the active thread.
    String activeThread = getThreadId(loc, pCfaEdge);

    // check that whether a new thread should be created.
    // try to generate the thread id. (thread creation edge)
    Triple<String, CFANode, Integer> newThreadInfo = analyzeThreadId(loc, pCfaEdge, edgPreLoc);

    // get the old thread location map.
    HashMap<String, CFANode> oldMap = Maps.newHashMap(loc.getLocationsNode());
    HashMap<String, Integer> oldNum = Maps.newHashMap(loc.getThreadNums());
    // update the old location or add new thread location(thread creation).
    oldMap.put(activeThread, edgSucLoc);
    // if a new thread is generated, we put it into the thread location map.
    if (newThreadInfo != null) {
      oldMap.put(newThreadInfo.getFirst(), newThreadInfo.getSecond());
      oldNum.put(newThreadInfo.getFirst(), newThreadInfo.getThird());
    }

    return Sets.newHashSet(new LocationsState(oldMap, oldNum, activeThread, followFunctionCalls));
  }

  /**
   * This function determines the thread-id of the edge pCfaEdge.
   *
   * @param pLocs The old thread location map which is used for indicating the location of each
   *        thread.
   * @param pCfaEdge The edge used for generating thread-id if it is a thread creating edge.
   * @return The thread-id of pCfaEdge.
   * @throws CPATransferException Throw this exception if the pLocs does not contain the precursor
   *         of pCfaEdge and pCfaEdge is not a thread creation edge.
   */
  private String getThreadId(final LocationsState pLocs, final CFAEdge pCfaEdge)
      throws CPATransferException {
    // short cut.
    if (!forConcurrent) {
      return mainThreadId;
    }

    CFANode edgePreLoc = pCfaEdge.getPredecessor();
    Map<String, CFANode> threadLocMap = pLocs.getLocationsNode();

    // try to get the thread id through the precursor of pCfaEdge.
    ImmutableSet<String> edgThread =
        from(threadLocMap.keySet()).filter(t -> threadLocMap.get(t).equals(edgePreLoc)).toSet();
    assert edgThread.size() == 1 : "invalid edge for the locations state: "
        + pLocs
        + "\tedge: "
        + pCfaEdge;

    return edgThread.iterator().next();
  }

  private Triple<String, CFANode, Integer>
      analyzeThreadId(final LocationsState pLocs, final CFAEdge pCfaEdge, final CFANode pPreLoc)
      throws CPATransferException, UnrecognizedCodeException {
    // thread creation.
    if (pCfaEdge.getEdgeType().equals(CFAEdgeType.StatementEdge)) {
      AStatement statement = ((AStatementEdge) pCfaEdge).getStatement();
      if (statement instanceof AFunctionCall) {
        AExpression functionNameExp =
            ((AFunctionCall) statement).getFunctionCallExpression().getFunctionNameExpression();
        if (functionNameExp instanceof AIdExpression) {
          final String functionName = ((AIdExpression) functionNameExp).getName();
          if (functionName.equals(THREAD_START)) {
            return genThreadIdLocInstance(pLocs, (AFunctionCall) statement);
          }
        }
      }
    }

    return null;
  }

  private Triple<String, CFANode, Integer>
      genThreadIdLocInstance(final LocationsState pLocs, final AFunctionCall pStmt)
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
    Pair<CFANode, Integer> newThreadLocInst = getThreadLocationInstance(pLocs, expr2.toString());
    String newThreadId = createThreadIdWithNumber(pLocs, ((CIdExpression) expr0).getName());

    return Triple.of(newThreadId, newThreadLocInst.getFirst(), newThreadLocInst.getSecond());
  }

  private String createThreadIdWithNumber(
      final LocationsState pLocs,
      final String threadId)
      throws UnrecognizedCodeException {
    Set<String> oldThreadNames = pLocs.getLocationsNode().keySet();

    String newThreadId = threadId;
    if (!allowMultipleLHS && oldThreadNames.contains(threadId)) {
      throw new UnrecognizedCodeException(
          "multiple thread assignments to same LHS not supported: " + threadId,
          null,
          null);
    }

    int index = 0;
    while (oldThreadNames.contains(newThreadId) && (index < maxNumberOfThreads)) {
      index++;
      newThreadId = threadId + THREAD_ID_SEPARATOR + index;
    }

    return newThreadId;
  }

  private LocationsState exitThreads(final LocationsState state) {
    LocationsState tmpState = new LocationsState(state);
    Map<String, CFANode> threadLocMap = tmpState.getLocationsNode();

    // clean up exited threads.
    // this is done before applying any other step.
    ImmutableSet<String> threadName = ImmutableSet.copyOf(threadLocMap.keySet());
    for (String id : threadName) {
      if (isLastNodeOfThread(threadLocMap.get(id))) {
        tmpState.removeThreadId(id);
      }
    }
    return tmpState;
  }

  private boolean isLastNodeOfThread(CFANode node) {
    if (0 == node.getNumLeavingEdges()) {
      return true;
    }

    if (1 == node.getNumEnteringEdges()) {
      return isThreadExit(node.getEnteringEdge(0));
    }

    return false;
  }

  private static boolean isThreadExit(CFAEdge cfaEdge) {
    if (CFAEdgeType.StatementEdge == cfaEdge.getEdgeType()) {
      AStatement statement = ((AStatementEdge) cfaEdge).getStatement();
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

  private Pair<CFANode, Integer>
      getThreadLocationInstance(final LocationsState state, String threadFunc)
      throws CPATransferException {
    Collection<Integer> usedNumbers = state.getThreadNums().values();
    if (useAllPossibleClones) {
      for (int i = MIN_THREAD_NUM; i < maxNumberOfThreads; ++i) {
        if (!usedNumbers.contains(i)) {
          if (useClonedFunctions) {
            threadFunc = threadFunc + SEPARATOR + i;
          }
          System.out.print(threadFunc + "\t");
          return Pair.of(cfa.getFunctionHead(threadFunc), i);
        }
      }
    } else {
      int num = MIN_THREAD_NUM;
      while (usedNumbers.contains(num)) {
        ++num;
      }
      if (useClonedFunctions) {
        threadFunc = threadFunc + SEPARATOR + num;
      }
      return Pair.of(cfa.getFunctionHead(threadFunc), num);
    }

    throw new CPATransferException(
        "could not determine the entry location of function '" + threadFunc + "' at " + state);
  }

  public boolean isFollowFunctionCalls() {
    return followFunctionCalls;
  }

}
