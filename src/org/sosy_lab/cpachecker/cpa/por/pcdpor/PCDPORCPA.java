// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.por.pcdpor;

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
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.cpa.bdd.PredicateManager;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;
import org.sosy_lab.cpachecker.util.predicates.bdd.BDDManagerFactory;
import org.sosy_lab.cpachecker.util.predicates.regions.NamedRegionManager;
import org.sosy_lab.cpachecker.util.predicates.regions.RegionManager;

@Options(prefix = "cpa.por.cpdpor")
public class PCDPORCPA extends AbstractCPA
    implements ConfigurableProgramAnalysis, StatisticsProvider {

  private final CFA cfa;
  private final Configuration config;
  private final NamedRegionManager manager;

  private final PCDPORStatistics statistics;
  private final PCDPORCPAStatistics stats;

  @Option(
    secure = true,
    description = "With this option enabled, function calls that occur"
        + " in the CFA are followed. By disabling this option one can traverse a function"
        + " without following function calls (in this case FunctionSummaryEdges are used)")
  private boolean followFunctionCalls = true;

  public static CPAFactory factory() {
    return AutomaticCPAFactory.forType(PCDPORCPA.class);
  }

  public PCDPORCPA(Configuration pConfig, LogManager pLogger, CFA pCfa)
      throws InvalidConfigurationException {
    super("sep", "sep", new PCDPORTransferRelation(pConfig, pLogger, pCfa));

    pConfig.inject(this);
    cfa = pCfa;

    RegionManager rmgr = new BDDManagerFactory(pConfig, pLogger).createRegionManager();
    manager = new NamedRegionManager(rmgr);
    config = pConfig;

    statistics = new PCDPORStatistics();
    stats = new PCDPORCPAStatistics(statistics);
  }

  @Override
  public AbstractState getInitialState(CFANode pNode, StateSpacePartition pPartition)
      throws InterruptedException {
    return PCDPORState
        .getInitialInstance(pNode, cfa.getMainFunction().getFunctionName(), followFunctionCalls);
  }

  @Override
  public PrecisionAdjustment getPrecisionAdjustment() {
    try {
      return new PCDPORPrecisionAdjustment(
          GlobalInfo.getInstance().getEdgeInfo().getCondDepGraph(),
          new ICComputer(cfa, new PredicateManager(config, manager, cfa), statistics),
          statistics);
    } catch (InvalidConfigurationException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return null;
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    pStatsCollection.add(stats);
  }

}
