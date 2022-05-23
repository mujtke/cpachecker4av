// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.por.pcdpor;

import java.util.Collection;
import java.util.Optional;
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
import org.sosy_lab.cpachecker.core.interfaces.WrapperCPA;
import org.sosy_lab.cpachecker.cpa.bdd.BDDCPA;
import org.sosy_lab.cpachecker.cpa.bdd.PredicateManager;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateCPA;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;
import org.sosy_lab.cpachecker.util.predicates.regions.NamedRegionManager;

@Options(prefix = "cpa.por.pcdpor")
public class PCDPORCPA extends AbstractCPA
    implements ConfigurableProgramAnalysis, StatisticsProvider {

  private final CFA cfa;
  private final Configuration config;

  private final PCDPORStatistics statistics;
  private final PCDPORCPAStatistics stats;

  @Option(
    secure = true,
    description = "With this option enabled, function calls that occur"
        + " in the CFA are followed. By disabling this option one can traverse a function"
        + " without following function calls (in this case FunctionSummaryEdges are used)")
  private boolean followFunctionCalls = true;

  @Option(
      secure = true,
    description = "Which type of state should be used to compute the constrained "
        + "dependency at certain state?"
        + "\n- bdd: BDDState (default)"
        + "\n- predicate: PredicateAbstractState"
        + "\nNOTICE: Corresponding CPA should be used!",
      values = {"BDD", "PREDICATE"},
      toUppercase = true)
  private String depComputationStateType = "BDD";
  
  public static CPAFactory factory() {
    return AutomaticCPAFactory.forType(PCDPORCPA.class);
  }

  public PCDPORCPA(
      Configuration pConfig,
      LogManager pLogger,
      CFA pCfa)
      throws InvalidConfigurationException {
    super("sep", "sep", new PCDPORTransferRelation(pConfig, pLogger, pCfa));

    pConfig.inject(this);

    cfa = pCfa;
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

  @SuppressWarnings("resource")
  @Override
  public PrecisionAdjustment getPrecisionAdjustment() {
    try {
      // create environment from CPA.
      Optional<ConfigurableProgramAnalysis> optCPAs = GlobalInfo.getInstance().getCPA();
      if (optCPAs.isPresent()) {
        if (depComputationStateType.equals("BDD")) {
          BDDCPA bddCpa = retriveCPA(optCPAs.get(), BDDCPA.class);
          NamedRegionManager manager = bddCpa.getManager();

          return new PCDPORPrecisionAdjustment(
              GlobalInfo.getInstance().getEdgeInfo().getCondDepGraph(),
              new BDDICComputer(cfa, new PredicateManager(config, manager, cfa), statistics),
              statistics);
        } else if (depComputationStateType.equals("PREDICATE")) {
          PredicateCPA predCpa = retriveCPA(optCPAs.get(), PredicateCPA.class);

          return new PCDPORPrecisionAdjustment(
              GlobalInfo.getInstance().getEdgeInfo().getCondDepGraph(),
              new PredicateICComputer(predCpa, statistics),
              statistics);
        } else {
          throw new InvalidConfigurationException(
              "Invalid Configuration: not support for the type of constrained dependency computation: '"
                  + depComputationStateType
                  + "'.");
        }
      }
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
