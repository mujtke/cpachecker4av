// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.por.pcdpor;

import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateAbstractState;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateCPA;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.dependence.conditional.CondDepConstraints;
import org.sosy_lab.cpachecker.util.predicates.AbstractionManager;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormula;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.regions.Region;
import org.sosy_lab.cpachecker.util.predicates.smt.FormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.smt.Solver;

public class PredicateICComputer extends AbstractICComputer {

  private final PCDPORStatistics statistics;

  // PredicateICComputer environment.
  private PredicateCPA predCPA;
  private Solver solver;
  private FormulaManagerView fmgr;
  private PathFormulaManager pfmgr;
  private AbstractionManager amgr;

  public PredicateICComputer(
      PredicateCPA pCpa,
      PCDPORStatistics pStatistics) {
    statistics = pStatistics;

    predCPA = pCpa;
    solver = predCPA.getSolver();
    fmgr = solver.getFormulaManager();
    pfmgr = predCPA.getPathFormulaManager();
    amgr = predCPA.getAbstractionManager();
  }

  @Override
  public boolean computeDep(CondDepConstraints pICs, AbstractState pState) {
    if (pState instanceof ARGState) {
      PredicateAbstractState predAbsState = AbstractStates.extractStateByType(pState, PredicateAbstractState.class);

      statistics.pcdporComputeDepTimer.start();
      statistics.depComputeTimes.inc();
      assert (!pICs.isUnCondDep());

      try {
        // get the region of parent predicate state.
        Region stateRegion = predAbsState.getAbstractionFormula().asRegion();

        // compute the region of independence constraints.
        Pair<CExpression, String> ic = pICs.getConstraints().iterator().next();
        // compute the region of ICs.
        PathFormula icPathFormula = pfmgr.makeAnd(pfmgr.makeEmptyPathFormula(), ic.getFirst());
        Region icRegion =
            amgr.convertFormulaToRegion(fmgr.uninstantiate(icPathFormula.getFormula()));

        // check whether stateRegion entials icRegion.
        if (amgr.entails(stateRegion, icRegion)) {
          statistics.pcdporComputeDepTimer.stop();
          statistics.depConstraintsEntailTimes.inc();
          return false;
        } else {
          statistics.pcdporComputeDepTimer.stop();
          statistics.depConstraintsNotEntailTimes.inc();
          return true;
        }
      } catch (Exception e) {
        e.printStackTrace();
      }

      // conservatively regard the two transitions are dependency at the parent state.
      statistics.pcdporComputeDepTimer.stop();
      statistics.depConstraintsOtherCaseTimes.inc();
      return true;
    }

    // conservatively regard the two transitions are dependency at the parent state.
    statistics.pcdporComputeDepTimer.stop();
    statistics.depConstraintsOtherCaseTimes.inc();
    return true;
  }

}
