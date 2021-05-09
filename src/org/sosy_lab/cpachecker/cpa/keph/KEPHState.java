// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.keph;

import java.io.Serializable;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;

public class KEPHState implements AbstractState, Serializable {

  private Integer keyEventPathHash;
  private Integer nextEdgeHash, nextEdgeCodeHash;

  public static KEPHState getInstance() {
    return new KEPHState(0, 0, 0);
  }

  public KEPHState(Integer pKeyEventPathHash, Integer pNextEdgeHash, Integer pNextEdgeCodeHash) {
    this.keyEventPathHash = pKeyEventPathHash;
    this.nextEdgeHash = pNextEdgeHash;
    this.nextEdgeCodeHash = pNextEdgeCodeHash;
  }

  public KEPHState(KEPHState pState) {
    this.keyEventPathHash = pState.keyEventPathHash;
    this.nextEdgeHash = pState.nextEdgeHash;
    this.nextEdgeCodeHash = pState.nextEdgeCodeHash;
  }

  @Override
  public int hashCode() {
    return keyEventPathHash + nextEdgeHash + nextEdgeCodeHash;
  }

  @Override
  public boolean equals(Object pObj) {
    return true;
  }

  public boolean equalsToOther(Object pObj) {
    if (pObj == null || !(pObj instanceof KEPHState)) {
      return false;
    }

    if (this == pObj) {
      return true;
    }

    KEPHState pOther = (KEPHState) pObj;
    return (this.keyEventPathHash == pOther.keyEventPathHash)
        && (this.nextEdgeHash == pOther.nextEdgeHash)
        && (this.nextEdgeCodeHash == pOther.nextEdgeCodeHash);
  }

  @Override
  public String toString() {
    return "keph: "
        + this.keyEventPathHash
        + ", "
        + this.nextEdgeHash
        + ", "
        + this.nextEdgeCodeHash;
  }

  public Integer getKeyEventPathHash() {
    return keyEventPathHash;
  }

  public void setKeyEventPathHash(Integer pKeyEventPathHash) {
    keyEventPathHash = pKeyEventPathHash;
  }

  public Integer getNextEdgeHash() {
    return nextEdgeHash;
  }

  public Integer getNextEdgeCodeHash() {
    return nextEdgeCodeHash;
  }

}
