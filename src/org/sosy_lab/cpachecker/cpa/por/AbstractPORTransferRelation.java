// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.por;

import java.util.Collection;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.defaults.AbstractSingleWrapperTransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;

public abstract class AbstractPORTransferRelation
    extends AbstractSingleWrapperTransferRelation
    implements TransferRelation {

  protected final LogManager logger;
  protected final ShutdownNotifier shutdownNotifier;

  protected AbstractPORTransferRelation(
      AbstractPORCPA pCpa,
      LogManager pLogger,
      ShutdownNotifier pShutdownNotifier) {
    super(pCpa.getWrappedCpa().getTransferRelation());
    logger = pLogger;
    shutdownNotifier = pShutdownNotifier;
  }

  @Override
  public Collection<? extends AbstractState>
      getAbstractSuccessors(AbstractState pState, Precision pPrecision)
          throws CPATransferException, InterruptedException {
    return transferRelation.getAbstractSuccessors(pState, pPrecision);
  }

  @Override
  public Collection<? extends AbstractState>
      getAbstractSuccessorsForEdge(AbstractState pState, Precision pPrecision, CFAEdge pCfaEdge)
          throws CPATransferException, InterruptedException {
    // TODO Auto-generated method stub
    return null;
  }

}
