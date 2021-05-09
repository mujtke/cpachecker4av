// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.keph;

import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.defaults.AbstractCPA;
import org.sosy_lab.cpachecker.core.defaults.AutomaticCPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;

public class KEPHCPA extends AbstractCPA {

  private final LogManager logger;
  private final ShutdownNotifier shutdownNotifier;
  private final CFA cfa;

  @Override
  public AbstractState getInitialState(CFANode pNode, StateSpacePartition pPartition)
      throws InterruptedException {
    // TODO Auto-generated method stub
    return KEPHState.getInstance();
  }

  public static CPAFactory factory() {
    return AutomaticCPAFactory.forType(KEPHCPA.class);
  }

  public KEPHCPA(LogManager pLogger, ShutdownNotifier pShutdownNotifier, CFA pCfa) {
    super("sep", "sep", new KEPHTransferRelation());

    logger = pLogger;
    shutdownNotifier = pShutdownNotifier;
    cfa = pCfa;
  }

  @Override
  public PrecisionAdjustment getPrecisionAdjustment() {
    return new KEPHPrecisionAdjustment(logger, cfa);
  }

}
