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
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.types.c.CArrayType;
import org.sosy_lab.cpachecker.cfa.types.c.CPointerType;
import org.sosy_lab.cpachecker.util.dependence.DGNode;

public class EdgeVtx implements DGNode {

  public static final BiFunction<Set<Var>, String, Set<Var>> sharedVarNameFilter =
      (vv, name) -> from(vv).filter(v -> v.getName().equals(name)).toSet();

  private final CFAEdge blockStartEdge;
  private final List<CFAEdge> blockEdges;
  private final Set<Var> gReadVars;
  private final Set<Var> gWriteVars;
  private final boolean simpleEdgeVtx;
  private final boolean containNonDetVar;
  private int blockEdgeNumber;

  public EdgeVtx(
      final CFAEdge pEdge,
      final List<CFAEdge> pEdges,
      final Set<Var> pGRVars,
      final Set<Var> pGWVars,
      boolean pSimpleEdgeVtx,
      boolean pContainNonDetVar,
      int pBlockEdgeNumber) {
    assert pGRVars != null && pGWVars != null;

    blockStartEdge = pEdge;
    blockEdges = pEdges;
    gReadVars = Set.copyOf(pGRVars);
    gWriteVars = Set.copyOf(pGWVars);
    simpleEdgeVtx = pSimpleEdgeVtx;
    containNonDetVar = pContainNonDetVar;
    blockEdgeNumber = pBlockEdgeNumber;
  }

  public EdgeVtx(final EdgeVtx pVtxOther) {
    assert pVtxOther != null;

    blockStartEdge = pVtxOther.blockStartEdge;
    blockEdges = List.copyOf(pVtxOther.blockEdges);
    gReadVars = Set.copyOf(pVtxOther.gReadVars);
    gWriteVars = Set.copyOf(pVtxOther.gWriteVars);
    simpleEdgeVtx = pVtxOther.simpleEdgeVtx;
    containNonDetVar = pVtxOther.containNonDetVar;
    blockEdgeNumber = pVtxOther.blockEdgeNumber;
  }

  public int getBlockSize() {
    return blockEdges.size();
  }

  public CFAEdge getBlockStartEdge() {
    return blockStartEdge;
  }

  public List<CFAEdge> getBlockEdges() {
    return blockEdges;
  }

  public CFAEdge getEdge(int index) {
    if (index < blockEdges.size()) {
      return blockEdges.get(index);
    }
    return null;
  }

  public Set<Var> getgReadVars() {
    return gReadVars;
  }

  public Set<Var> getgWriteVars() {
    return gWriteVars;
  }

  public boolean isSimpleEdgeVtx() {
    return simpleEdgeVtx;
  }

  public boolean isContainNonDetVar() {
    return containNonDetVar;
  }

  public boolean isPureWriteVtx() {
    return gReadVars.isEmpty() && !gWriteVars.isEmpty();
  }

  public boolean isPureReadVtx() {
    return !gReadVars.isEmpty() && gWriteVars.isEmpty();
  }

  public void setBlockEdgeNumber(int pBlockEdgeNumber) {
    blockEdgeNumber = pBlockEdgeNumber;
  }

  public int getBlockEdgeNumber() {
    return blockEdgeNumber;
  }

  public EdgeVtx mergeGlobalRWVarsOnly(EdgeVtx pOther) {
    assert pOther != null;

    Set<Var> tmpGRVars = Sets.union(gReadVars, pOther.gReadVars),
        tmpGWVars = Sets.union(gWriteVars, pOther.gWriteVars);
    return new EdgeVtx(
        blockStartEdge,
        blockEdges,
        tmpGRVars,
        tmpGWVars,
        simpleEdgeVtx,
        (this.containNonDetVar || pOther.containNonDetVar),
        blockEdgeNumber);
  }

  /**
   * Get all the read variables by type.
   *
   * @param pType The target type.
   * @return The set of read variables with type pType.
   * @implNote We need to process the two special cases of {@link CPointerType} and {@link
   *     CArrayType}.
   */
  public Set<Var> getReadVarsByType(Class<?> pType) {
    return from(gReadVars).filter(v -> pType.isInstance(v.getVarType())).toSet();
  }

  public Set<Var> getWriteVarsByType(Class<?> pType) {
    return from(gWriteVars).filter(v -> pType.isInstance(v.getVarType())).toSet();
  }

  public Set<Var> getReadVarsByName(String pName) {
    return sharedVarNameFilter.apply(gReadVars, pName);
  }

  public Set<Var> getWriteVarsByName(String pName) {
    return sharedVarNameFilter.apply(gWriteVars, pName);
  }

  @Override
  public int hashCode() {
    String readHash = "r" + gReadVars.hashCode(), writeHash = "w" + gWriteVars.hashCode();
    return blockStartEdge.hashCode() + readHash.hashCode() + writeHash.hashCode();
  }

  @Override
  public boolean equals(Object pObj) {
    if (pObj == this) {
      return true;
    }

    if (pObj != null && pObj instanceof EdgeVtx) {
      EdgeVtx other = (EdgeVtx) pObj;

      return blockStartEdge == other.blockStartEdge
          && gReadVars.equals(other.gReadVars)
          && gWriteVars.equals(other.gWriteVars);
    }

    return false;
  }

  @Override
  public String toString() {

    String result =
        "[ "
            + blockStartEdge.getPredecessor().getFunctionName()
            + ": \""
            + blockStartEdge.getCode()
            + "\", r( ";

    for (Var v : gReadVars) {
      result += v + " ";
    }
    result += "), w( ";
    for (Var v : gWriteVars) {
      result += v + " ";
    }
    result += ") ]";

    return result;
  }

}
