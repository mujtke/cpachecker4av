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
package org.sosy_lab.cpachecker.util.dependence.conditional;

import org.sosy_lab.cpachecker.util.statistics.StatCounter;
import org.sosy_lab.cpachecker.util.statistics.StatInt;
import org.sosy_lab.cpachecker.util.statistics.StatKind;
import org.sosy_lab.cpachecker.util.statistics.StatTimer;

public class CondDepGraphBuilderStatistics {

  // timer.
  final StatTimer nodeBuildTimer =
      new StatTimer("Time for building the nodes of conditional dependence graph");
  final StatTimer depGraphBuildTimer =
      new StatTimer("Time for building conditional dependence graph");

  // counter.
  final StatInt gVarAccessNodeNumber = new StatInt(StatKind.SUM, "Number of dependent node");
  final StatCounter depNodePairNumber = new StatCounter("Number of dependent node pairs");
  final StatCounter unCondDepNodePairNumber =
      new StatCounter("Number of un-conditional dependent node pairs");
  final StatCounter blockNumber = new StatCounter("Number of block");
  final StatInt blockSize = new StatInt(StatKind.COUNT, "Size of block");
}
