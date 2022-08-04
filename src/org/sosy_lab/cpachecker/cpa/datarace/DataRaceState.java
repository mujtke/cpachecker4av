// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.datarace;

import java.util.Objects;
import org.sosy_lab.cpachecker.core.interfaces.AbstractQueryableState;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.exceptions.InvalidQueryException;

public class DataRaceState implements AbstractState, AbstractQueryableState {

  private boolean parentIsDataRaceFlag;
  private boolean isDataRace;

  public DataRaceState(boolean pParentIsDataRaceFlag, boolean pIsDataRace) {
    parentIsDataRaceFlag = pParentIsDataRaceFlag;
    isDataRace = pIsDataRace;
  }

  public static DataRaceState getInitialInstance() {
    return new DataRaceState(false, false);
  }

  public boolean isDataRace() {
    return isDataRace;
  }

  public void updateDataRace() {
    isDataRace = true;
  }

  @Override
  public int hashCode() {
    return Objects.hash(isDataRace, parentIsDataRaceFlag);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    DataRaceState other = (DataRaceState) obj;
    return isDataRace == other.isDataRace && parentIsDataRaceFlag == other.parentIsDataRaceFlag;
  }

  @Override
  public String toString() {
    return "data-race: " + isDataRace;
  }

  @Override
  public boolean checkProperty(String pProperty) throws InvalidQueryException {
    if (pProperty.equalsIgnoreCase("data-race")) {
      return parentIsDataRaceFlag;
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
