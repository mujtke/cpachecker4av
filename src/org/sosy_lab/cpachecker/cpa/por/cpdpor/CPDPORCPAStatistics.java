// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.por.cpdpor;

import java.io.PrintStream;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;

public class CPDPORCPAStatistics implements Statistics {

  private CPDPORStatistics statistics;

  public CPDPORCPAStatistics(CPDPORStatistics pStatistics) {
    statistics = pStatistics;
  }

  @Override
  public void printStatistics(PrintStream pOut, Result pResult, UnmodifiableReachedSet pReached) {
    pOut.println(
        "CPDPOR dependency computation overhead: ("
            + statistics.cpdporComputeDepTimer.getConsumedTime()
            + ", "
            + statistics.depComputeTimes.getUpdateCount()
            + ")");
    pOut.println("CPDPOR constraint entailment information: ");
    pOut.println("   Entail:        " + statistics.depConstraintsEntailTimes.getUpdateCount());
    pOut.println("   Not Entail:    " + statistics.depConstraintsNotEntailTimes.getUpdateCount());
    pOut.println("   Other Cases:   " + statistics.depConstraintsOtherCaseTimes.getUpdateCount());
    pOut.println("CPDPOR check skip information: ");
    pOut.println(
        "   Check Times:                                       "
            + statistics.checkSkipTimes.getUpdateCount());
    pOut.println(
        "   Unconditional Dependent Times:                     "
            + statistics.checkSkipUnDepTimes.getUpdateCount());
    pOut.println(
        "   Unconditional Independent Times:                   "
            + statistics.checkSkipUnIndepTimes.getUpdateCount());
    pOut.println(
        "   Conditional Dependent Times:                       "
            + statistics.checkSkipCondDepTimes.getUpdateCount());
    pOut.println(
        "   Conditional Independent Times:                     "
            + statistics.checkSkipCondIndepTimes.getUpdateCount());
    pOut.println(
        "   Other Cases (loop start or thread creation) Times: "
            + statistics.checkSkipOtherCaseTimes.getUpdateCount());
    pOut.println("CPDPOR avoid exploration information: ");
    pOut.println(
        "   Avoid Exploration Total Times:             "
            + statistics.avoidExplorationTimes.getUpdateCount());
    pOut.println(
        "   Real Redundant (Constraint Computation):   "
            + statistics.realRedundantTimes.getUpdateCount());
  }

  @Override
  public @Nullable String getName() {
    return "CPDPORCPA";
  }

}
