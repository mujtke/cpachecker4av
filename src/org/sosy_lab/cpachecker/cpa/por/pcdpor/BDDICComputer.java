// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.por.pcdpor;

import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.types.MachineModel;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.bdd.BDDState;
import org.sosy_lab.cpachecker.cpa.bdd.BDDVectorCExpressionVisitor;
import org.sosy_lab.cpachecker.cpa.bdd.BitvectorManager;
import org.sosy_lab.cpachecker.cpa.bdd.PredicateManager;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.dependence.conditional.CondDepConstraints;
import org.sosy_lab.cpachecker.util.predicates.regions.NamedRegionManager;
import org.sosy_lab.cpachecker.util.predicates.regions.Region;
import org.sosy_lab.java_smt.api.SolverException;

public class BDDICComputer extends AbstractICComputer {

  private final PredicateManager predmgr;
  private final MachineModel machineModel;
  private final PCDPORStatistics statistics;

  public BDDICComputer(CFA pCfa, PredicateManager pPredmgr, PCDPORStatistics pStatistics) {
    assert pCfa.getVarClassification().isEmpty();

    predmgr = pPredmgr;
    machineModel = pCfa.getMachineModel();
    statistics = pStatistics;
  }

  @Override
  public boolean computeDep(CondDepConstraints pICs, AbstractState pState) {
    if (pState instanceof BDDState) {
      BDDState bddState = (BDDState) pState;

      statistics.pcdporComputeDepTimer.start();
      statistics.depComputeTimes.inc();
      assert (!pICs.isUnCondDep());
      // System.out.println("compute ic.");

      // create a dummy assume for this expression.
      Pair<CExpression, String> ic = pICs.getConstraints().iterator().next();

      final Region[] expRegion = computeExpRegion(ic.getFirst(), bddState.getBvmgr(), predmgr);

      if (expRegion != null) {
        Region evaluated = bddState.getBvmgr().makeOr(expRegion);

        NamedRegionManager rmgr = bddState.getManager();
        try {
          if (rmgr.entails(bddState.getRegion(), evaluated)) {
            // System.out.println("compute result: entailed -> conditional dependent");
            statistics.pcdporComputeDepTimer.stop();
            statistics.depConstraintsEntailTimes.inc();
            return false;
          } else {
            // System.out.println("compute result: not entailed-> conditional dependent");
            statistics.pcdporComputeDepTimer.stop();
            statistics.depConstraintsNotEntailTimes.inc();
            return true;
          }
        } catch (SolverException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        } catch (InterruptedException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
        statistics.pcdporComputeDepTimer.stop();
        statistics.depConstraintsOtherCaseTimes.inc();
        return true;

      } else {
        statistics.pcdporComputeDepTimer.stop();
        statistics.depConstraintsOtherCaseTimes.inc();
        return true;
      }
    } else {
      return true;
    }
  }

  private Region[]
      computeExpRegion(CExpression pExp, BitvectorManager pBvMgr, PredicateManager pPredMgr) {
    Region[] value = null;
    try {
      value =
          pExp.accept(new BDDVectorCExpressionVisitor(pPredMgr, null, pBvMgr, machineModel, null));
    } catch (Exception e) {
      return null;
    }
    return value;
  }

}
