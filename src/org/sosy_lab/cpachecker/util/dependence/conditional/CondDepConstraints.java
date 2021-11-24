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

import static com.google.common.collect.FluentIterable.from;

import com.google.common.collect.Sets;
import java.util.Set;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.util.Pair;

/**
 * This object provides the conditional dependence constraints of two nodes.
 *
 * @implNote If two nodes have no dependency relation, the generated {@link CondDepConstraints}
 *     should be null.
 */
public class CondDepConstraints {

  // the constraints that confines the dependency relation of two nodes.
  private Set<Pair<CExpression, String>> constraints;
  // if two nodes are un-conditional dependent with each other (e.g., x = 1; x = y + 1; ('x' is a
  // global variable)), we need no other information to confine the dependency relation. otherwise,
  // we need provide some extra information to determine whether the two nodes are dependent (e.g.,
  // b[i] = 1; b[j] = 2; 'b' is a global array, and we need provide a constraint 'i = j' to confine
  // this dependence relation.)
  private boolean unCondDep;
  // whether the two nodes have conflict variables.
  private boolean haveConfVars;

  public static final CondDepConstraints unCondDepConstraint = new CondDepConstraints();

  public CondDepConstraints() {
    constraints = Set.of();
    unCondDep = true;
    haveConfVars = true;
  }

  public CondDepConstraints(
      final Set<Pair<CExpression, String>> pConstraints,
      boolean pUnCondDep,
      boolean pHaveConfVars) {
    assert pConstraints != null;
    constraints = pConstraints;
    unCondDep = pUnCondDep;
    haveConfVars = pHaveConfVars;
  }

  public CondDepConstraints(final CondDepConstraints pOther) {
    assert pOther != null;
    constraints = Set.copyOf(pOther.constraints);
    unCondDep = pOther.unCondDep;
    haveConfVars = pOther.haveConfVars;
  }

  public void addConstraint(Pair<CExpression, String> pExp) {
    if(pExp != null) {
      constraints.add(pExp);
    }
  }

  public void addConstraints(Set<Pair<CExpression, String>> pExps) {
    if (pExps != null && !pExps.isEmpty()) {
      constraints.addAll(pExps);
    }
  }

  public Set<Pair<CExpression, String>> getConstraints() {
    return constraints;
  }

  public void removeConstraint(CExpression pExp) {
    if (pExp != null) {
      constraints.removeAll(from(constraints).filter(c -> c.getFirst().equals(pExp)).toSet());
    }
  }

  public void removeConstraints(Set<CExpression> pExps) {
    if (pExps != null && !pExps.isEmpty()) {
      pExps.forEach(e -> removeConstraint(e));
    }
  }

  public boolean isUnCondDep() {
    return unCondDep;
  }

  public static CondDepConstraints mergeConstraints(CondDepConstraints... pConsts) {
    Set<Pair<CExpression, String>> tmpConstraints = Set.of();

    boolean confVars = false;
    int noConstraintNumber = 0;
    for (CondDepConstraints consts : pConsts) {
      if (consts != null) {
        tmpConstraints = Sets.union(tmpConstraints, consts.constraints);
        confVars = confVars ? true : consts.haveConfVars;
      } else {
        ++noConstraintNumber;
      }
    }

    if (noConstraintNumber == pConsts.length) {
      return null;
    }
    return new CondDepConstraints(tmpConstraints, tmpConstraints.isEmpty(), confVars);
  }

  public boolean isHaveConfVars() {
    return haveConfVars;
  }

  @Override
  public int hashCode() {
    return constraints.hashCode();
  }

  @Override
  public boolean equals(Object pObj) {
    if (pObj == this) {
      return true;
    }

    if (pObj != null && pObj instanceof CondDepConstraints) {
      CondDepConstraints other = (CondDepConstraints) pObj;

      return constraints.equals(other.constraints);
    }
    return false;
  }

  @Override
  public String toString() {
    String result = "( ";
    for (Pair<CExpression, String> cst : constraints) {
      result += cst.getSecond() + " ";
    }
    result += ")";
    return result;
  }
}
