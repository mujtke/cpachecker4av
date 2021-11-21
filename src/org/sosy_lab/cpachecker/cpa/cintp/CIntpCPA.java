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

import java.util.Collection;
import java.util.Optional;
import java.util.logging.Level;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
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
import org.sosy_lab.cpachecker.core.interfaces.WrapperCPA;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateCPA;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;

public class CIntpCPA extends AbstractCPA
    implements ConfigurableProgramAnalysis, StatisticsProvider {

  private final Configuration config;
  private final LogManager logger;
  private final ShutdownNotifier shutdownNotifier;
  private final CIntpStatistics statistics;
  private final CIntpCPAStatistics stats;

  private CFA cfa;

  public static CPAFactory factory() {
    return AutomaticCPAFactory.forType(CIntpCPA.class);
  }

  public CIntpCPA(
      Configuration pConfig,
      LogManager pLogger,
      ShutdownNotifier pShutdownNotifier,
      CFA pCfa) {
    super("sep", "sep", new CIntpTransferRelation());
    pLogger.log(
        Level.INFO,
        "CIntpCPA is used for PredicateCPA, please place CIntpCPA before the PredicateCPA!");

    config = pConfig;
    logger = pLogger;
    shutdownNotifier = pShutdownNotifier;
    statistics = new CIntpStatistics();
    stats = new CIntpCPAStatistics(statistics);

    cfa = pCfa;
  }

  @Override
  public AbstractState getInitialState(CFANode pNode, StateSpacePartition pPartition)
      throws InterruptedException {
    return CIntpState.getInstance();
  }

  @SuppressWarnings("resource")
  @Override
  public PrecisionAdjustment getPrecisionAdjustment() {
    try {
      // create environment from the PredicateCPA.
      Optional<ConfigurableProgramAnalysis> optCPAs = GlobalInfo.getInstance().getCPA();
      if(optCPAs.isPresent()) {
        PredicateCPA cpa = retriveCPA(optCPAs.get(), PredicateCPA.class);
        return new CIntpPrecisionAdjustment(config, logger, shutdownNotifier, statistics, cpa, cfa);
      }
      logger.log(Level.SEVERE, "no CPAs available!");
    } catch (InvalidConfigurationException e) {
      this.logger
          .log(Level.SEVERE, "Failed to get the precision adjustment operator: " + e.getMessage());
      e.printStackTrace();
    }
    return null;
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    pStatsCollection.add(stats);
  }

  @SuppressWarnings("unchecked")
  public <T extends ConfigurableProgramAnalysis> T
      retriveCPA(final ConfigurableProgramAnalysis pCPA, Class<T> pClass)
          throws InvalidConfigurationException {
    if (pCPA.getClass().equals(pClass)) {
      return (T) pCPA;
    } else if (pCPA instanceof WrapperCPA) {
      WrapperCPA wCPAs = (WrapperCPA) pCPA;
      T result = wCPAs.retrieveWrappedCpa(pClass);

      if (result != null) {
        return result;
      }
    }
    throw new InvalidConfigurationException("could not find the CPA " + pClass + " from " + pCPA);
  }

}
