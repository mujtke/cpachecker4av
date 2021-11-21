// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.por.bippor;

import org.sosy_lab.cpachecker.core.interfaces.AbstractState;

public class KEPHState implements AbstractState {

  private Integer keyEventPathHash;
  private boolean needRemove;

  public static KEPHState getInstance() {
    return new KEPHState(0, false);
  }

  public KEPHState(Integer pKeyEventPathHash, boolean pNeedRemove) {
    this.keyEventPathHash = pKeyEventPathHash;
    this.needRemove = pNeedRemove;
  }

  public KEPHState(final KEPHState pOther) {
    this.keyEventPathHash = pOther.keyEventPathHash;
    this.needRemove = pOther.needRemove;
  }

  public boolean isNeedRemove() {
    return needRemove;
  }

  public void setNeedRemove(boolean pNeedRemove) {
    needRemove = pNeedRemove;
  }

  public Integer getKeyEventPathHash() {
    return keyEventPathHash;
  }

  public void setKeyEventPathHash(Integer pKeyEventPathHash) {
    keyEventPathHash = pKeyEventPathHash;
  }

  @Override
  public int hashCode() {
    return keyEventPathHash + Boolean.valueOf(needRemove).hashCode();
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
        && (this.needRemove == pOther.needRemove);
  }

  @Override
  public String toString() {
    return "keph: " + this.keyEventPathHash + ", " + this.needRemove;
  }

}
