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
package org.sosy_lab.cpachecker.cpa.cintp;

import org.sosy_lab.cpachecker.util.statistics.StatCounter;
import org.sosy_lab.cpachecker.util.statistics.StatInt;
import org.sosy_lab.cpachecker.util.statistics.StatKind;
import org.sosy_lab.cpachecker.util.statistics.ThreadSafeTimerContainer;

public class CIntpStatistics {

  // timer
  final ThreadSafeTimerContainer pathGenerationTimer = new ThreadSafeTimerContainer("Time for generating C-Intp path formula");
  final ThreadSafeTimerContainer cintpTimer =
      new ThreadSafeTimerContainer("Time for performing concrete C-Intp");
  final ThreadSafeTimerContainer cintpOverallTimer =
      new ThreadSafeTimerContainer("Time for performing C-Intp");

  // counter.
  final StatInt numTotalIntpPathLength =
      new StatInt(StatKind.SUM, "Total length of path formulas used by C-Intp");
  final StatInt numGeneratedPredicates =
      new StatInt(StatKind.COUNT, "The number of generated predicates");
  final StatCounter numUsedCache = new StatCounter("Times of using cached conditional predicates");
  final StatCounter numSucICIntpPathGen =
      new StatCounter("Times of successfully generating the path formula for IC-Intp");
  final StatCounter numFailICIntpPathGen =
      new StatCounter("Times of failed generating the path formula for IC-Intp");
}
