// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.por.cpdpor;

import org.sosy_lab.cpachecker.util.statistics.StatCounter;
import org.sosy_lab.cpachecker.util.statistics.StatTimer;

public class CPDPORStatistics {

  // timer
  final StatTimer cpdporComputeDepTimer = new StatTimer("CPDPOR dependency compute time");

  // counter.
  final StatCounter depComputeTimes = new StatCounter("CPDPOR dependency check times");
  final StatCounter depConstraintsEntailTimes = new StatCounter("CPDPOR constraint entail times");
  final StatCounter depConstraintsNotEntailTimes =
      new StatCounter("CPDPOR constraint not entail times");
  final StatCounter depConstraintsOtherCaseTimes =
      new StatCounter("CPDPOR constraints unknown times");

  final StatCounter checkSkipTimes = new StatCounter("CPDPOR check skip times");
  final StatCounter checkSkipUnDepTimes =
      new StatCounter("CPDPOR check-skip unconditional dependent times");
  final StatCounter checkSkipUnIndepTimes =
      new StatCounter("CPDPOR check-skip unconditional independent times");
  final StatCounter checkSkipCondDepTimes =
      new StatCounter("CPDPOR check-skip conditional dependent times");
  final StatCounter checkSkipCondIndepTimes =
      new StatCounter("CPDPOR check-skip conditional independent times");
  final StatCounter checkSkipOtherCaseTimes =
      new StatCounter("CPDPOR check-skip failed times (other cases)");
  final StatCounter realRedundantTimes = new StatCounter("CPDPOR real redundant times");
  final StatCounter avoidExplorationTimes = new StatCounter("CPDPOR avoid exploration times");
}
