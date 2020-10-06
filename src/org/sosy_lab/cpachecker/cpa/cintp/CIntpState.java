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
package org.sosy_lab.cpachecker.cpa.cintp;

import com.google.common.collect.Sets;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.util.predicates.AbstractionPredicate;

public class CIntpState implements AbstractState, Serializable {

  private Integer pathInstance;
  private Integer curEdgeHash;
  private Integer unsatEdgeHash;
  private Set<AbstractionPredicate> predicates;

  public static CIntpState getInstance() {
    return new CIntpState(0, 0, new HashSet<>());
  }

  public CIntpState(
      Integer pPathInstance,
      Integer pCurEdgeHash,
      Set<AbstractionPredicate> pPredicates) {
    assert pPredicates != null;
    pathInstance = pPathInstance;
    curEdgeHash = pCurEdgeHash;
    unsatEdgeHash = -1;
    predicates = pPredicates;
  }

  public CIntpState(CIntpState pState) {
    pathInstance = pState.pathInstance;
    curEdgeHash = pState.curEdgeHash;
    predicates = Sets.newHashSet(pState.predicates);
  }

  @Override
  public int hashCode() {
    return predicates.hashCode();
  }

  /**
   * Notice that this equals function is from the AbstractState interface, we unconditionally return
   * true. If in some cases that need to compare two states, please use {@code equalsToOther()}
   * function.
   */
  @Override
  public boolean equals(Object pObj) {
    return true;
    // return equalsToOther(pObj);
  }

  @Override
  public String toString() {
    return "(ph: " + pathInstance + ", eh: " + curEdgeHash + ", pred: " + predicates.toString() + ")";
  }

  public boolean equalsToOther(Object pObj) {
    if (pObj != null && pObj instanceof CIntpState) {
      CIntpState cintpOther = (CIntpState) pObj;

      if ((this.predicates.equals(cintpOther.predicates))) {
        return true;
      }
    }
    return false;
  }

  public Integer getPathInstance() {
    return pathInstance;
  }

  public Set<AbstractionPredicate> getPredicates() {
    return predicates;
  }

  public Integer getCurEdgeHash() {
    return curEdgeHash;
  }

  public Integer getUnsatEdgeHash() {
    return unsatEdgeHash;
  }

  public void setUnsatEdgeHash(Integer pUnsatEdgeHash) {
    unsatEdgeHash = pUnsatEdgeHash;
  }

  public void addPredicate(AbstractionPredicate pAbsPred) {
    predicates.add(pAbsPred);
  }

  public void addPredicateSet(Set<AbstractionPredicate> pAbsPredSet) {
    predicates.addAll(pAbsPredSet);
  }

  public boolean isPerformedCIntp() {
    return predicates.isEmpty();
  }

}
