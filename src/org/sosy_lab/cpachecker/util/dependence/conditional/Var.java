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
package org.sosy_lab.cpachecker.util.dependence.conditional;

import org.sosy_lab.cpachecker.cfa.ast.AExpression;
import org.sosy_lab.cpachecker.cfa.types.c.CType;

public class Var {

  private String name;
  private AExpression exp;
  private boolean isGlobal;
  private CType varType;

  public Var(String pName, AExpression pExp, CType pType, boolean pGlobal) {
    name = pName;
    exp = pExp;
    isGlobal = pGlobal;
    varType = pType;
  }

  public String getName() {
    return name;
  }

  public AExpression getExp() {
    return exp;
  }

  public boolean isGlobal() {
    return isGlobal;
  }

  public CType getVarType() {
    return varType;
  }

  @Override
  public int hashCode() {
    return exp.hashCode();
  }

  @Override
  public boolean equals(Object pObj) {
    if (this == pObj) {
      return true;
    }

    if (pObj != null && pObj instanceof Var) {
      Var other = (Var) pObj;

      // note: we do not compare the expression of these two variables, it is important for the
      // comparison two 'complex' variables (e.g., pointer, array, struct).
      //      return name == other.name && isGlobal == other.isGlobal;
      return name == other.name
          && exp.equals(other.exp)
          && isGlobal == other.isGlobal;
    }

    return false;
  }

  @Override
  public String toString() {
    return name;
  }
}
