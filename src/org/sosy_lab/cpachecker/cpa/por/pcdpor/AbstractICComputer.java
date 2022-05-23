// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.por.pcdpor;

import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.util.dependence.conditional.CondDepConstraints;

public abstract class AbstractICComputer {

  public abstract boolean computeDep(CondDepConstraints pICs, AbstractState pState);

}
