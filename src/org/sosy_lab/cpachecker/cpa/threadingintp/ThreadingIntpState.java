// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.threadingintp;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.FluentIterable.from;
import static org.sosy_lab.cpachecker.cpa.threading.ThreadingTransferRelation.THREAD_JOIN;
import static org.sosy_lab.cpachecker.cpa.threading.ThreadingTransferRelation.isLastNodeOfThread;
import static org.sosy_lab.cpachecker.cpa.threadingintp.ThreadingIntpTransferRelation.extractParamName;
import static org.sosy_lab.cpachecker.cpa.threadingintp.ThreadingIntpTransferRelation.getLockId;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.collect.PathCopyingPersistentTreeMap;
import org.sosy_lab.common.collect.PersistentMap;
import org.sosy_lab.cpachecker.cfa.ast.AExpression;
import org.sosy_lab.cpachecker.cfa.ast.AFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.AIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.AStatement;
import org.sosy_lab.cpachecker.cfa.model.AStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdgeType;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.postprocessing.global.CFACloner;
import org.sosy_lab.cpachecker.core.interfaces.AbstractQueryableState;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.AbstractStateWithLocations;
import org.sosy_lab.cpachecker.core.interfaces.Graphable;
import org.sosy_lab.cpachecker.core.interfaces.Partitionable;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackState;
import org.sosy_lab.cpachecker.cpa.callstack.CallstackStateEqualsWrapper;
import org.sosy_lab.cpachecker.cpa.location.LocationState;
import org.sosy_lab.cpachecker.exceptions.InvalidQueryException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCodeException;
import scala.Int;

/** This immutable state represents a location state combined with a callstack state. */
public class ThreadingIntpState implements AbstractState, AbstractStateWithLocations, Graphable, Partitionable, AbstractQueryableState {

  private static final String PROPERTY_DEADLOCK = "deadlock";

  final static int MIN_THREAD_NUM = 0;
  final static int MAX_PRIORITY_NUMBER = Int.MaxValue(), MIN_PRIORITY_NUMBER = Int.MinValue();

  // String :: identifier for the thread TODO change to object or memory-location
  // CallstackState +  LocationState :: thread-position
  private final PersistentMap<String, ThreadIntpState> threads;

  // String :: lock-id  -->  String :: thread-id
  private final PersistentMap<String, String> locks;

  // String :: interrupt function-name -> interrupt times
  private Map<String, Integer> intpTimes;

  // the enable flags of different interrupt levels.
  private boolean[] intpLevelEnableFlags;

  // String :: identifier for the interrupt
  // this stack only preserves the thread-ids of interrupts, other threads (include main)
  // should not in this stack.
  private final Deque<String> intpStack;

  /**
   * Thread-id of last active thread that produced this exact {@link ThreadingIntpState}. This value
   * should only be set in {@link ThreadingIntpTransferRelation#getAbstractSuccessorsForEdge} and must
   * be deleted in {@link ThreadingIntpTransferRelation#strengthen}, e.g. set to {@code null}. It is not
   * considered to be part of any 'full' abstract state, but serves as intermediate flag to have
   * information for the strengthening process.
   */
  @Nullable private final String activeThread;

  /**
   * This functioncall was called when creating this thread. This value should only be set in
   * {@link ThreadingIntpTransferRelation#getAbstractSuccessorsForEdge} when creating a new thread,
   * i.e., only for the first state in the new thread, which is directly after the function call.
   *
   * <p>
   * It must be deleted in {@link ThreadingIntpTransferRelation#strengthen}, e.g. set to {@code
   * null}. It is not considered to be part of any 'full' abstract state, but serves as intermediate
   * flag to have information for the strengthening process.
   */
  @Nullable
  private final FunctionCallEdge entryFunction;

  /**
   * This map contains the mapping of threadIds to the unique identifier used for witness
   * validation. Without a witness, it should always be empty.
   */
  private final PersistentMap<String, Integer> threadIdsForWitness;

  public ThreadingIntpState(final int pMaxIntpLevel) {
    this.threads = PathCopyingPersistentTreeMap.of();
    this.locks = PathCopyingPersistentTreeMap.of();
    this.intpStack = new ArrayDeque<>();
    this.intpTimes = new HashMap<>();
    this.activeThread = null;
    this.entryFunction = null;
    this.threadIdsForWitness = PathCopyingPersistentTreeMap.of();

    this.intpLevelEnableFlags = new boolean[pMaxIntpLevel];
    for (int i = 0; i < pMaxIntpLevel; ++i) {
      this.intpLevelEnableFlags[i] = true;
    }
  }

  private ThreadingIntpState(
      PersistentMap<String, ThreadIntpState> pThreads,
      PersistentMap<String, String> pLocks,
      Deque<String> pIntpQueue,
      boolean[] pIntpLevelEnableFlags,
      Map<String, Integer> pIntpTimes,
      String pActiveThread,
      FunctionCallEdge entryFunction,
      PersistentMap<String, Integer> pThreadIdsForWitness) {
    this.threads = pThreads;
    this.locks = pLocks;
    this.intpStack = new ArrayDeque<>(pIntpQueue);
    this.intpLevelEnableFlags = new boolean[pIntpLevelEnableFlags.length];
    for (int i = 0; i < pIntpLevelEnableFlags.length; ++i)
      this.intpLevelEnableFlags[i] = pIntpLevelEnableFlags[i];
    this.intpTimes = new HashMap<>(pIntpTimes);
    this.activeThread = pActiveThread;
    this.entryFunction = entryFunction;
    this.threadIdsForWitness = pThreadIdsForWitness;
  }

  private ThreadingIntpState withThreads(PersistentMap<String, ThreadIntpState> pThreads) {
    return new ThreadingIntpState(
        pThreads,
        locks,
        new ArrayDeque<>(intpStack),
        intpLevelEnableFlags,
        new HashMap<>(intpTimes),
        activeThread,
        entryFunction,
        threadIdsForWitness);
  }

  private ThreadingIntpState withLocks(PersistentMap<String, String> pLocks) {
    return new ThreadingIntpState(
        threads,
        pLocks,
        new ArrayDeque<>(intpStack),
        intpLevelEnableFlags,
        new HashMap<>(intpTimes),
        activeThread,
        entryFunction,
        threadIdsForWitness);
  }

  private ThreadingIntpState withThreadIdsForWitness(
      PersistentMap<String, Integer> pThreadIdsForWitness) {
    return new ThreadingIntpState(
        threads,
        locks,
        new ArrayDeque<>(intpStack),
        intpLevelEnableFlags,
        new HashMap<>(intpTimes),
        activeThread,
        entryFunction,
        pThreadIdsForWitness);
  }

  public ThreadingIntpState
      addThreadAndCopy(String id, int num, int pri, AbstractState stack, AbstractState loc) {
    Preconditions.checkNotNull(id);
    Preconditions.checkArgument(!threads.containsKey(id), "thread already exists");
    return withThreads(
        threads
            .putAndCopy(id, new ThreadIntpState(loc, stack, pri, num)));
  }

  public ThreadingIntpState
      updateLocationAndCopy(String id, AbstractState stack, AbstractState loc) {
    Preconditions.checkNotNull(id);
    Preconditions.checkArgument(threads.containsKey(id), "updating non-existing thread");
    return withThreads(
        threads.putAndCopy(
            id,
            new ThreadIntpState(
                loc,
                stack,
                threads.get(id).getPriority(),
                threads.get(id).getNum())));
  }

  public ThreadingIntpState removeThreadAndCopy(String id) {
    Preconditions.checkNotNull(id);
    checkState(threads.containsKey(id), "leaving non-existing thread: %s", id);
    return withThreads(threads.removeAndCopy(id));
  }

  public Set<String> getThreadIds() {
    return threads.keySet();
  }

  public AbstractState getThreadCallstack(String id) {
    return Preconditions.checkNotNull(threads.get(id).getCallstack());
  }

  public LocationState getThreadLocation(String id) {
    return (LocationState) Preconditions.checkNotNull(threads.get(id).getLocation());
  }

  public int getThreadPriority(String id) {
    Preconditions
        .checkArgument(threads.containsKey(id), "does not exist the thread-id '" + id + "'");
    return threads.get(id).getPriority();
  }

  Set<Integer> getThreadNums() {
    Set<Integer> result = new LinkedHashSet<>();
    for (ThreadIntpState ts : threads.values()) {
      result.add(ts.getNum());
    }
    Preconditions.checkState(result.size() == threads.size());
    return result;
  }

  int getNewThreadNum(String pFuncName) {
    Map<String, Integer> thrdNumMap = new HashMap<>();
    for (ThreadIntpState ts : threads.values()) {
      AbstractState locs = ts.getLocation();
      if (locs instanceof LocationState) {
        String[] funcInfo =
            ((LocationState) locs).getLocationNode().getFunctionName().split(CFACloner.SEPARATOR);
        if (!thrdNumMap.containsKey(funcInfo[0])) {
          thrdNumMap.put(funcInfo[0], 1);
        } else {
          thrdNumMap.put(funcInfo[0], thrdNumMap.get(funcInfo[0]) + 1);
        }
      } else {
        return getSmallestMissingThreadNum();
      }
    }

    if (thrdNumMap.containsKey(pFuncName)) {
      return thrdNumMap.get(pFuncName) + 1;
    } else {
      return 1;
    }
  }

  int getSmallestMissingThreadNum() {
    int num = MIN_THREAD_NUM;
    // TODO loop is not efficient for big number of threads
    final Set<Integer> threadNums = getThreadNums();
    while(threadNums.contains(num)) {
      num++;
    }
    return num;
  }

  public ThreadingIntpState enableIntpAndCopy(int pLevel) {
    Preconditions.checkArgument(
        pLevel >= 0 && pLevel < intpLevelEnableFlags.length,
        "interruption level "
            + pLevel
            + " is out of boundary (0 ~ "
            + intpLevelEnableFlags.length
            + ").");
    // Preconditions.checkArgument(
    // intpLevelEnableFlags[pLevel],
    // "the interruption of level " + pLevel + " has already been enabled.");

    ThreadingIntpState result =
        new ThreadingIntpState(
        threads,
        locks,
            new ArrayDeque<>(intpStack),
        intpLevelEnableFlags,
            new HashMap<>(intpTimes),
        activeThread,
        entryFunction,
        threadIdsForWitness);
    result.intpLevelEnableFlags[pLevel] = true;

    return result;
  }

  public ThreadingIntpState enableAllIntpAndCopy() {
    ThreadingIntpState result =
        new ThreadingIntpState(
            threads,
            locks,
            new ArrayDeque<>(intpStack),
            intpLevelEnableFlags,
            new HashMap<>(intpTimes),
            activeThread,
            entryFunction,
            threadIdsForWitness);
    for (int i = 0; i < result.intpLevelEnableFlags.length; ++i) {
      result.intpLevelEnableFlags[i] = true;
    }

    return result;
  }

  public ThreadingIntpState disableIntpAndCopy(int pLevel) {
    Preconditions.checkArgument(
        pLevel >= 0 && pLevel < intpLevelEnableFlags.length,
        "interruption level "
            + pLevel
            + " is out of boundary (0 ~ "
            + intpLevelEnableFlags.length
            + ").");
    // Preconditions.checkArgument(
    // !intpLevelEnableFlags[pLevel],
    // "the interruption of level " + pLevel + " has already been disabled.");

    ThreadingIntpState result =
        new ThreadingIntpState(
        threads,
        locks,
            new ArrayDeque<>(intpStack),
        intpLevelEnableFlags,
            new HashMap<>(intpTimes),
        activeThread,
        entryFunction,
        threadIdsForWitness);
    result.intpLevelEnableFlags[pLevel] = false;

    return result;
  }

  public ThreadingIntpState disableAllIntpAndCopy() {
    ThreadingIntpState result =
        new ThreadingIntpState(
            threads,
            locks,
            new ArrayDeque<>(intpStack),
            intpLevelEnableFlags,
            new HashMap<>(intpTimes),
            activeThread,
            entryFunction,
            threadIdsForWitness);
    for (int i = 0; i < result.intpLevelEnableFlags.length; ++i) {
      result.intpLevelEnableFlags[i] = false;
    }

    return result;
  }

  public ThreadingIntpState addLockAndCopy(String threadId, String lockId) {
    Preconditions.checkNotNull(lockId);
    Preconditions.checkNotNull(threadId);
    checkArgument(
        threads.containsKey(threadId),
        "blocking non-existant thread: %s with lock: %s",
        threadId,
        lockId);
    return withLocks(locks.putAndCopy(lockId, threadId));
  }

  public ThreadingIntpState removeLockAndCopy(String threadId, String lockId) {
    Preconditions.checkNotNull(threadId);
    Preconditions.checkNotNull(lockId);
    checkArgument(
        threads.containsKey(threadId),
        "unblocking non-existant thread: %s with lock: %s",
        threadId,
        lockId);
    return withLocks(locks.removeAndCopy(lockId));
  }

  /** returns whether any of the threads has the lock */
  public boolean hasLock(String lockId) {
    return locks.containsKey(lockId); // TODO threadId needed?
  }

  /** returns whether the given thread has the lock */
  public boolean hasLock(String threadId, String lockId) {
    return locks.containsKey(lockId) && threadId.equals(locks.get(lockId));
  }

  /** returns whether there is any lock registered for the thread. */
  public boolean hasLockForThread(String threadId) {
    return locks.containsValue(threadId);
  }

  public boolean isProcessingInterrupt() {
    return !intpStack.isEmpty();
  }

  @Override
  public String toString() {
    return "( threads={\n"
        + Joiner.on(",\n ").withKeyValueSeparator("=").join(threads)
        + "}\n and locks={"
        + Joiner.on(",\n ").withKeyValueSeparator("=").join(locks)
        + "}\n and intpStack={"
        + Joiner.on(", ").join(intpStack)
        + "}\n and interrupt flags={"
        + Arrays.toString(intpLevelEnableFlags)
        + "}\n"
        + (activeThread == null ? "" : ("\n produced from thread " + activeThread))
        + "\n"
        + Joiner.on(",\n ").withKeyValueSeparator("=").join(threadIdsForWitness)
        + ")";
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof ThreadingIntpState)) {
      return false;
    }
    ThreadingIntpState ts = (ThreadingIntpState)other;
    return threads.equals(ts.threads)
        && locks.equals(ts.locks)
        && intpStack.equals(ts.intpStack)
        && Arrays.equals(intpLevelEnableFlags, ts.intpLevelEnableFlags)
        && Objects.equals(activeThread, ts.activeThread)
        && threadIdsForWitness.equals(ts.threadIdsForWitness);
  }

  @Override
  public int hashCode() {
    return Objects
        .hash(
            threads,
            locks,
            intpStack,
            Arrays.hashCode(intpLevelEnableFlags),
            activeThread,
            threadIdsForWitness);
  }

  private FluentIterable<AbstractStateWithLocations> getLocations() {
    return FluentIterable.from(threads.values())
        .transform(s -> (AbstractStateWithLocations) s.getLocation());
  }

  @Override
  public Iterable<CFANode> getLocationNodes() {
    return getLocations().transformAndConcat(AbstractStateWithLocations::getLocationNodes);
  }

  @Override
  public Iterable<CFAEdge> getOutgoingEdges() {
    return getLocations().transformAndConcat(AbstractStateWithLocations::getOutgoingEdges);
  }

  @Override
  public Iterable<CFAEdge> getIngoingEdges() {
    return getLocations().transformAndConcat(AbstractStateWithLocations::getIngoingEdges);
  }

  @Override
  public String toDOTLabel() {
    StringBuilder sb = new StringBuilder();

    sb.append("[");
    Joiner.on(",\n ").withKeyValueSeparator("=").appendTo(sb, threads);
    sb.append("]");
    sb.append("\nenable flags: [");
    for(int i = 0; i < intpLevelEnableFlags.length; ++i) {
      sb.append(" " + intpLevelEnableFlags[i] + " ");
    }
    sb.append("]");

    return sb.toString();
  }

  @Override
  public boolean shouldBeHighlighted() {
    return false;
  }

  @Override
  public Object getPartitionKey() {
    return threads;
  }


  @Override
  public String getCPAName() {
    return "ThreadingCPA";
  }

  @Override
  public boolean checkProperty(String pProperty) throws InvalidQueryException {
    if (PROPERTY_DEADLOCK.equals(pProperty)) {
      try {
        return hasDeadlock();
      } catch (UnrecognizedCodeException e) {
        throw new InvalidQueryException("deadlock-check had a problem", e);
      }
    }
    throw new InvalidQueryException("Query '" + pProperty + "' is invalid.");
  }

  /**
   * check, whether one of the outgoing edges can be visited
   * without requiring a already used lock.
   */
  private boolean hasDeadlock() throws UnrecognizedCodeException {
    FluentIterable<CFAEdge> edges = FluentIterable.from(getOutgoingEdges());

    // no need to check for existing locks after program termination -> ok

    // no need to check for existing locks after thread termination
    // -> TODO what about a missing ATOMIC_LOCK_RELEASE?

    // no need to check VERIFIER_ATOMIC, ATOMIC_LOCK or LOCAL_ACCESS_LOCK,
    // because they cannot cause deadlocks, as there is always one thread to go
    // (=> the thread that has the lock).
    // -> TODO what about a missing ATOMIC_LOCK_RELEASE?

    // no outgoing edges, i.e. program terminates -> no deadlock possible
    if (edges.isEmpty()) {
      return false;
    }

    for (CFAEdge edge : edges) {
      if (!needsAlreadyUsedLock(edge) && !isWaitingForOtherThread(edge)) {
        // edge can be visited, thus there is no deadlock
        return false;
      }
    }

    // if no edge can be visited, there is a deadlock
    return true;
  }

  /** check, if the edge required a lock, that is already used. This might cause a deadlock. */
  private boolean needsAlreadyUsedLock(CFAEdge edge) throws UnrecognizedCodeException {
    final String newLock = getLockId(edge);
    return newLock != null && hasLock(newLock);
  }

  /** A thread might need to wait for another thread, if the other thread joins at
   * the current edge. If the other thread never exits, we have found a deadlock. */
  private boolean isWaitingForOtherThread(CFAEdge edge) throws UnrecognizedCodeException {
    if (edge.getEdgeType() == CFAEdgeType.StatementEdge) {
      AStatement statement = ((AStatementEdge)edge).getStatement();
      if (statement instanceof AFunctionCall) {
        AExpression functionNameExp = ((AFunctionCall)statement).getFunctionCallExpression().getFunctionNameExpression();
        if (functionNameExp instanceof AIdExpression) {
          final String functionName = ((AIdExpression)functionNameExp).getName();
          if (THREAD_JOIN.equals(functionName)) {
            final String joiningThread = extractParamName(statement, 0);
            // check whether other thread is running and has at least one outgoing edge,
            // then we have to wait for it.
            if (threads.containsKey(joiningThread)
                && !isLastNodeOfThread(getThreadLocation(joiningThread).getLocationNode())) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  /** A ThreadState describes the state of a single thread. */
  private static class ThreadIntpState {

    // String :: identifier for the thread TODO change to object or memory-location
    // CallstackState +  LocationState :: thread-position
    private final AbstractState location;
    private final CallstackStateEqualsWrapper callstack;

    // Notice: normal threads have the lowest priority (i.e., )
    private final int priority;

    // Each thread is assigned to an Integer
    // TODO do we really need this? -> needed for identification of cloned functions.
    private final int num;

    ThreadIntpState(AbstractState pLocation, AbstractState pCallstack, int pPriority, int pNum) {
      location = pLocation;
      callstack = new CallstackStateEqualsWrapper((CallstackState)pCallstack);
      priority = pPriority;
      num= pNum;
    }

    public AbstractState getLocation() {
      return location;
    }

    public AbstractState getCallstack() {
      return callstack.getState();
    }

    public int getPriority() {
      return priority;
    }

    public int getNum() {
      return num;
    }

    public boolean isInterruptThread() {
      return priority != MAX_PRIORITY_NUMBER && priority != MIN_PRIORITY_NUMBER;
    }

    @Override
    public String toString() {
      return location + " " + callstack + " @@ " + priority + " @@ " + num;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof ThreadIntpState)) {
        return false;
      }
      ThreadIntpState other = (ThreadIntpState)o;
      return location.equals(other.location)
          && callstack.equals(other.callstack)
          && priority == other.priority
          && num == other.num;
    }

    @Override
    public int hashCode() {
      return Objects.hash(location, callstack, priority, num);
    }
  }

  /** See {@link #activeThread}. */
  public ThreadingIntpState withActiveThread(@Nullable String pActiveThread) {
    return new ThreadingIntpState(
        threads,
        locks,
        new ArrayDeque<>(intpStack),
        intpLevelEnableFlags,
        new HashMap<>(intpTimes),
        pActiveThread,
        entryFunction,
        threadIdsForWitness);
  }

  String getActiveThread() {
    return activeThread;
  }

  /** See {@link #entryFunction}. */
  public ThreadingIntpState withEntryFunction(@Nullable FunctionCallEdge pEntryFunction) {
    return new ThreadingIntpState(
        threads,
        locks,
        new ArrayDeque<>(intpStack),
        intpLevelEnableFlags,
        new HashMap<>(intpTimes),
        activeThread,
        pEntryFunction,
        threadIdsForWitness);
  }

  /** See {@link #entryFunction}. */
  @Nullable
  public FunctionCallEdge getEntryFunction() {
    return entryFunction;
  }

  @Nullable
  Integer getThreadIdForWitness(String threadId) {
    Preconditions.checkNotNull(threadId);
    return threadIdsForWitness.get(threadId);
  }

  boolean hasWitnessIdForThread(int witnessId) {
    return threadIdsForWitness.containsValue(witnessId);
  }

  ThreadingIntpState setThreadIdForWitness(String threadId, int witnessId) {
    Preconditions.checkNotNull(threadId);
    Preconditions.checkArgument(
        !threadIdsForWitness.containsKey(threadId), "threadId already exists");
    Preconditions.checkArgument(
        !threadIdsForWitness.containsValue(witnessId), "witnessId already exists");
    return withThreadIdsForWitness(threadIdsForWitness.putAndCopy(threadId, witnessId));
  }

  ThreadingIntpState removeThreadIdForWitness(String threadId) {
    Preconditions.checkNotNull(threadId);
    checkArgument(
        threadIdsForWitness.containsKey(threadId), "removing non-existant thread: %s", threadId);
    return withThreadIdsForWitness(threadIdsForWitness.removeAndCopy(threadId));
  }

  public boolean isInterruptEnabled(int pLevel) {
    Preconditions.checkArgument(
        pLevel > 0 && pLevel <= intpLevelEnableFlags.length,
        "interruption level "
            + pLevel
            + " is out of boundary (1 ~ "
            + intpLevelEnableFlags.length
            + ").");

    return intpLevelEnableFlags[pLevel - 1];
  }

  public boolean isAllInterruptDisabled() {
    for (int i = 0; i < this.intpLevelEnableFlags.length; ++i) {
      if (this.intpLevelEnableFlags[i]) {
        return false;
      }
    }
    return true;
  }

  public String getButNotRemoveTopProcInterruptId() {
    if (!intpStack.isEmpty()) {
      return intpStack.peekLast();
    }
    return null;
  }

  public String getFuncNameByThreadId(final String threadId) {
    if (threadId != null && threads.containsKey(threadId)) {
      CFANode loc =
          ((AbstractStateWithLocations) threads.get(threadId).getLocation()).getLocationNodes()
              .iterator()
              .next();
      return loc.getFunctionName();
    } else {
      return null;
    }
  }

  public AbstractState getCallstackByThreadId(final String threadId) {
    if (threadId != null && threads.containsKey(threadId)) {
      return threads.get(threadId).getCallstack();
    } else {
      return null;
    }
  }

  public static String removeCloneInfoOfFuncName(String funcName) {
    if (funcName != null) {
      return funcName.contains(CFACloner.SEPARATOR)
          ? funcName.substring(0, funcName.indexOf(CFACloner.SEPARATOR))
          : funcName;
    }
    return funcName;
  }

  public ImmutableSet<String> getAllInterruptFunctions() {
    // only preserves the name of interrupt functions without clone information.
    return from(threads.values()).filter(s -> s.isInterruptThread())
        .transform(
            s -> ((AbstractStateWithLocations) s.getLocation()).getLocationNodes()
                .iterator()
                .next()
                .getFunctionName())
        .transform(f -> removeCloneInfoOfFuncName(this.getFuncNameByThreadId(f)))
        .toSet();
  }

  public int getCurrentInterruptTimes(String intpFuncName) {
    return intpTimes.containsKey(intpFuncName) ? intpTimes.get(intpFuncName) : 0;
  }

  public void pushIntpStack(String intpThreadId) {
    intpStack.addLast(intpThreadId);
  }

  public void removeIntpFromStack(String intpFuncName) {
    // NOTICE: this may cause the feature 'interrupt reentrant' not well supported!
    // TODO: fix this operation to support interrupt reentrant.
    intpStack.remove(intpFuncName);
  }

  public int getIntpStackLevel() {
    return intpStack.size();
  }

  public void addIntpFuncTimes(String intpFuncName) {
    if (!intpTimes.containsKey(intpFuncName)) {
      intpTimes.put(intpFuncName, 0);
    }
    intpTimes.put(intpFuncName, intpTimes.get(intpFuncName) + 1);
  }

  public boolean isInterruptId(String tid) {
    return threads.get(tid).isInterruptThread();
  }

}
