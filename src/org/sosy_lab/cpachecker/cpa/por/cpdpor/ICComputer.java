// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.por.cpdpor;

import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.ast.FileLocation;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.cfa.types.MachineModel;
import org.sosy_lab.cpachecker.cpa.bdd.BDDState;
import org.sosy_lab.cpachecker.cpa.bdd.BDDVectorCExpressionVisitor;
import org.sosy_lab.cpachecker.cpa.bdd.BitvectorManager;
import org.sosy_lab.cpachecker.cpa.bdd.PredicateManager;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.dependence.conditional.CondDepConstraints;
import org.sosy_lab.cpachecker.util.predicates.regions.NamedRegionManager;
import org.sosy_lab.cpachecker.util.predicates.regions.Region;
import org.sosy_lab.java_smt.api.SolverException;

public class ICComputer {

  // private BitvectorComputer bvComputer;

  private final PredicateManager predmgr;
  private final MachineModel machineModel;
  private final CPDPORStatistics statistics;

  public ICComputer(CFA pCfa, PredicateManager pPredmgr, CPDPORStatistics pStatistics) {
    assert pCfa.getVarClassification().isEmpty();

    predmgr = pPredmgr;
    machineModel = pCfa.getMachineModel();
    statistics = pStatistics;
  }

  public boolean computeDep(CondDepConstraints pICs, BDDState pState) {
    statistics.cpdporComputeDepTimer.start();
    statistics.depComputeTimes.inc();
    assert (!pICs.isUnCondDep());
    // System.out.println("compute ic.");

    // create a dummy assume for this expression.
    Pair<CExpression, String> ic = pICs.getConstraints().iterator().next();
    // CAssumeEdge asuDummyEdge = createDummyAssumeEdge(ic.getFirst());

    // System.out.println("ic: " + ic.getFirst());

    final Region[] expRegion =
        computeExpRegion(ic.getFirst(), pState.getBvmgr(), predmgr);
    // BDDState stateCopy = new BDDState(pState.getManager(), pState.getBvmgr(),
    // pState.getRegion());
    // BDDState stateCopy2 = new BDDState(pState.getManager(), pState.getBvmgr(),
    // pState.getRegion());
    // Region relatedVars = stateCopy2.forget(stateCopy.forget(expRegion).getRegion()).getRegion();

    if (expRegion != null) {
      Region evaluated = pState.getBvmgr().makeOr(expRegion);
      //
      // Region newRegion =
      // pState.getManager().makeAnd(pState.getRegion(), pState.getManager().makeNot(evaluated));

      // BDDState evaluatedBDD = new BDDState(pState.getManager(), pState.getBvmgr(), evaluated);
      // BDDState newRegionBDD = new BDDState(pState.getManager(), pState.getBvmgr(), evaluated);

      NamedRegionManager rmgr = pState.getManager();
      try {
        if (rmgr.entails(pState.getRegion(), evaluated)) {
          // System.out.println("compute result: entailed -> conditional dependent");
          statistics.cpdporComputeDepTimer.stop();
          statistics.depConstraintsEntailTimes.inc();
          return false;
        } else {
          // System.out.println("compute result: not entailed-> conditional dependent");
          statistics.cpdporComputeDepTimer.stop();
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
      statistics.cpdporComputeDepTimer.stop();
      statistics.depConstraintsOtherCaseTimes.inc();
      return true;

      // String curDot = pState.getManager().regionToDot(pState.getRegion());
      // String relatedDot = pState.getManager().regionToDot(relatedVars);
      // String evalDot = pState.getManager().regionToDot(evaluated);
      // String newDot = pState.getManager().regionToDot(newRegion);


      // if (newRegion.isFalse()) {
      // System.out.println("compute result: false -> conditional dependent");
      // return true;
      // } else if (newRegion.isTrue()) {
      // System.out.println("compute result: true -> conditional independent");
      // return false;
      // } else {
      // System.out.println("compute result: not determined! -> conditional independent");
      // return false;
      // }
    } else {
      statistics.cpdporComputeDepTimer.stop();
      statistics.depConstraintsOtherCaseTimes.inc();
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

  private CAssumeEdge createDummyAssumeEdge(CExpression pExp) {
    return new CAssumeEdge(
        pExp.toASTString(),
        FileLocation.DUMMY,
        CFANode.newDummyCFANode("dummy"),
        CFANode.newDummyCFANode("dummy"),
        pExp,
        true);
  }

  /**
   * This function handles assumptions like "if(a==b)" and "if(a!=0)". A region is build for the
   * assumption. This region is added to the BDDstate to get the next state. If the next state is
   * False, the assumption is not fulfilled. In this case NULL is returned.
   */
  // @Override
  // protected BDDState handleAssumption(
  // CAssumeEdge cfaEdge, CExpression expression, boolean truthAssumption)
  // throws UnsupportedCodeException {
  //
  // Partition partition = varClass.getPartitionForEdge(cfaEdge);
  // final Region[] operand =
  // bvComputer.evaluateVectorExpression(
  // partition, expression, CNumericTypes.INT, cfaEdge.getSuccessor(), precision);
  // if (operand == null) {
  // return state;
  // } // assumption cannot be evaluated
  // Region evaluated = bvmgr.makeOr(operand);
  //
  // if (!truthAssumption) { // if false-branch
  // evaluated = rmgr.makeNot(evaluated);
  // }
  //
  // // get information from region into evaluated region
  // Region newRegion = rmgr.makeAnd(state.getRegion(), evaluated);
  // if (newRegion.isFalse()) { // assumption is not fulfilled / not possible
  // return null;
  // } else {
  // return new BDDState(rmgr, bvmgr, newRegion);
  // }
  // }

}
