// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.observation;

import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.Set;
import java.util.logging.Level;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.defaults.SingleEdgeTransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.dependence.conditional.ConditionalDepGraph;
import org.sosy_lab.cpachecker.util.dependence.conditional.EdgeVtx;
import org.sosy_lab.cpachecker.util.dependence.conditional.Var;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;

public class ObservationTransferRelation extends SingleEdgeTransferRelation {

  private LogManager logger;

  private final ConditionalDepGraph condDepGraph;

  public ObservationTransferRelation(final LogManager pLogger) {
    logger = pLogger;
    condDepGraph = GlobalInfo.getInstance().getEdgeInfo().getCondDepGraph();
  }

  @Override
  public Collection<? extends AbstractState>
      getAbstractSuccessorsForEdge(AbstractState pState, Precision pPrecision, CFAEdge pCfaEdge)
          throws CPATransferException, InterruptedException {
    // TODO Auto-generated method stub
    ObservationState curState = (ObservationState) pState,
        newState = new ObservationState(curState);

    // Obtain the read/write variable information from the conditional dependency graph.
    EdgeVtx edgeRWInfo = (EdgeVtx) condDepGraph.getDGNode(pCfaEdge.hashCode());
    if (edgeRWInfo != null) {
      Set<Var> readVars = edgeRWInfo.getgReadVars(), writeVars = edgeRWInfo.getgWriteVars();

      // NOTICE: we need to update read event first, since a statement always firstly read global
      // variable and then write value to a global variable (i.e., we should avoid the
      // self-observation situation (e.g., x = x + 1;)).
      if (!readVars.isEmpty()) {
        // This is a read event.
        for (Var var : readVars) {
          Pair<SSAVariable, Integer> latestWriteVarInfo =
              newState.getLatestSSAWriteVariable(var.getName());
          if (latestWriteVarInfo != null) {
            // We can update the observation function.
            newState.updateObservationFunction(
                (pCfaEdge.toString() + "_R").hashCode(),
                latestWriteVarInfo.getSecond());
          } else {
            // This read event 'reads' none write global variable.
            logger.log(
                Level.WARNING,
                "The read event reads a none write global variable: var: "
                    + var.getName()
                    + ", edge: "
                    + pCfaEdge);
          }
        }
      }

      // Now, we need to update the write event.
      if (!writeVars.isEmpty()) {
        // This is also a write event.
        for (Var var : writeVars) {
          Pair<SSAVariable, Integer> latestWriteVarInfo =
              newState.getLatestSSAWriteVariable(var.getName());
          if (latestWriteVarInfo != null) {
            SSAVariable writeVar = new SSAVariable(latestWriteVarInfo.getFirst());
            writeVar.updateSubscript();
            newState.updateCurrentWrites(writeVar, (pCfaEdge.toString() + "_W").hashCode());
          } else {
            // New write access event.
            newState.updateCurrentWrites(
                new SSAVariable(var.getName(), 0),
                (pCfaEdge.toString() + "_W").hashCode());
          }
        }
      }
    }

    return ImmutableSet.of(newState);
  }

}
