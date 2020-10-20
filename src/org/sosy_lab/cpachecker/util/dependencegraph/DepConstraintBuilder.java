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
package org.sosy_lab.cpachecker.util.dependencegraph;

import com.google.common.collect.Sets;
import java.util.HashSet;
import java.util.Set;
import org.sosy_lab.cpachecker.cfa.ast.FileLocation;
import org.sosy_lab.cpachecker.cfa.ast.c.CArraySubscriptExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression.BinaryOperator;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CPointerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.cfa.types.c.CArrayType;
import org.sosy_lab.cpachecker.cfa.types.c.CPointerType;
import org.sosy_lab.cpachecker.cfa.types.c.CSimpleType;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.dependence.conditional.CondDepConstraints;
import org.sosy_lab.cpachecker.util.dependence.conditional.EdgeVtx;
import org.sosy_lab.cpachecker.util.dependence.conditional.ExpToStringVisitor;
import org.sosy_lab.cpachecker.util.dependence.conditional.Var;

public class DepConstraintBuilder {

  private static DepConstraintBuilder builder;
  private static ExpToStringVisitor exprVisitor;

  public static DepConstraintBuilder getInstance() {
    if (builder == null) {
      builder = new DepConstraintBuilder();
      exprVisitor = ExpToStringVisitor.getInstance();
    }
    return builder;
  }

  private DepConstraintBuilder() {}

  /**
   * This function generates the conditional dependence constraints for two {@link DGNode}.
   *
   * @param pNode1 The first {@link DGNode}.
   * @param pNode2 The second {@link DGNode}.
   * @param pUseCondDepConstraints Whether consider to compute the conditional constraints, if
   *     enabled, a set of constraints will be computed.
   * @return The set of conditional dependence constraints.
   * @implNote If pUseCondDepConstraints is not enabled, we only consider whether the two {@link
   *     DGNode} have potential conflict variables according to the specific variable type (e.g.,
   *     {@link CPointerType}, {@link CArrayType}).
   */
  public CondDepConstraints buildDependenceConstraints(
      EdgeVtx pNode1, EdgeVtx pNode2, boolean pUseCondDepConstraints) {
    assert pNode1 != null && pNode2 != null;

    // we only compute the conditional dependence constraints for simple DGNodes.
    if (pNode1.isSimpleEdgeVtx() && pNode2.isSimpleEdgeVtx()) {
      // handle pointer constraint.
      CondDepConstraints pointerConstraint =
          handlePointerDepConstraints(pNode1, pNode2, pUseCondDepConstraints);
      // handle array constraint.
      CondDepConstraints arrayConstraint =
          handleArrayDepConstraints(pNode1, pNode2, pUseCondDepConstraints);
      // handle normal constraint.
      CondDepConstraints normalConstraint =
          handleNormalDepConstraints(pNode1, pNode2, pUseCondDepConstraints);

      return CondDepConstraints.mergeConstraints(
          pointerConstraint, arrayConstraint, normalConstraint);
    } else {
      // complex DGNodes could only compute the un-conditional dependence.

      // handle pointer constraint.
      CondDepConstraints pointerConstraint = handlePointerDepConstraints(pNode1, pNode2, false);
      // handle array constraint.
      CondDepConstraints arrayConstraint = handleArrayDepConstraints(pNode1, pNode2, false);
      // handle normal constraint.
      CondDepConstraints normalConstraint = handleNormalDepConstraints(pNode1, pNode2, false);

      return CondDepConstraints.mergeConstraints(
          pointerConstraint, arrayConstraint, normalConstraint);
    }
  }

  /**
   * This function handles the pointer dependence constraints of two {@link DGNode}.
   *
   * @param pNode1 The first {@link DGNode}.
   * @param pNode2 The second {@link DGNode}.
   * @return The conditional dependence constraints of these two simple {@link DGNode}.
   */
  private CondDepConstraints handlePointerDepConstraints(
      EdgeVtx pNode1, EdgeVtx pNode2, boolean pUseCondDep) {
    Class<CPointerType> pType = CPointerType.class;

    // filter out all the pointer variables.
    Set<Var> vr1 = pNode1.getReadVarsByType(pType), vw1 = pNode1.getWriteVarsByType(pType);
    Set<Var> vr2 = pNode2.getReadVarsByType(pType), vw2 = pNode2.getWriteVarsByType(pType);

    // we cannot directly intersect these sets, since we need to get potentially conflict variable
    // pair.
    Set<Pair<Var, Var>> r1w2 = pointerVarSetIntersect(vr1, vw2);
    Set<Pair<Var, Var>> w1r2 = pointerVarSetIntersect(vw1, vr2);
    Set<Pair<Var, Var>> w1w2 = pointerVarSetIntersect(vw1, vw2);
    Set<Pair<Var, Var>> confVarSet = Sets.union(r1w2, Sets.union(w1r2, w1w2));

    if (!confVarSet.isEmpty()) {
      return pUseCondDep ? handlePointer(confVarSet) : CondDepConstraints.unCondDepConstraint;
    }

    // for pointer processing, the two DGNodes are independent.
    return null;
  }

  /**
   * This function generates the pointer constraints from the give set of pointer variable pairs.
   *
   * @param pPointerSet The set of pointer variable pair (*p, *q).
   * @return The constraints constructed from the set, one pair forms one constraint (p = q).
   */
  private CondDepConstraints handlePointer(Set<Pair<Var, Var>> pPointerSet) {
    Set<Pair<CExpression, String>> ptrConstraints = new HashSet<>();

    for (Pair<Var, Var> ptrPair : pPointerSet) {
      Var lhsVar = ptrPair.getFirst(), rhsVar = ptrPair.getSecond();
      CType type = lhsVar.getVarType();
      CExpression lhs = ((CPointerExpression) (lhsVar.getExp())).getOperand();
      CExpression rhs = ((CPointerExpression) (rhsVar.getExp())).getOperand();
      String cstDescription = lhs.accept(exprVisitor) + " = " + rhs.accept(exprVisitor);

      ptrConstraints.add(
          Pair.of(
              new CBinaryExpression(
                  FileLocation.DUMMY, type, type, lhs, rhs, BinaryOperator.EQUALS),
              cstDescription));
    }

    return new CondDepConstraints(ptrConstraints, false);
  }

  /**
   * This function generates the common variable pairs of these two sets.
   *
   * @param pSet1 The first pointer variable set.
   * @param pSet2 The second pointer variable set.
   * @return The common typed pointer variable set.
   * @implNote We only check whether the two pointer could be assigned to each other, and we place
   *     the pair into the result set if true.
   */
  private Set<Pair<Var, Var>> pointerVarSetIntersect(Set<Var> pSet1, Set<Var> pSet2) {
    Set<Pair<Var, Var>> result = new HashSet<>();

    // short cut.
    if (pSet1.isEmpty() || pSet2.isEmpty()) {
      return Set.of();
    }

    // only compare the type of these two global variables.
    for (Var v1 : pSet1) {
      for (Var v2 : pSet2) {
        if (v1.getVarType().canBeAssignedFrom(v2.getVarType())) {
          result.add(Pair.of(v1, v2));
        }
      }
    }

    return result;
  }

  /**
   * This function handles the array dependence constraints of two {@link DGNode}.
   *
   * @param pNode1 The first {@link DGNode}.
   * @param pNode2 The Second {@link DGNode}.
   * @return The conditional dependence constraints of these two simple {@link DGNode}.
   */
  private CondDepConstraints handleArrayDepConstraints(
      EdgeVtx pNode1, EdgeVtx pNode2, boolean pUseCondDep) {
    Class<CArrayType> pType = CArrayType.class;

    Set<Var> vr1 = pNode1.getReadVarsByType(pType), vw1 = pNode1.getWriteVarsByType(pType);
    Set<Var> vr2 = pNode2.getReadVarsByType(pType), vw2 = pNode2.getWriteVarsByType(pType);

    // we cannot directly intersect these sets, since we need to get potentially conflict variable
    // pair.
    Set<Pair<Var, Var>> r1w2 = arrayVarSetIntersect(vr1, vw2);
    Set<Pair<Var, Var>> w1r2 = arrayVarSetIntersect(vw1, vr2);
    Set<Pair<Var, Var>> w1w2 = arrayVarSetIntersect(vw1, vw2);
    Set<Pair<Var, Var>> confVarSet = Sets.union(r1w2, Sets.union(w1r2, w1w2));

    if (!confVarSet.isEmpty()) {
      return pUseCondDep ? handleArr(confVarSet) : CondDepConstraints.unCondDepConstraint;
    }

    // for array processing, the two DGNodes are independent.
    return null;
  }

  /**
   * This function generates the array constraints from the given set of array variable pairs.
   *
   * @param pArrPairs The set of array variable pair (a[i] = a[j]).
   * @return The constraints constructed from the set, one pair forms one constraint (i = j).
   */
  private CondDepConstraints handleArr(Set<Pair<Var, Var>> pArrPairs) {
    Set<Pair<CExpression, String>> arrConstraints = new HashSet<>();

    for(Pair<Var, Var> arrPair : pArrPairs) {
      Var lhsVar = arrPair.getFirst(), rhsVar = arrPair.getSecond();
      CType type = lhsVar.getVarType();
      CExpression lhs = ((CArraySubscriptExpression) (lhsVar.getExp())).getSubscriptExpression();
      CExpression rhs = ((CArraySubscriptExpression) (rhsVar.getExp())).getSubscriptExpression();
      String cstDescription = lhs.accept(exprVisitor) + " = " + rhs.accept(exprVisitor);

      arrConstraints.add(
          Pair.of(
              new CBinaryExpression(
                  FileLocation.DUMMY, type, type, lhs, rhs, BinaryOperator.EQUALS),
              cstDescription));
    }

    return new CondDepConstraints(arrConstraints, false);
  }

  /**
   * This function generates the common variable pairs of these two sets.
   *
   * @param pSet1 The first array variable set.
   * @param pSet2 The second array variable set.
   * @return The common array variable pairs.
   * @implNote We only compare the name array variable pairs that constructed from the given two
   *     sets.
   */
  private Set<Pair<Var, Var>> arrayVarSetIntersect(Set<Var> pSet1, Set<Var> pSet2) {
    Set<Pair<Var, Var>> result = new HashSet<>();

    // short cut.
    if (pSet1.isEmpty() || pSet2.isEmpty()) {
      return Set.of();
    }

    // only compare the name of these two global variables.
    for (Var v1 : pSet1) {
      for (Var v2 : pSet2) {
        if (v1.getName().equals(v2.getName())) {
          result.add(Pair.of(v1, v2));
        }
      }
    }

    return result;
  }

  /**
   * This function handles the normal dependence constraints of two {@link DGNode}.
   *
   * @param pNode1 The first {@link DGNode}.
   * @param pNode2 The Second {@link DGNode}.
   * @return The conditional dependence constraints of these two simple {@link DGNode}.
   */
  private CondDepConstraints handleNormalDepConstraints(
      EdgeVtx pNode1, EdgeVtx pNode2, boolean pUseCondDep) {
    Class<CSimpleType> pType = CSimpleType.class;

    Set<Var> vr1 = pNode1.getReadVarsByType(pType), vw1 = pNode1.getWriteVarsByType(pType);
    Set<Var> vr2 = pNode2.getReadVarsByType(pType), vw2 = pNode2.getWriteVarsByType(pType);

    Set<Var> r1w2 = Sets.intersection(vr1, vw2);
    Set<Var> w1r2 = Sets.intersection(vw1, vr2);
    Set<Var> w1w2 = Sets.intersection(vw1, vw2);
    Set<Var> confVarSet = Sets.union(r1w2, Sets.union(w1r2, w1w2));

    if (!confVarSet.isEmpty()) {
      // for simple DGNode, there are only one potential conflict variable.
      assert confVarSet.size() == 1;
      CType type = confVarSet.iterator().next().getVarType();

      CExpressionAssignmentStatement stmt1 = getAssignmentStatement(pNode1.getEdge()),
          stmt2 = getAssignmentStatement(pNode2.getEdge());

      if (stmt1 == null && stmt2 == null) {
        // both the two edges are not assignment statements, but there is a global variable they
        // accessed.
        return CondDepConstraints.unCondDepConstraint;
      } else if (stmt1 != null && stmt2 != null) {
        return pUseCondDep
            ? handleWRWR(stmt1, stmt2, type)
            : CondDepConstraints.unCondDepConstraint;
      } else if (stmt1 == null || stmt2 == null) {
        if (stmt1 == null) {
          return pUseCondDep ? handleRDWR(stmt2, type) : CondDepConstraints.unCondDepConstraint;
        } else {
          return pUseCondDep ? handleRDWR(stmt1, type) : CondDepConstraints.unCondDepConstraint;
        }
      }
    }

    // for normal processing, the two DGNodes are independent.
    return null;
  }

  /**
   * This function handles two write assignment statements.
   *
   * @param pStmt The first write assignment statement (x = e1).
   * @param pType The assignment type of this statement.
   * @return It returns a constraint consist of a {@link CBinaryExpression} (x = e1).
   */
  private CondDepConstraints handleRDWR(CExpressionAssignmentStatement pStmt, CType pType) {
    CExpression lhs = pStmt.getLeftHandSide(), rhs = pStmt.getRightHandSide();
    CBinaryExpression condConstraint =
        new CBinaryExpression(FileLocation.DUMMY, pType, pType, lhs, rhs, BinaryOperator.EQUALS);
    String cstDescription = lhs.accept(exprVisitor) + " = " + rhs.accept(exprVisitor);

    if (lhs.equals(rhs)) {
      return CondDepConstraints.unCondDepConstraint;
    }
    return new CondDepConstraints(Set.of(Pair.of(condConstraint, cstDescription)), false);
  }

  /**
   * This function handles two write assignment statements.
   *
   * @param pStmt1 The first write assignment statement (x = e1).
   * @param pStmt2 The second write assignment statement (x = e2).
   * @param pType The assignment type of these two statements.
   * @return It returns a constraint consist of a {@link CBinaryExpression} (e1 = e2).
   */
  private CondDepConstraints handleWRWR(
      CExpressionAssignmentStatement pStmt1, CExpressionAssignmentStatement pStmt2, CType pType) {
    CExpression lhs = pStmt1.getRightHandSide(), rhs = pStmt2.getRightHandSide();
    CBinaryExpression condConstraint =
        new CBinaryExpression(FileLocation.DUMMY, pType, pType, lhs, rhs, BinaryOperator.EQUALS);
    String cstDescription = lhs.accept(exprVisitor) + " = " + rhs.accept(exprVisitor);

    if (lhs.equals(rhs)) {
      return CondDepConstraints.unCondDepConstraint;
    }
    return new CondDepConstraints(Set.of(Pair.of(condConstraint, cstDescription)), false);
  }

  /**
   * This function get the assignment statement of the given edge.
   *
   * @param pEdge The edge that need to be analyzed.
   * @return Return the assignment statement of this edge if it contains a {@link
   *     CExpressionAssignmentStatement}, and return null, otherwise.
   */
  private CExpressionAssignmentStatement getAssignmentStatement(CFAEdge pEdge) {
    if(pEdge != null) {
      if(pEdge instanceof CStatementEdge) {
        CStatement stmt = ((CStatementEdge) pEdge).getStatement();
        if(stmt instanceof CExpressionAssignmentStatement) {
          return (CExpressionAssignmentStatement) stmt;
        }
      }
    }
    return null;
  }

}
