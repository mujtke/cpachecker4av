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
package org.sosy_lab.cpachecker.cpa.influence;

import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.defaults.AutomaticCPAFactory;
import org.sosy_lab.cpachecker.core.defaults.DelegateAbstractDomain;
import org.sosy_lab.cpachecker.core.defaults.MergeJoinOperator;
import org.sosy_lab.cpachecker.core.defaults.MergeSepOperator;
import org.sosy_lab.cpachecker.core.defaults.StopSepOperator;
import org.sosy_lab.cpachecker.core.defaults.precision.VariableTrackingPrecision;
import org.sosy_lab.cpachecker.core.interfaces.AbstractDomain;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysisWithBAM;
import org.sosy_lab.cpachecker.core.interfaces.MergeOperator;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;
import org.sosy_lab.cpachecker.core.interfaces.StopOperator;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;

@Options(prefix = "cpa.influence")
public class InfluenceCPA implements ConfigurableProgramAnalysisWithBAM {

  public static CPAFactory factory() {
    return AutomaticCPAFactory.forType(InfluenceCPA.class);
  }

  private final CFA cfa;
  private VariableTrackingPrecision precision;
  private final ShutdownNotifier shutdownNotifier;
  private final LogManager logger;

  @Option(secure = true, description = "mergeType", values = {"sep", "join"})
  private String merge = "sep";

  private InfluenceCPA(
      CFA pCfa,
      Configuration pConfig,
      LogManager pLogger,
      ShutdownNotifier pShutdownNotifier)
      throws InvalidConfigurationException {
    pConfig.inject(this);

    cfa = pCfa;
    precision =
        VariableTrackingPrecision
            .createStaticPrecision(pConfig, cfa.getVarClassification(), getClass());

    shutdownNotifier = pShutdownNotifier;
    logger = pLogger;
  }

  @Override
  public AbstractDomain getAbstractDomain() {
    // TODO Auto-generated method stub
    return DelegateAbstractDomain.<InfluenceState>getInstance();
  }

  @Override
  public TransferRelation getTransferRelation() {
    // TODO Auto-generated method stub
    return new InfluenceTransferRelation(cfa);
  }

  @Override
  public MergeOperator getMergeOperator() {
    // TODO Auto-generated method stub
    switch (merge) {
      case "sep":
        return MergeSepOperator.getInstance();
      case "join":
        return new MergeJoinOperator(getAbstractDomain());
      default:
        throw new AssertionError("unexpected operator: " + merge);
    }
  }

  @Override
  public StopOperator getStopOperator() {
    // TODO Auto-generated method stub
    return new StopSepOperator(getAbstractDomain());
  }

  @Override
  public Precision getInitialPrecision(CFANode pNode, StateSpacePartition pPartition)
      throws InterruptedException {
    // TODO Auto-generated method stub
    return precision;
  }

  @Override
  public AbstractState getInitialState(CFANode pNode, StateSpacePartition pPartition)
      throws InterruptedException {
    // TODO Auto-generated method stub
    return InfluenceState.empty();
  }


  public CFA getCfa() {
    return cfa;
  }

  public ShutdownNotifier getShutdownNotifier() {
    return shutdownNotifier;
  }

  public LogManager getLogger() {
    return logger;
  }

  @Override
  public PrecisionAdjustment getPrecisionAdjustment() {
    // TODO Auto-generated method stub
    return new InfluencePrecisionAdjustment(logger);
  }

}
