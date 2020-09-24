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
package org.sosy_lab.cpachecker.core.algorithm.ccex;

import java.util.List;
import java.util.logging.Level;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.AnalysisDirection;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.ARGUtils;
import org.sosy_lab.cpachecker.cpa.arg.path.ARGPath;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormula;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormulaManagerImpl;
import org.sosy_lab.cpachecker.util.predicates.smt.FormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.smt.Solver;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.ProverEnvironment;
import org.sosy_lab.java_smt.api.SolverContext.ProverOptions;
import org.sosy_lab.java_smt.api.SolverException;

@Options(prefix = "ccex")
public class CounterexampleChecker {

  @Option(
    secure = true,
    description = "Whether use the core formulas that the last assume formula "
        + "depends on to check the satisfiability of a counterexample.")
  private boolean useCoreFormulas = false;

  protected final Configuration config;
  protected final LogManager logger;
  protected final ShutdownNotifier shutdownNotifier;

  private final Solver solver;
  private final FormulaManagerView formulaManager;
  private final PathFormulaManager pathFormulaManager;

  public CounterexampleChecker(
      CFA pCfa,
      Configuration pConfig,
      LogManager pLogger,
      ShutdownNotifier pShutdownNotifier)
      throws InvalidConfigurationException {
    pConfig.inject(this);

    config = pConfig;
    logger = pLogger;
    shutdownNotifier = pShutdownNotifier;

    solver = Solver.create(pConfig, pLogger, pShutdownNotifier);
    formulaManager = solver.getFormulaManager();
    pathFormulaManager =
        new PathFormulaManagerImpl(
            formulaManager,
            pConfig,
            pLogger,
            pShutdownNotifier,
            pCfa,
            AnalysisDirection.FORWARD);
  }

  public boolean infeasible(ReachedSet pReachedSet)
      throws CPATransferException, InterruptedException {
    logger.log(Level.INFO, "Start checking the counterexample.");

    assert ARGUtils
        .checkARG(pReachedSet) : "ARG and reached set do not match before the check";

    final ARGState lastElement = (ARGState) pReachedSet.getLastState();
    assert lastElement
        .isTarget() : "Last element in reached is not a target state before the check.";

    final @Nullable ARGPath path = ARGUtils.getOnePathTo(lastElement);

    boolean result = false;
    try {
      result = infeasible0(path);
    } catch (SolverException e) {
      throw new InterruptedException("Could not check the satisfiability of path: " + path);
    }
    logger.log(
        Level.INFO,
        "The check operation for the counterexample is finished, the result is: "
            + (result ? "infeasible" : "feasible"));
    return result;
  }

  public boolean infeasible0(ARGPath path)
      throws CPATransferException, InterruptedException, SolverException {
    List<CFAEdge> pathEdges = path.getFullPath();
    if (useCoreFormulas) {
      // TODO Deal with long path formula.
      // When the path formula is too long, it may degrade the performance of SMT solver.
      // Therefore, we need to short the path formula. (the method of shorting the path formula
      // could be found in the optimization section of C-Intp algorithm)
    } else {
      BooleanFormula formula = genEntireFormulaForPath(pathEdges);

      // if the path formula is unsatisfiable, then we can conclude that this error path is actually
      // infeasible.
      return isUnsat(formula);
    }

    return true;
  }

  private BooleanFormula genEntireFormulaForPath(List<CFAEdge> pathEdges)
      throws CPATransferException, InterruptedException {
    PathFormula initFormula = pathFormulaManager.makeEmptyPathFormula();

    for (CFAEdge edge : pathEdges) {
      initFormula = pathFormulaManager.makeAnd(initFormula, edge);
    }

    return initFormula.getFormula();
  }

  private boolean isUnsat(BooleanFormula formula) throws InterruptedException, SolverException {
    try (ProverEnvironment prover = solver.newProverEnvironment(ProverOptions.GENERATE_MODELS)) {
      prover.push(formula);
      boolean unsat = prover.isUnsat();
      return unsat;
    }
  }

}
