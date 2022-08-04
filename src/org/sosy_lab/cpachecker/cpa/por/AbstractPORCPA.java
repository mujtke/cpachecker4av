// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.por;

import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.core.defaults.AbstractSingleWrapperCPA;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;

@Options(prefix = "cpa.por")
public abstract class AbstractPORCPA extends AbstractSingleWrapperCPA {

  protected final LogManager logger;
  protected final ShutdownNotifier shutdownNotifier;

  protected AbstractPORCPA(
      ConfigurableProgramAnalysis pCpa,
      Configuration pConfig,
      LogManager pLogger,
      ShutdownNotifier pShutdownNotifier)
      throws InvalidConfigurationException {
    super(pCpa);
    pConfig.inject(this, AbstractPORCPA.class);

    logger = pLogger;
    shutdownNotifier = pShutdownNotifier;
  }

  @Override
  protected ConfigurableProgramAnalysis getWrappedCpa() {
    return super.getWrappedCpa();
  }

}
