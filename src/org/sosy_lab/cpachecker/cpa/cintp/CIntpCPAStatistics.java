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

import java.io.PrintStream;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;

public class CIntpCPAStatistics implements Statistics {

  private CIntpStatistics statistics;

  public CIntpCPAStatistics(CIntpStatistics pStatistics) {
    statistics = pStatistics;
  }

  @Override
  public void printStatistics(PrintStream pOut, Result pResult, UnmodifiableReachedSet pReached) {
    pOut.println("Time of overall C-Intp: " + statistics.cintpOverallTimer.getSumTime());
    pOut.println("Time of performing concrete C-Intp: " + statistics.cintpTimer.getSumTime());
    pOut.println("  Max Time of C-Intp: " + statistics.cintpTimer.getMaxTime());
    pOut.println("  Avg Time of C-Intp: " + statistics.cintpTimer.getAvgTime());
    pOut.println("  Times of C-Intp: " + statistics.cintpTimer.getUpdateCount());

    pOut.println(
        "Time of generating path formula for C-Intp: "
            + statistics.pathGenerationTimer.getSumTime());
    pOut.println(
        "  Max Length of path formula: " + statistics.numTotalIntpPathLength.getMaxValue());
    pOut.println("  Avg Length of path formula: " + statistics.numTotalIntpPathLength.getAverage());
    pOut.println(
        "  Total Length of path formula: " + statistics.numTotalIntpPathLength.getValueSum());
    pOut.println(
        "Number of generated conditional predicates: "
            + CIntpPrecisionAdjustment.getFormulaPredicateMap().size());
    pOut.println(
        "Times of using cached conditional predicates: " + statistics.numUsedCache.getValue());


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
    return "CIntpCPA";
  }

}
