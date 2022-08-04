// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.datarace;

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
import org.sosy_lab.cpachecker.core.interfaces.WrapperCPA;
import org.sosy_lab.cpachecker.core.interfaces.pcc.ProofChecker.ProofCheckerCPA;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateCPA;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;

public class DataRaceCPA extends AbstractCPA
    implements ConfigurableProgramAnalysis, ProofCheckerCPA {

  private Configuration config;
  private LogManager logger;
  private ShutdownNotifier shutdownNotifier;
  private CFA cfa;

  public static CPAFactory factory() {
    return AutomaticCPAFactory.forType(DataRaceCPA.class);
  }

  public DataRaceCPA(
      Configuration pConfig,
      LogManager pLogger,
      ShutdownNotifier pShutdownNotifier,
      CFA pCfa) {
    super("sep", "sep", new DataRaceTransferRelation());

    config = pConfig;
    logger = pLogger;
    shutdownNotifier = pShutdownNotifier;
    cfa = pCfa;
  }

  @Override
  public AbstractState getInitialState(CFANode pNode, StateSpacePartition pPartition)
      throws InterruptedException {
    return DataRaceState.getInitialInstance();
  }

  @SuppressWarnings("resource")
  @Override
  public PrecisionAdjustment getPrecisionAdjustment() {
    try {
      // create environment from the PredicateCPA.
      Optional<ConfigurableProgramAnalysis> optCPAs = GlobalInfo.getInstance().getCPA();
      if (optCPAs.isPresent()) {
        PredicateCPA cpa = retriveCPA(optCPAs.get(), PredicateCPA.class);
        return new DataRacePrecisionAdjustment(config, logger, shutdownNotifier, cpa, cfa);
      }
      logger.log(Level.SEVERE, "no CPAs available!");
    } catch (InvalidConfigurationException e) {
      this.logger
          .log(Level.SEVERE, "Failed to get the precision adjustment operator: " + e.getMessage());
      e.printStackTrace();
    }
    return null;
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

  public Configuration getConfiguration() {
    return config;
  }

  public CFA getCFA() {
    return cfa;
  }

}
