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
 *
 *
 *  CPAchecker web page:
 *    http://cpachecker.sosy-lab.org
 */
package org.sosy_lab.cpachecker.cpa.icintp;

import org.sosy_lab.cpachecker.core.interfaces.AbstractState;

public class ICIntpState implements AbstractState {

  private Integer pathInst;

  public static ICIntpState getInstance() {
    return new ICIntpState(-1);
  }

  public ICIntpState(Integer pPathInst) {
    pathInst = pPathInst;
  }

  public Integer getPathInstance() {
    return pathInst;
  }

  @Override
  public int hashCode() {
    return pathInst;
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

  public boolean equalsToOther(Object pObj) {
    if (pObj != null && pObj instanceof ICIntpState) {
      ICIntpState other = (ICIntpState) pObj;

      if (this.pathInst.equals(other.pathInst)) {
        return true;
      }
    }

    return false;
  }

  @Override
  public String toString() {
    return "(path instance: " + pathInst + ")";
  }
}
