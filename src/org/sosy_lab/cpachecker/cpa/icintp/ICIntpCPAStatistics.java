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

import java.io.PrintStream;
import javax.annotation.Nullable;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;

public class ICIntpCPAStatistics implements Statistics {

  private ICIntpStatistics statistics;

  public ICIntpCPAStatistics(ICIntpStatistics pStatistics) {
    statistics = pStatistics;
  }

  @Override
  public void printStatistics(PrintStream pOut, Result pResult, UnmodifiableReachedSet pReached) {
    pOut.println("C-Intp overall consumed time: " + statistics.cintpOverallTimer.getConsumedTime());
    pOut.println("C-Intp real times: " + statistics.cintpTimer.getUpdateCount());
    pOut.println("C-Intp real consumed time: " + statistics.cintpTimer.getConsumedTime());
    pOut.println(
        "C-Intp unsat interpolation times: " + statistics.numUnsatIntpTimes.getUpdateCount());

    pOut.println(
        "C-Intp overall path formula chain length: "
            + statistics.numTotalIntpPathLength.getValueSum());
    pOut.println(
        "  Max Length of path formula chain: " + statistics.numTotalIntpPathLength.getMaxValue());
    pOut.println(
        "  Avg Length of path formula chain: " + statistics.numTotalIntpPathLength.getAverage());
    pOut.println(
        "C-Intp generated conditional predicates: "
            + ICIntpPrecisionAdjustment.getFormulaPredicateMap().size());
    pOut.println("C-Intp location cache used times: " + statistics.numUsedCache.getValue());

    long sucICIntpPathGenNum = statistics.numSucICIntpPathGen.getValue();
    long failICIntpPathGenNum = statistics.numFailICIntpPathGen.getValue();
    long totalPathGenNum = sucICIntpPathGenNum + failICIntpPathGenNum;
    float sucPathGenRatio =
        (totalPathGenNum > 0) ? (sucICIntpPathGenNum / (float) totalPathGenNum) * 100.0f : 0;
    float failPathGenRatio =
        (totalPathGenNum > 0) ? (failICIntpPathGenNum / (float) totalPathGenNum) * 100.0f : 0;
    pOut.println(
        "Times of successfully generating the path formula for IC-Intp: "
            + sucICIntpPathGenNum
            + " ("
            + sucPathGenRatio
            + "%)");
    pOut.println(
        "Times of failed generating the path formula for IC-Intp: "
            + failICIntpPathGenNum
            + " ("
            + failPathGenRatio
            + "%)");
  }

  @Override
  public @Nullable String getName() {
    return "ICIntpCPA";
  }
}
