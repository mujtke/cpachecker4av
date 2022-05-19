// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.datarace;

import org.sosy_lab.cpachecker.core.interfaces.AbstractQueryableState;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.exceptions.InvalidQueryException;

public class DataRaceState implements AbstractState, AbstractQueryableState {

  private boolean isDataRace;

  public DataRaceState(boolean pIsDataRace) {
    isDataRace = pIsDataRace;
  }

  public static DataRaceState getInitialInstance() {
    return new DataRaceState(false);
  }

  public boolean isDataRace() {
    return isDataRace;
  }

  public void updateDataRace() {
    isDataRace = true;
  }

  @Override
  public boolean checkProperty(String pProperty) throws InvalidQueryException {
    if (pProperty.equalsIgnoreCase("data-race")) {
      return isDataRace;
    } else {
      throw new InvalidQueryException("The Query \"" + pProperty + "\" is invalid.");
    }
  }

  @Override
  public String getCPAName() {
    return "Data-Race";
  }

  @Override
  public Object evaluateProperty(String pProperty) throws InvalidQueryException {
    return checkProperty(pProperty);
  }

}
