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
 *
 *
 *  CPAchecker web page:
 *    http://cpachecker.sosy-lab.org
 */
package org.sosy_lab.cpachecker.cpa.icintp;

import org.sosy_lab.cpachecker.util.statistics.StatCounter;
import org.sosy_lab.cpachecker.util.statistics.StatInt;
import org.sosy_lab.cpachecker.util.statistics.StatKind;
import org.sosy_lab.cpachecker.util.statistics.StatTimer;

public class ICIntpStatistics {

  // timer
  final StatTimer cintpOverallTimer = new StatTimer("C-Intp overall time");
  final StatTimer cintpTimer = new StatTimer("C-Intp real time");
  final StatTimer pathGenerationTimer = new StatTimer("C-Intp path generate time");

  // counter.
  final StatCounter numUnsatIntpTimes = new StatCounter("C-Intp unsat interpolation times");
  final StatInt numTotalIntpPathLength = new StatInt(StatKind.SUM, "C-Intp overall path length");
  final StatInt numGeneratedPredicates = new StatInt(StatKind.COUNT, "C-Intp generated predicates");
  final StatCounter numUsedCache = new StatCounter("C-Intp location cache used times");
  final StatCounter numSucICIntpPathGen =
      new StatCounter("IC-Intp successfully generate interpolation path times");
  final StatCounter numFailICIntpPathGen =
      new StatCounter("IC-Intp failed generate interpolation path times");
}
