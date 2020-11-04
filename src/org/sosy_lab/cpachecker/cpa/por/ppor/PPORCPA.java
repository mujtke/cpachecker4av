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
package org.sosy_lab.cpachecker.cpa.por.ppor;

import java.util.Collection;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.defaults.AbstractCPA;
import org.sosy_lab.cpachecker.core.defaults.AutomaticCPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;

@Options(prefix = "cpa.por.ppor")
public class PPORCPA extends AbstractCPA
    implements StatisticsProvider, ConfigurableProgramAnalysis {

  private final PPORStatistics statistics;
  private final CFA cfa;

  @Option(
      secure = true,
      description = "With this option enabled, function calls that occur"
          + " in the CFA are followed. By disabling this option one can traverse a function"
          + " without following function calls (in this case FunctionSummaryEdges are used)")
    private boolean followFunctionCalls = true;

  public static CPAFactory factory() {
    return AutomaticCPAFactory.forType(PPORCPA.class);
  }

  public PPORCPA(Configuration pConfig, LogManager pLogger, CFA pCfa)
      throws InvalidConfigurationException {
    super("sep", "sep", new PPORTransferRelation(pConfig, pLogger, pCfa));
    pConfig.inject(this);
    statistics = new PPORStatistics();
    cfa = pCfa;
  }

  @Override
  public AbstractState getInitialState(CFANode pNode, StateSpacePartition pPartition)
      throws InterruptedException {
    return PPORState.getInitialInstance(
        pNode, cfa.getMainFunction().getFunctionName(), followFunctionCalls);
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    pStatsCollection.add(statistics);
    PPORTransferRelation transferRelation = (PPORTransferRelation) this.getTransferRelation();
    pStatsCollection.add(transferRelation.getCondDepGraphBuildStatistics());
  }


}
