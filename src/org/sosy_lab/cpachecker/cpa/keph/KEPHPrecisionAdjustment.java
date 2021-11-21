// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.keph;

import com.google.common.base.Function;
import de.uni_freiburg.informatik.ultimate.smtinterpol.util.ArrayQueue;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.exceptions.CPAException;

public class KEPHPrecisionAdjustment implements PrecisionAdjustment {

  private final LogManager logger;

  // Key Event: an event is called a key event if it need to access variables.
  private Set<Integer> keyEventCache = new HashSet<>();

  // explored key event paths.
  private Set<Integer> expdKEPCache = new HashSet<>();

  public KEPHPrecisionAdjustment(LogManager pLogger, CFA pCfa) {
    logger = pLogger;
    keyEventCache = extractKeyEvents(pLogger, pCfa);
  }

  private Set<Integer> extractKeyEvents(LogManager pLogger, CFA pCfa) {
    EdgeEvaluationExtractor extractor = new EdgeEvaluationExtractor(pLogger);
    Set<Integer> result = new HashSet<>();

    // get entry point of all the functions in the given program.
    Iterator<FunctionEntryNode> funcIter = pCfa.getAllFunctionHeads().iterator();
    Set<Integer> finishedEdges = new HashSet<>();
    // iteratively process each function.
    while (funcIter.hasNext()) {
      FunctionEntryNode func = funcIter.next();
      Queue<CFAEdge> edgeQueue = new ArrayQueue<>();

      // bfs strategy.
      edgeQueue.add(func.getLeavingEdge(0));
      while (!edgeQueue.isEmpty()) {
        CFAEdge edge = edgeQueue.remove();
        Integer edgeHash = edge.hashCode();

        // this edge is not processed.
        if (!finishedEdges.contains(edgeHash)) {
          // get the evaluation information.
          // if no variable is evaluated, then it's not a key event.
          if (!extractor.extractEdgeEvaluationInfo(edge).isEmpty()) {
            result.add(edgeHash);
          }
          finishedEdges.add(edgeHash);

          // System.out.println(edge + "\t" + result.get(edgeHash));

          // process its successor edge.
          CFANode edgeSucNode = edge.getSuccessor();
          for (int i = 0; i < edgeSucNode.getNumLeavingEdges(); ++i) {
            edgeQueue.add(edgeSucNode.getLeavingEdge(i));
          }
        }
      }
    }

    return result;
  }

  @Override
  public Optional<PrecisionAdjustmentResult> prec(
      AbstractState pState,
      Precision pPrecision,
      UnmodifiableReachedSet pStates,
      Function<AbstractState, AbstractState> pStateProjection,
      AbstractState pFullState)
      throws CPAException, InterruptedException {
    KEPHState kephState = (KEPHState) pState;

    // current action is not a key event, we only inherit the key event path hash from the old one.
    if (!keyEventCache.contains(kephState.getNextEdgeHash())) {
      kephState.setNeedRemove(false);
      return Optional.of(
          PrecisionAdjustmentResult
              .create(pState, pPrecision, PrecisionAdjustmentResult.Action.CONTINUE));
    } else {
      int newKeyEventPathHash =
          (kephState.getKeyEventPathHash().toString()
              + kephState.getNextEdgeHash().toString()
              + kephState.getNextEdgeCodeHash().toString()).hashCode();

      if (expdKEPCache.contains(newKeyEventPathHash)) {
        // System.out.println("keph: " + newKeyEventPathHash);
        kephState.setNeedRemove(true);
      } else {
        expdKEPCache.add(newKeyEventPathHash);
        // System.out.println("keph: " + newKeyEventPathHash);

        kephState.setNeedRemove(false);
        kephState.setKeyEventPathHash(newKeyEventPathHash);
      }
      return Optional.of(
          PrecisionAdjustmentResult
              .create(pState, pPrecision, PrecisionAdjustmentResult.Action.CONTINUE));
    }
  }

}
