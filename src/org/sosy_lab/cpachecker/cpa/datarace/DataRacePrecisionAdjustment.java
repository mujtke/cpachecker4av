// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.datarace;

import static com.google.common.collect.FluentIterable.from;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import java.util.Optional;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.dependence.DGNode;
import org.sosy_lab.cpachecker.util.dependence.conditional.ConditionalDepGraph;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;

public class DataRacePrecisionAdjustment implements PrecisionAdjustment {

  private final ConditionalDepGraph condDepGraph;

  public DataRacePrecisionAdjustment() {
    condDepGraph = GlobalInfo.getInstance().getEdgeInfo().getCondDepGraph();
  }

  @Override
  public Optional<PrecisionAdjustmentResult> prec(
      AbstractState pState,
      Precision pPrecision,
      UnmodifiableReachedSet pStates,
      Function<AbstractState, AbstractState> pStateProjection,
      AbstractState pFullState)
      throws CPAException, InterruptedException {

    if(pFullState instanceof ARGState) {
      ARGState argCurState = (ARGState) pFullState,
          argParState = argCurState.getParents().iterator().next();
      
      ImmutableList<Pair<DataRaceState, CFAEdge>> drBrotherStateAndEdgePairs =
          from(argParState.getChildren()).transform(
              bs -> Pair.of(
                  AbstractStates.extractStateByType(bs, DataRaceState.class),
                  argParState.getEdgeToChild(bs)))
              .toList();
      
      if (drBrotherStateAndEdgePairs.size() > 1) {
        for (int i = 0; i < drBrotherStateAndEdgePairs.size() - 1; ++i) {
          Pair<DataRaceState, CFAEdge> drBrotherPairI = drBrotherStateAndEdgePairs.get(i);
          DGNode drBrotherDepNodeI = condDepGraph.getDGNode(drBrotherPairI.getSecond().hashCode());

          for (int j = 1; j < drBrotherStateAndEdgePairs.size(); ++j) {
            Pair<DataRaceState, CFAEdge> drBrotherPairJ = drBrotherStateAndEdgePairs.get(j);
            DGNode drBrotherDepNodeJ =
                condDepGraph.getDGNode(drBrotherPairJ.getSecond().hashCode());
            
            if (condDepGraph.dep(drBrotherDepNodeI, drBrotherDepNodeJ) != null) {
              drBrotherPairI.getFirst().updateDataRace();
              drBrotherPairJ.getFirst().updateDataRace();
            }
          }
        }
      }
    }

    return Optional.of(
        PrecisionAdjustmentResult
            .create(pState, pPrecision, PrecisionAdjustmentResult.Action.CONTINUE));
  }

}
