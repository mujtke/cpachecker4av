// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.por.pcdpor;

import org.sosy_lab.cpachecker.util.statistics.StatCounter;
import org.sosy_lab.cpachecker.util.statistics.StatTimer;

public class PCDPORStatistics {

  // timer
  final StatTimer pcdporComputeDepTimer = new StatTimer("PCDPOR dependency compute time");

  // counter.
  final StatCounter depComputeTimes = new StatCounter("PCDPOR dependency check times");
  final StatCounter depConstraintsEntailTimes = new StatCounter("PCDPOR constraint entail times");
  final StatCounter depConstraintsNotEntailTimes =
      new StatCounter("PCDPOR constraint not entail times");
  final StatCounter depConstraintsOtherCaseTimes =
      new StatCounter("PCDPOR constraints unknown times");

  final StatCounter checkSkipTimes = new StatCounter("PCDPOR check skip times");
  final StatCounter checkSkipUnDepTimes =
      new StatCounter("PCDPOR check-skip unconditional dependent times");
  final StatCounter checkSkipUnIndepTimes =
      new StatCounter("PCDPOR check-skip unconditional independent times");
  final StatCounter checkSkipCondDepTimes =
      new StatCounter("PCDPOR check-skip conditional dependent times");
  final StatCounter checkSkipCondIndepTimes =
      new StatCounter("PCDPOR check-skip conditional independent times");
  final StatCounter checkSkipOtherCaseTimes =
      new StatCounter("PCDPOR check-skip failed times (other cases)");
  final StatCounter realRedundantTimes = new StatCounter("PCDPOR real redundant times");
  final StatCounter avoidExplorationTimes = new StatCounter("PCDPOR avoid exploration times");
}
