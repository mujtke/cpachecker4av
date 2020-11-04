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
package org.sosy_lab.cpachecker.cpa.por.mpor;

import java.io.PrintStream;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.util.statistics.StatInt;
import org.sosy_lab.cpachecker.util.statistics.StatKind;
import org.sosy_lab.cpachecker.util.statistics.ThreadSafeTimerContainer;

public class MPORStatistics implements Statistics {

  final ThreadSafeTimerContainer successorGenTimer =
      new ThreadSafeTimerContainer("Time for generating MPOR successor");
  final ThreadSafeTimerContainer successorLocGenTimer =
      new ThreadSafeTimerContainer("Time for generating successor location");
  final ThreadSafeTimerContainer threadsDynamicInfoUpdateTimer =
      new ThreadSafeTimerContainer("Time for updating the threads dynamic information");
  final ThreadSafeTimerContainer threadsDepChainUpdateTimer =
      new ThreadSafeTimerContainer("Time for updating the threads dependency chain");
  final ThreadSafeTimerContainer canScheduleCheckTimer =
      new ThreadSafeTimerContainer("Time for checking whether the successor could be scheduled");

  final StatInt sizeDepChain = new StatInt(StatKind.SUM, "The size of dependency chain");

  @Override
  public void printStatistics(PrintStream pOut, Result pResult, UnmodifiableReachedSet pReached) {
    put(pOut, 0, successorGenTimer);
    put(pOut, 0, successorLocGenTimer);
    put(pOut, 0, threadsDynamicInfoUpdateTimer);
    put(pOut, 0, threadsDepChainUpdateTimer);
    put(pOut, 0, canScheduleCheckTimer);
    put(pOut, 0, sizeDepChain);
  }

  @Override
  public @Nullable String getName() {
    return "MPORCPA";
  }
}
