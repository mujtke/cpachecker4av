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

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.defaults.SingleEdgeTransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;

public class CIntpTransferRelation extends SingleEdgeTransferRelation {


  /**
   * This function only update the path hash, the conditional interpolants are generated in
   * precision adjustment procedure.
   *
   * @implNote The procedure of generating conditional interpolants needs ARGState.
   */
  @Override
  public Collection<? extends AbstractState>
      getAbstractSuccessorsForEdge(AbstractState pState, Precision pPrecision, CFAEdge pCfaEdge)
          throws CPATransferException, InterruptedException {
    assert pState instanceof CIntpState;
    Integer pathHash = genPathHash((CIntpState) pState, pCfaEdge);
    return Set.of(new CIntpState(pathHash, pCfaEdge.hashCode(), new HashSet<>()));
  }

  /**
   * This function generates the path hash of an edge.
   *
   * @param pState The state with historical path hash.
   * @param pCfaEdge The edge that need to perform the C-Intp.
   * @return The new path hash.
   *
   * @implNote Computation method: oldPathHash + edgeStatementHash
   */
  public int genPathHash(CIntpState pState, CFAEdge pCfaEdge) {
    return pState.getPathInstance()
        + (!pCfaEdge.getRawStatement().isEmpty()
            ? pCfaEdge.getRawStatement().hashCode()
            : pCfaEdge.hashCode());
  }


}
