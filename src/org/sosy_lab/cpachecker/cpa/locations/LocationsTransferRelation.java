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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.threading.MultiThreadState;
import org.sosy_lab.cpachecker.util.threading.SingleThreadState;
import org.sosy_lab.cpachecker.util.threading.ThreadOperator;

@Options(prefix = "cpa.locations")
public class LocationsTransferRelation implements TransferRelation {

  @Option(
    secure = true,
    description = "With this option enabled, the migration between two abstract states will apply to concurrent programs (compatible with serial programs).")
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

  @Option(
    secure = true,
    description = "use increased number for each newly created same thread."
        + "When this option is enabled, we need not to clone a thread function many times if "
        + "every thread function is only used once (i.e., cfa.cfaCloner.numberOfCopies can be set to 1).")
  private boolean useIncClonedFunc = false;

  private CFA cfa;
  private final String mainThreadId;
  private final ThreadOperator threadOptr;

  public LocationsTransferRelation(Configuration pConfig, CFA pCfa)
      throws InvalidConfigurationException {
    pConfig.inject(this);
    cfa = checkNotNull(pCfa);
    // we use the main function's name as thread identifier.
    mainThreadId = cfa.getMainFunction().getFunctionName();
    // construct the thread operator.
    threadOptr =
        new ThreadOperator(
            forConcurrent,
            allowMultipleLHS,
            maxNumberOfThreads,
            useClonedFunctions,
            useAllPossibleClones,
            useIncClonedFunc,
            mainThreadId,
            cfa);
  }

  @Override
  public Collection<? extends AbstractState>
      getAbstractSuccessors(AbstractState pState, Precision pPrecision)
          throws CPATransferException, InterruptedException {
    LocationsState locsState = (LocationsState) pState;
    Iterable<CFAEdge> outEdges = locsState.getOutgoingEdges();

    List<AbstractState> results = new ArrayList<>();
    for (CFAEdge edge : outEdges) {
      Collection<? extends AbstractState> sucStates =
          getAbstractSuccessorsForEdge(locsState, pPrecision, edge);
      results.addAll(sucStates);
    }

    return results;
  }

  @Override
  public Collection<? extends AbstractState>
      getAbstractSuccessorsForEdge(AbstractState pState, Precision pPrecision, CFAEdge pCfaEdge)
          throws CPATransferException, InterruptedException {
    // remove all exited threads (new locations state is created).
    LocationsState newLocState =
        (LocationsState) threadOptr.exitThreads(pState, LocationsState.class);
    MultiThreadState locsState = newLocState.getMultiThreadState();

    // get the active thread.
    String activeThread = threadOptr.getThreadIdThroughEdge(locsState, pCfaEdge);
    if (activeThread == null) {
      return ImmutableSet.of();
    }

    // check that whether a new thread should be created, and try to generate the new thread
    // location instance if pCfaEdge is a thread creation edge.
    Pair<String, SingleThreadState> newThreadInstance =
        threadOptr.genNewThreadTransferInfo(locsState, pCfaEdge);

    Map<String, SingleThreadState> locsMap = locsState.getThreadLocations();
    // update the location corresponding to pCfaEdge.
    SingleThreadState activeThreadState = locsMap.get(activeThread);
    locsState.getThreadLocations()
        .put(
            activeThread,
            new SingleThreadState(pCfaEdge.getSuccessor(), activeThreadState.getNum()));
    // update the location of created thread.
    if (newThreadInstance != null) {
      locsMap.put(newThreadInstance.getFirst(), newThreadInstance.getSecond());
    }
    newLocState.setTransThread(activeThread);

    return Sets.newHashSet(newLocState);
  }

  public LocationsState getInitialState(CFANode pLoc) {
    // gather the information of the main thread.
    SingleThreadState mainLocState = new SingleThreadState(pLoc, ThreadOperator.MIN_THREAD_NUM);
    Map<String, SingleThreadState> locMap = new HashMap<>();
    locMap.put(mainThreadId, mainLocState);

    return new LocationsState(locMap, mainThreadId, followFunctionCalls);
  }

}
