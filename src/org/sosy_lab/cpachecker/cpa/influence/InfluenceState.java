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

import com.google.common.collect.Sets;
import java.io.Serializable;
import java.util.Set;
import org.sosy_lab.cpachecker.core.defaults.LatticeAbstractState;
import org.sosy_lab.cpachecker.exceptions.CPAException;

/**
 * This state tracks the global variables that read or write by a statement and marks whether it
 * have normal successor.
 *
 * @implNote inf = (R_g, W_g) is a pair of global read/write variable set, each set preserves the
 *           read/write global variables according to a statement.
 * @implNote If state A have normal successor B, then B will be explored first.
 */
public class InfluenceState implements LatticeAbstractState<InfluenceState>, Serializable {

  // (R_g, W_g): (read_global_set, write_global_set)
  private final GlobalVarReadWritePair influence;

  // whether the current state have normal successors.
  private boolean haveNormalSuc;

  // whether the current state have updated the haveNormalSuc;
  private boolean isUpdated;

  private InfluenceState(
      GlobalVarReadWritePair pInfluence,
      boolean pHaveNormalSuc,
      boolean pIsUpdated) {
    influence = pInfluence;
    haveNormalSuc = pHaveNormalSuc;
    isUpdated = pIsUpdated;
  }

  public GlobalVarReadWritePair getInfluence() {
    return influence;
  }

  @Override
  public int hashCode() {
    // TODO Auto-generated method stub
    return influence.hashCode();
  }

  @Override
  public boolean equals(Object pOther) {
    // TODO Auto-generated method stub
    if (this == pOther) {
      return true;
    }
    if (pOther instanceof InfluenceState) {
      InfluenceState other = (InfluenceState) pOther;
      GlobalVarReadWritePair otherInf = other.getInfluence();

      return influence.getgReadVars().equals(otherInf.getgReadVars())
          && influence.getgWriteVars().equals(otherInf.getgWriteVars());
    }
    return false;
  }

  @Override
  public String toString() {
    // TODO Auto-generated method stub
    return "[" + influence.toString() + " <-> " + haveNormalSuc + " <-> " + isUpdated + "]";
  }

  public Set<String> getReadGlobalSet() {
    return influence.getgReadVars();
  }

  public Set<String> getWriteGlobalSet() {
    return influence.getgWriteVars();
  }

  public static InfluenceState empty() {
    return new InfluenceState(new GlobalVarReadWritePair(), false, false);
  }

  public static InfluenceState
      forInfluence(
          GlobalVarReadWritePair pInfluence,
          boolean pHaveInfluence,
          boolean pIsUpdated) {
    return new InfluenceState(pInfluence, pHaveInfluence, pIsUpdated);
  }

  public boolean isHaveNormalSuccessor() {
    return haveNormalSuc;
  }

  public boolean isUpdated() {
    return isUpdated;
  }

  public void updateSucNormalInfo(boolean pHaveNormalSuc) {
    haveNormalSuc = pHaveNormalSuc;
  }

  public void markUpdated(boolean pIsUpdated) {
    isUpdated = pIsUpdated;
  }

  /**
   * If the influence pair does not contains any global variable, then the current state is a normal
   * state.
   */
  public boolean isCurStateNormal() {
    return Sets.union(influence.getgReadVars(), influence.getgWriteVars()).isEmpty();
  }

  @Override
  public InfluenceState join(InfluenceState pOther) throws CPAException, InterruptedException {
    // TODO Auto-generated method stub
    Set<String> newRg = Sets.union(this.getReadGlobalSet(), pOther.getReadGlobalSet());
    Set<String> newWg = Sets.union(this.getWriteGlobalSet(), pOther.getWriteGlobalSet());

    return new InfluenceState(
        new GlobalVarReadWritePair(newRg, newWg),
        (haveNormalSuc || pOther.isHaveNormalSuccessor()),
        false);
  }

  @Override
  public boolean isLessOrEqual(InfluenceState pOther) throws CPAException, InterruptedException {
    // TODO Auto-generated method stub
    Set<String> thisRg = this.getReadGlobalSet(), thisWg = this.getWriteGlobalSet();
    Set<String> othrRg = pOther.getReadGlobalSet(), othrWg = pOther.getWriteGlobalSet();

    if ((othrRg.size() >= thisRg.size()
        && Sets.intersection(thisRg, othrRg).equals(thisRg))
        && (othrWg.size() >= thisWg.size() && Sets.intersection(thisWg, othrWg).equals(thisWg))) {
      return true;
    }

    return false;
  }

}
