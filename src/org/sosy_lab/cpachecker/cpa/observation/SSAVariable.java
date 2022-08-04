// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.observation;

import com.google.common.base.Preconditions;
import java.util.Objects;

public class SSAVariable {

  private String var;
  private Integer subscript;

  public SSAVariable(final String pVar, final Integer pSubscript) {
    Preconditions.checkArgument(
        (pVar != null && !pVar.isEmpty() && pSubscript >= 0),
        "The given parameter for SSA Variable are invalid!");
    var = pVar;
    subscript = pSubscript;
  }

  public SSAVariable(final SSAVariable pOther) {
    Preconditions.checkArgument((pOther != null), "The given SSA Variable is invalid!");
    var = pOther.var;
    subscript = pOther.subscript;
  }

  public String getVar() {
    return var;
  }

  public Integer getSubscript() {
    return subscript;
  }

  /**
   * This function increase the subscript of the variable.
   */
  public void updateSubscript() {
    subscript = subscript + 1;
  }

  @Override
  public int hashCode() {
    // TODO Auto-generated method stub
    return Objects.hash(var, subscript);
  }

  @Override
  public boolean equals(Object pObj) {
    // TODO Auto-generated method stub
    if (this == pObj) {
      return true;
    }
    if (pObj == null || !(pObj instanceof SSAVariable)) {
      return false;
    }

    SSAVariable other = (SSAVariable) pObj;
    return (var == other.var) && subscript.equals(other.subscript);
  }

  @Override
  public String toString() {
    // TODO Auto-generated method stub
    return var + "_" + subscript;
  }

  /**
   * Check whether the current variable is an instance of pVar.
   *
   * @param pVar The variable name with no subscript.
   * @return Return true if the variable name is equals to pVar.
   */
  public boolean isAnInstanceOf(final String pVar) {
    if (pVar == null) {
      return false;
    }

    return var.equals(pVar);
  }

}
