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
package org.sosy_lab.cpachecker.cpa.locations;

import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.logging.Level;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.defaults.AbstractCPA;
import org.sosy_lab.cpachecker.core.defaults.AutomaticCPAFactory;
import org.sosy_lab.cpachecker.core.defaults.SingletonPrecision;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysisWithBAM;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;
import org.sosy_lab.cpachecker.core.interfaces.pcc.ProofChecker.ProofCheckerCPA;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;

public class LocationsCPA extends AbstractCPA
    implements ConfigurableProgramAnalysisWithBAM, ProofCheckerCPA {

  public static CPAFactory factory() {
    return AutomaticCPAFactory.forType(LocationsCPA.class);
  }

  public static LocationsCPA create(Configuration pConfig, LogManager pLogger, CFA pCfa)
      throws InvalidConfigurationException {
    return new LocationsCPA(pConfig, pLogger, pCfa);
  }

  public LocationsCPA(Configuration pConfig, LogManager pLogger, CFA pCfa)
      throws InvalidConfigurationException {
    super("sep", "sep", new LocationsTransferRelation(pConfig, pCfa));
    pLogger.log(
        Level.INFO,
        "When verifying concurrent programs, please keep the parameters of LocationsCPA consistent with those of ThreadingCPA!");
  }

  @Override
  public AbstractState getInitialState(CFANode pLoc, StateSpacePartition pPartition)
      throws InterruptedException {
    return ((LocationsTransferRelation) getTransferRelation()).getInitialState(pLoc);
  }

  @Override
  public boolean areAbstractSuccessors(
      AbstractState pState,
      CFAEdge pCfaEdge,
      Collection<? extends AbstractState> pSuccessors)
      throws CPATransferException, InterruptedException {
    ImmutableSet<? extends AbstractState> successors = ImmutableSet.copyOf(pSuccessors);
    ImmutableSet<? extends AbstractState> actualSuccessors =
        ImmutableSet.copyOf(
            getTransferRelation().getAbstractSuccessorsForEdge(
                pState,
                SingletonPrecision.getInstance(),
                pCfaEdge));
    return successors.equals(actualSuccessors);
  }

}
