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

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.ast.AExpression;
import org.sosy_lab.cpachecker.cfa.ast.FileLocation;
import org.sosy_lab.cpachecker.cfa.ast.c.CArraySubscriptExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CAssignment;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression.BinaryOperator;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIntegerLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CLeftHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CParameterDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CPointerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CReturnStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.cfa.types.c.CArrayType;
import org.sosy_lab.cpachecker.cfa.types.c.CPointerType;
import org.sosy_lab.cpachecker.cfa.types.c.CSimpleType;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.core.AnalysisDirection;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.Triple;
import org.sosy_lab.cpachecker.util.dependence.conditional.CondDepConstraints;
import org.sosy_lab.cpachecker.util.dependence.conditional.EdgeSharedVarAccessExtractor.VarVisitor;
import org.sosy_lab.cpachecker.util.dependence.conditional.EdgeVtx;
import org.sosy_lab.cpachecker.util.dependence.conditional.ExpToStringVisitor;
import org.sosy_lab.cpachecker.util.dependence.conditional.ReplaceVisitor;
import org.sosy_lab.cpachecker.util.dependence.conditional.Var;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormula;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormulaManagerImpl;
import org.sosy_lab.cpachecker.util.predicates.pathformula.SSAMap;
import org.sosy_lab.cpachecker.util.predicates.pathformula.pointeraliasing.PointerTargetSet;
import org.sosy_lab.cpachecker.util.predicates.smt.FormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.smt.Solver;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.BooleanFormulaManager;
import org.sosy_lab.java_smt.api.ProverEnvironment;

public class DepConstraintBuilder {

  private static boolean useSolverToCompute = false;

  private static DepConstraintBuilder builder;
  private static ExpToStringVisitor exprVisitor;

  private static CFA cfa;
  private static Configuration config;
  private static LogManager logger;
  private static ShutdownNotifier shutdownNotifier;
  private static Solver solver;
  private static ProverEnvironment prover;
  private static FormulaManagerView fmgr;
  private static PathFormulaManager pfmgr;
  private static BooleanFormulaManager bfmgr;

  private static PathFormula emptyPathFormula;

  public static void setupEnvironment(
      CFA pCfa,
      Configuration pConfig,
      LogManager pLogger,
      ShutdownNotifier pShutdownNotifier) {
    cfa = pCfa;
    config = pConfig;
    logger = pLogger;
    shutdownNotifier = pShutdownNotifier;
  }

  public static void setUseSolverToCompute(boolean pUseSolverToCompute) {
    useSolverToCompute = pUseSolverToCompute;
  }

  public static DepConstraintBuilder getInstance() {
    if (builder == null) {
      try {
        builder = new DepConstraintBuilder();
      } catch (InvalidConfigurationException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      exprVisitor = ExpToStringVisitor.getInstance();
    }
    return builder;
  }

  private DepConstraintBuilder()
      throws InvalidConfigurationException {
    solver = Solver.create(config, logger, shutdownNotifier);
    fmgr = solver.getFormulaManager();
    pfmgr =
        new PathFormulaManagerImpl(
            fmgr,
            config,
            logger,
            shutdownNotifier,
            cfa,
            AnalysisDirection.FORWARD);
    bfmgr = fmgr.getBooleanFormulaManager();
    emptyPathFormula =
        new PathFormula(
            bfmgr.makeTrue(),
            SSAMap.emptySSAMap(),
            PointerTargetSet.emptyPointerTargetSet(),
            0);
    if (useSolverToCompute) {
      prover = solver.newProverEnvironment();
    }
  }

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
          pUseCondDepConstraints
              ? handleNormalDepConstraints2(pNode1, pNode2, pUseCondDepConstraints)
              : handleNormalDepConstraints(pNode1, pNode2, pUseCondDepConstraints);

      return CondDepConstraints.mergeConstraints(
          pointerConstraint, arrayConstraint, normalConstraint);
    } else {
      // currently, complex DGNodes could only compute the un-conditional dependence.

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

    boolean haveConfVars = false;
    for (Pair<Var, Var> ptrPair : pPointerSet) {
      Var lhsVar = ptrPair.getFirst(), rhsVar = ptrPair.getSecond();
      // check whether the two pointers are the same variable.
      haveConfVars = haveConfVars ? true : lhsVar.equals(rhsVar);
      // special for pointer types.
      CExpression lhsCmpExp = (CExpression) handlePointerVarExp(lhsVar.getExp()),
          rhsCmpExp = (CExpression) handlePointerVarExp(rhsVar.getExp());
      // this step should be promised by function call pointerVarSetIntersect(...).
      assert lhsCmpExp.getExpressionType().equals(rhsCmpExp.getExpressionType());
      CType type = lhsCmpExp.getExpressionType();
      String cstDescription = lhsCmpExp.accept(exprVisitor) + " = " + rhsCmpExp.accept(exprVisitor);

      // short cut.
      if (lhsCmpExp.equals(rhsCmpExp)) {
        return CondDepConstraints.unCondDepConstraint;
      }

      ptrConstraints.add(
          Pair.of(
              new CBinaryExpression(
                  FileLocation.DUMMY, type, type, lhsCmpExp, rhsCmpExp, BinaryOperator.EQUALS),
              cstDescription));
    }

    return new CondDepConstraints(ptrConstraints, false, haveConfVars);
  }

  private AExpression handlePointerVarExp(AExpression pExp) {
    if(pExp instanceof CPointerExpression) {
      return pExp;
    } else if(pExp instanceof CArraySubscriptExpression) {
      Set<Var> vars = ((CArraySubscriptExpression) pExp).getArrayExpression().accept(VarVisitor.instance);
      assert vars.size() == 1;
      return handlePointerVarExp(vars.iterator().next().getExp());
    }
    // other cases, we do not know its real pointer expression.
    // TODO special process for this expression.
    return pExp;
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
        CType v1Type = v1.getVarType(), v2Type = v2.getVarType();
        if (v1Type.canBeAssignedFrom(v2Type) || v1Type.equals(v2Type)) {
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

    boolean haveConfVars = false;
    for(Pair<Var, Var> arrPair : pArrPairs) {
      Var lhsVar = arrPair.getFirst(), rhsVar = arrPair.getSecond();
      //
      haveConfVars = haveConfVars ? true : lhsVar.equals(rhsVar);
      // special for array types.
      CExpression lhsVarExp = (CExpression) lhsVar.getExp(),
          rhsVarExp = (CExpression) rhsVar.getExp();

      if (lhsVarExp instanceof CArraySubscriptExpression
          && rhsVarExp instanceof CArraySubscriptExpression) {
        CExpression lhsCmpExp = ((CArraySubscriptExpression) lhsVarExp).getSubscriptExpression();
        CExpression rhsCmpExp = ((CArraySubscriptExpression) rhsVarExp).getSubscriptExpression();
        // we use subscript expression type as the final type.
        CType type = lhsCmpExp.getExpressionType();
        String cstDescription =
            lhsCmpExp.accept(exprVisitor) + " = " + rhsCmpExp.accept(exprVisitor);

        // short cut.
        if (lhsCmpExp.equals(rhsCmpExp)) {
          return CondDepConstraints.unCondDepConstraint;
        }

        arrConstraints.add(
            Pair.of(
                new CBinaryExpression(
                    FileLocation.DUMMY, type, type, lhsCmpExp, rhsCmpExp, BinaryOperator.EQUALS),
                cstDescription));
      } else {
        // the two variables may have no array subscript expression, we assume that they
        // un-conditional dependent (note: they have common variable name but different expression,
        // e.g., (1) int a[10]; (2) a;).
        return CondDepConstraints.unCondDepConstraint;
      }
    }

    return new CondDepConstraints(arrConstraints, false, haveConfVars);
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
    Class<CArrayType> arrType = CArrayType.class;

    // short cut.
    if (pSet1.isEmpty() || pSet2.isEmpty()) {
      return Set.of();
    }

    // only compare the name of these two global variables.
    for (Var v1 : pSet1) {
      for (Var v2 : pSet2) {
        if (v1.getName().equals(v2.getName()) // common array base.
            && arrType.isInstance(v1.getVarType()) // both array type.
            && arrType.isInstance(v2.getVarType())) {
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
      // for simple DGNode which contains only one potential conflict variable, we special process.
      if (confVarSet.size() == 1) {
        CType type = confVarSet.iterator().next().getVarType();
        CExpressionAssignmentStatement stmt1 =
            getAssignmentStatement(pNode1.getBlockStartEdge(), confVarSet.iterator().next()),
            stmt2 =
                getAssignmentStatement(pNode2.getBlockStartEdge(), confVarSet.iterator().next());

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
      } else {
        // have multiple potential conflict variables.
        return CondDepConstraints.unCondDepConstraint;
      }
    }

    // for normal processing, the two DGNodes are independent.
    return null;
  }

  /*
   * This function handles the normal dependence constraints of two {@link DGNode}.
   * 
   * @param pNode1 The first {@link DGNode}.
   * @param pNode2 The second {@link DGNode}.
   * @return The conditional dependence constraints of these two simple {@link DGNode}.
   */
  private CondDepConstraints
      handleNormalDepConstraints2(EdgeVtx pNode1, EdgeVtx pNode2, boolean pUseCondDep) {
    Class<CSimpleType> pType = CSimpleType.class;

    Set<Var> vr1 = pNode1.getReadVarsByType(pType), vw1 = pNode1.getWriteVarsByType(pType);
    Set<Var> vr2 = pNode2.getReadVarsByType(pType), vw2 = pNode2.getWriteVarsByType(pType);

    Set<Var> r1w2 = Sets.intersection(vr1, vw2);
    Set<Var> w1r2 = Sets.intersection(vw1, vr2);
    Set<Var> w1w2 = Sets.intersection(vw1, vw2);
    Set<Var> confVarSet = Sets.union(r1w2, Sets.union(w1r2, w1w2));

    // these two nodes access the same global variable.
    if (!confVarSet.isEmpty()) {
      // for simple DGNode which contains only one potential conflict variable (can special
      // process).
      if (confVarSet.size() == 1) {
        // <assume, assign1, assign2>
        // if there is no assume edge, then the first element is null;
        // if there is an assume edge (notice: there are at most one assume edge), then the third
        // element is null.
        Triple<Pair<EdgeVtx, CExpression>, Pair<EdgeVtx, CExpressionAssignmentStatement>, Pair<EdgeVtx, CExpressionAssignmentStatement>> aa2 =
            getAssumeAndAssignStmts(pNode1, pNode2, confVarSet.iterator().next());
        //
        return computeConstraints(aa2, pUseCondDep);
      } else {
        // have multiple potential conflict variables.
        return CondDepConstraints.unCondDepConstraint;
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
    return new CondDepConstraints(Set.of(Pair.of(condConstraint, cstDescription)), false, true);
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
    return new CondDepConstraints(Set.of(Pair.of(condConstraint, cstDescription)), false, true);
  }

  /**
   * This function get the assignment statement of the given edge.
   *
   * @param pEdge The edge that need to be analyzed.
   * @param pGAVar The commonly accessed global variable.
   * @return Return the assignment statement of this edge if it contains a
   *         {@link CExpressionAssignmentStatement}, and return null, otherwise.
   */
  private CExpressionAssignmentStatement getAssignmentStatement(CFAEdge pEdge, Var pGAVar) {
    if(pEdge != null) {
      if(pEdge instanceof CStatementEdge) {
        CStatement stmt = ((CStatementEdge) pEdge).getStatement();
        if(stmt instanceof CExpressionAssignmentStatement) {
          return (CExpressionAssignmentStatement) stmt;
        }
      } else if (pEdge instanceof CDeclarationEdge) {
        CVariableDeclaration decl =
            (CVariableDeclaration) ((CDeclarationEdge) pEdge).getDeclaration();
        // only process initialized edge.
        if (decl.getInitializer() != null) {
          // create variable.
          CIdExpression var =
              new CIdExpression(decl.getFileLocation(), decl.getType(), decl.getName(), decl);
          // create assignment statement.
          CInitializerExpression initExp = (CInitializerExpression) decl.getInitializer();
          return new CExpressionAssignmentStatement(
                  decl.getFileLocation(),
                  var,
                  initExp.getExpression());
        }
      } else if (pEdge instanceof CReturnStatementEdge) {
        Optional<CReturnStatement> rtStmt = ((CReturnStatementEdge) pEdge).getRawAST();
        if (rtStmt.isPresent()) {
          Optional<CAssignment> rtAsg = rtStmt.get().asAssignment();
          return rtAsg.isPresent() ? (CExpressionAssignmentStatement) rtAsg.get() : null;
        }
      } else if (pEdge instanceof CFunctionCallEdge) {
        // get actual parameters.
        List<CExpression> fcActualParms = ((CFunctionCallEdge) pEdge).getArguments();
        if (fcActualParms != null && !fcActualParms.isEmpty()) {
          if (fcActualParms.size() == 1) {
            Optional<CFunctionCall> fcStmt = ((CFunctionCallEdge) pEdge).getRawAST();
            if (fcStmt.isPresent()) {
              // get formal parameters.
              List<CParameterDeclaration> fcFormalParms =
                  fcStmt.get().getFunctionCallExpression().getDeclaration().getParameters();

              // create tmp local variable for the formal parameter, since formal parameter cannot
              // be LHS variable.
              CParameterDeclaration fcFmParm = fcFormalParms.get(0);
              CIdExpression tmpLHSParm =
                  new CIdExpression(
                      fcFmParm.getFileLocation(),
                      fcFmParm.getType(),
                      fcFmParm.getName(),
                      fcFmParm);
              // create assignment statement for the common accessed variable.
              return new CExpressionAssignmentStatement(
                  pEdge.getFileLocation(),
                  tmpLHSParm,
                  fcActualParms.get(0));
            }
          } else {
            // since we cannot determine which formal parameter the actual parameter write to, we
            // need consider it conservatively (unconditional dependent with other statements which
            // write the same global variable).
            return null;
          }
        }
      }
    }
    return null;
  }

  /*
   * Obtain the assume and assignment triple of the given two edges. If there are no assume edge,
   * then the first element (boolean expression) is null; If there is an assume edge (at most one
   * assume edge since two assume edge are independent), then the third element (assignment
   * statement) is null.
   * 
   * @param pNode1 The first node that need to be analyzed.
   * @param pNode2 The second node that need to be analyzed.
   * @return Return a triple <assume_exp, assignemnt_stmt1, assignment_stmt2>.
   */
  private
      Triple<Pair<EdgeVtx, CExpression>, Pair<EdgeVtx, CExpressionAssignmentStatement>, Pair<EdgeVtx, CExpressionAssignmentStatement>>
      getAssumeAndAssignStmts(EdgeVtx pNode1, EdgeVtx pNode2, Var pGAVar) {
    //
    CFAEdge pEdge1 =
        pNode1.getBlockEdgeNumber() == 1 ? pNode1.getBlockStartEdge() : pNode1.getEdge(1);
    CFAEdge pEdge2 =
        pNode2.getBlockEdgeNumber() == 1 ? pNode2.getBlockStartEdge() : pNode2.getEdge(1);
    
    // handle assume edge.
    Pair<EdgeVtx, CExpression> assumeExpPair =
        pEdge1 instanceof CAssumeEdge
            ? Pair.of(pNode1, ((CAssumeEdge) pEdge1).getExpression())
            : (pEdge2 instanceof CAssumeEdge
                ? Pair.of(pNode2, ((CAssumeEdge) pEdge2).getExpression())
                : null);

    // handle assignment edge.
    CExpressionAssignmentStatement asgStmt1 = getAssignmentStatement(pEdge1, pGAVar),
        asgStmt2 = getAssignmentStatement(pEdge2, pGAVar);

    assert (asgStmt1 != null || asgStmt2 != null);
    Pair<EdgeVtx, CExpressionAssignmentStatement> asgStmt1Pair =
        asgStmt1 != null
            ? Pair.of(pNode1, asgStmt1)
            : (asgStmt2 != null ? Pair.of(pNode2, asgStmt2) : null);
    Pair<EdgeVtx, CExpressionAssignmentStatement> asgStmt2Pair =
        asgStmt1 != null && asgStmt2 != null ? Pair.of(pNode2, asgStmt2) : null;

    return Triple.of(
        assumeExpPair,
        asgStmt1Pair,
        asgStmt2Pair);
  }

  private CondDepConstraints computeConstraints(
      Triple<Pair<EdgeVtx, CExpression>, Pair<EdgeVtx, CExpressionAssignmentStatement>, Pair<EdgeVtx, CExpressionAssignmentStatement>> pAA2,
      boolean pUseCondDep) {
    Pair<EdgeVtx, CExpression> assumePair = pAA2.getFirst();
    Pair<EdgeVtx, CExpressionAssignmentStatement> asgStmt1Pair = pAA2.getSecond(),
        asgStmt2Pair = pAA2.getThird();
    
    // both the two nodes are assignment statements.
    if(assumePair == null) {
      if (asgStmt1Pair == null || asgStmt2Pair == null) {
        // we cannot get precise assignment pair of these two node (mainly caused by the
        // function-call that contains multiple parameters).
        // e.g., x is a global variable, func(int a, bool b):
        // func(x, x+1); x = x + 2; => they access the same global variable x, and
        // func(x, x+1) should output two assignments {(a = x), (b = x + 1)}.
        // However, 'EdgeVtx' of func(x, x+1) is ({x}_r, {}_w), we cannot obtain a single assignment
        // statement.
        return CondDepConstraints.unCondDepConstraint;
      }
      
      // obtain statements of the two nodes.
      CExpressionAssignmentStatement asgStmt1 = asgStmt1Pair.getSecond(), asgStmt2 = asgStmt2Pair.getSecond();
      CLeftHandSide asgStmt1LHS = asgStmt1.getLeftHandSide(),
          asgStmt2LHS = asgStmt2.getLeftHandSide();
      CExpression asgStmt1RHS = asgStmt1.getRightHandSide(),
          asgStmt2RHS = asgStmt2.getRightHandSide();
      
      // declare replacer.
      ReplaceVisitor stmt1Replacer = new ReplaceVisitor(asgStmt1LHS, asgStmt1RHS),
          stmt2Replacer = new ReplaceVisitor(asgStmt2LHS, asgStmt2RHS);

      if (asgStmt1LHS.toASTString().equals(asgStmt2LHS.toASTString())) {
        //// case: t1: y = exp_1; t2: y = exp_2
        // for execution orders 't1;t2' and 't2;t1'
        CExpression resOrder12 = asgStmt2RHS.accept(stmt1Replacer),
            resOrder21 = asgStmt1RHS.accept(stmt2Replacer);
        if (!resOrder12.toASTString().equals(resOrder21.toASTString())) {
          CBinaryExpression constraint =
              new CBinaryExpression(
                  resOrder12.getFileLocation(),
                  resOrder12.getExpressionType(),
                  resOrder21.getExpressionType(),
                  resOrder12,
                  resOrder21,
                  BinaryOperator.EQUALS);
          return checkSatOfConstraintAndReturn(
              new CondDepConstraints(
                  Set.of(Pair.of(constraint, constraint.toASTString())),
              pUseCondDep,
                  true));
        } else {
          // for the case: t1: y = x + 1; t2: y = x + 1;
          // they are independent.
          return null;
        }
      } else {
        //// case: t1: x = exp_1; t2: y = exp_2
        // for execution order 't1; t2':
        CExpression resOrder12Var1 = asgStmt1RHS,
            resOrder12Var2 = asgStmt2RHS.accept(stmt1Replacer);
        // for execution order 't2; t1':
        CExpression resOrder21Var1 = asgStmt1RHS.accept(stmt2Replacer),
            resOrder21Var2 = asgStmt2RHS;

        if (!resOrder12Var1.toASTString().equals(resOrder21Var1.toASTString())) {
          CBinaryExpression constraint =
              new CBinaryExpression(
                  resOrder12Var1.getFileLocation(),
                  resOrder12Var1.getExpressionType(),
                  resOrder21Var1.getExpressionType(),
                  resOrder12Var1,
                  resOrder21Var1,
                  BinaryOperator.EQUALS);
          return checkSatOfConstraintAndReturn(
              new CondDepConstraints(
              Set.of(Pair.of(constraint, constraint.toASTString())),
              pUseCondDep,
                  true));
        } else if (!resOrder12Var2.toASTString().equals(resOrder21Var2.toASTString())) {
          CBinaryExpression constraint =
              new CBinaryExpression(
                  resOrder12Var2.getFileLocation(),
                  resOrder12Var2.getExpressionType(),
                  resOrder21Var2.getExpressionType(),
                  resOrder12Var2,
                  resOrder21Var2,
                  BinaryOperator.EQUALS);
          return checkSatOfConstraintAndReturn(
              new CondDepConstraints(
              Set.of(Pair.of(constraint, constraint.toASTString())),
              pUseCondDep,
                  true));
        } else {
          // both of the replacements are equal, which indicates that the two transitions are
          // independent.
          return null;
        }
      }
    } else {
      // one of the two nodes are assume edge.
      assert (assumePair != null && asgStmt1Pair != null);
      Set<Var> assumeRVars = assumePair.getFirst().getgReadVars(),
          asgLHSWVars = asgStmt1Pair.getFirst().getgWriteVars();
      // the two nodes are statically dependent.
      assert (!Sets.intersection(assumeRVars, asgLHSWVars).isEmpty());

      // obtain the expression and statement from these two node.
      CExpression assExp = assumePair.getSecond();
      boolean isTrueBranch =
          ((CAssumeEdge) assumePair.getFirst().getBlockStartEdge()).getTruthAssumption();
      CExpressionAssignmentStatement asgStmt = asgStmt1Pair.getSecond();
      
      // (x > 0, x = a + b) => (a + b > 0)
      // (!(x > 0), x = x + 1) => (x + 1 <= 0)
      ReplaceVisitor replacer =
          new ReplaceVisitor(asgStmt.getLeftHandSide(), asgStmt.getRightHandSide());
      CExpression repRes = handleCMPOperator(assExp.accept(replacer), isTrueBranch);

      return checkSatOfConstraintAndReturn(
          new CondDepConstraints(
          Set.of(Pair.of(repRes, repRes.toASTString())),
          pUseCondDep,
              true));
    }
  }

  /**
   * This function checks whether the given constraints are unconditionally independent. e.g., c1: x
   * == y => x == y; c3: x == x+1 => \top
   * 
   * @param pConstraints It contains a set of constraints and have the form: c_1 /\ c_2 ... /\ c_n.
   * @return Return the original constraints if all constraints in the set are satisfiable.
   */
  private CondDepConstraints checkSatOfConstraintAndReturn(final CondDepConstraints pConstraints) {
    Preconditions.checkNotNull(pConstraints);

    // this flag is used to check whether all constraints are always satisifiable.
    // if so, then the constraint will be null (i.e., corresponding transitions are naturally
    // independent)
    boolean areAllTop = true;

    try {
      for (Pair<CExpression, String> c : pConstraints.getConstraints()) {
        BooleanFormula constraintBoolFormula =
            fmgr.simplify(pfmgr.makeAnd(emptyPathFormula, c.getFirst()).getFormula());
        if (bfmgr.isTrue(constraintBoolFormula)) {
          continue;
        } else if (bfmgr.isFalse(constraintBoolFormula)) {
          return CondDepConstraints.unCondDepConstraint;
        }
        areAllTop = false; // exist one constraint that is not always satisfiable.
        if (useSolverToCompute) {
          prover.push(constraintBoolFormula);

          if (prover.isUnsat()) {
            // case: c: x == x + 1 (this constraint cannot be satisfied)
            return CondDepConstraints.unCondDepConstraint;
          }
          // clean up prover.
          prover.pop();
        }
      }
    } catch (Exception e) {
      logger.log(
          Level.WARNING,
          "exception occured when checking the satisfiability of constraints: " + pConstraints);
      e.printStackTrace();
    }

    if (areAllTop) {
      // case: c1: 1 < 2; c2: x == x (these constraints are always satisfiable)
      return null;
    } else {
      // case: c: x == y (this constraint can be satisfied, e.g., x = 1 and y = 1)
      return pConstraints;
    }
  }

  private CondDepConstraints computeConstraints2(
      Triple<Pair<EdgeVtx, CExpression>, Pair<EdgeVtx, CExpressionAssignmentStatement>, Pair<EdgeVtx, CExpressionAssignmentStatement>> pAA2,
      boolean pUseCondDep) {
    Pair<EdgeVtx, CExpression> assumePair = pAA2.getFirst();
    Pair<EdgeVtx, CExpressionAssignmentStatement> asgStmt1Pair = pAA2.getSecond(),
        asgStmt2Pair = pAA2.getThird();

    if (assumePair == null) {
      // both the two nodes are assignment statements.
      if (asgStmt1Pair == null || asgStmt2Pair == null) {
        // we cannot get precise assignment pair of these two node (mainly caused by the
        // function-call that contains multiple parameters).
        // e.g., x is a global variable, func(int a, bool b):
        // func(x, x+1); x = x + 2; => they access the same global variable x, and
        // func(x, x+1) should output two assignments {(a = x), (b = x + 1)}.
        // However, 'EdgeVtx' of func(x, x+1) is ({x}_r, {}_w), we cannot obtain a single assignment
        // statement.
        return CondDepConstraints.unCondDepConstraint;
      }

      // obtain statements of the two nodes.
      CExpressionAssignmentStatement asgStmt1 = asgStmt1Pair.getSecond(),
          asgStmt2 = asgStmt2Pair.getSecond();
      // obtain the two nodes and read/write variables.
      EdgeVtx asgStmt1Node = asgStmt1Pair.getFirst(), asgStmt2Node = asgStmt2Pair.getFirst();
      Set<Var> asgStmt1WVars = asgStmt1Node.getgWriteVars(),
          asgStmt2WVars = asgStmt2Node.getgWriteVars();
      
      if(asgStmt1.toString().equals(asgStmt2.toString())) {
        // short cut.
        // cover case: 1. (x = a + b, x = a + b); 2. (x = x + 1, x = x + 1)
        return null;
      } else {
        // case: 3. (x = a + b, x = x + 1)  => UCD
        // case: 4. (l1 = x + 1, x = a + b) => x == (a + b)
        // case: 5. (l1 = x + 1, x = x + 1) => UCD
        // case: 6. (x = a + b, y = x) => x == (a + b)

        // determine which one is read event (notice: at most one of the two nodes is an read event).
        Pair<EdgeVtx, CExpressionAssignmentStatement> readStmtPair = asgStmt1WVars.isEmpty() ? asgStmt1Pair : (asgStmt2WVars.isEmpty() ? asgStmt2Pair : null);

        // for case 3 & 6:
        if (readStmtPair == null) {
          // both the two statements are write event.
          Set<Var> asgStmt1LHSWVar = asgStmt1Pair.getFirst().getgWriteVars(),
              asgStmt2LHSWVar = asgStmt2Pair.getFirst().getgWriteVars();

          if (Sets.intersection(asgStmt1LHSWVar, asgStmt2LHSWVar).isEmpty()) {
            // case 6.
            CExpression w1AsgStmtRHS = asgStmt1Pair.getSecond().getRightHandSide(),
                w2AsgStmtRHS = asgStmt2Pair.getSecond().getRightHandSide();

            if (asgStmt1Pair.toString().contains("__unbuffered_p0_EAX")) {
              int kk = 0;
            }

            if (isOperatorTypeEqual(w1AsgStmtRHS, w2AsgStmtRHS)) {
              CBinaryExpression constraint =
                  new CBinaryExpression(
                      w1AsgStmtRHS.getFileLocation(),
                      w1AsgStmtRHS.getExpressionType(),
                      w1AsgStmtRHS.getExpressionType(),
                      w1AsgStmtRHS,
                      w2AsgStmtRHS,
                      BinaryOperator.EQUALS);
              return new CondDepConstraints(
                  Set.of(Pair.of(constraint, constraint.toASTString())),
                  pUseCondDep,
                  true);
            } else {
              // special case: y = (x == 3); x = x + 1;
              // since the rhs of the first expression is 'bool', while the rhs of the second
              // expression is 'int'.
              return CondDepConstraints.unCondDepConstraint;
            }
          } else {
            // case 3.
            return CondDepConstraints.unCondDepConstraint;
          }
        } else {
          // determine which one is write event.
          Pair<EdgeVtx, CExpressionAssignmentStatement> writeStmtPair =
              asgStmt1Pair.equals(readStmtPair) ? asgStmt2Pair : asgStmt1Pair;
          
          // obtain the write-variable and read-variable of the write event.
          Set<Var> lhsWVars = writeStmtPair.getFirst().getgWriteVars(),
              rhsRVars = writeStmtPair.getFirst().getgReadVars();
          
          if (Sets.intersection(lhsWVars, rhsRVars).isEmpty()) {
            // for case 4:
            CExpressionAssignmentStatement wAsgStmt = writeStmtPair.getSecond();
            CExpression wAsgLHSExp = wAsgStmt.getLeftHandSide(),
                wAsgRHSExp = wAsgStmt.getRightHandSide();

            CBinaryExpression constraint =
                new CBinaryExpression(
                wAsgLHSExp.getFileLocation(),
                wAsgLHSExp.getExpressionType(),
                wAsgLHSExp.getExpressionType(),
                wAsgLHSExp,
                wAsgRHSExp,
                BinaryOperator.EQUALS);
            return new CondDepConstraints(
                Set.of(Pair.of(constraint, constraint.toASTString())),
                pUseCondDep,
                true);
          } else {
            // for case 5:
            return CondDepConstraints.unCondDepConstraint;
          }
        }
      }
    } else {
      // one of the two nodes are assume edge.
      assert(assumePair != null && asgStmt1Pair != null);
      Set<Var> assumeRVars = assumePair.getFirst().getgReadVars(),
          asgLHSWVars = asgStmt1Pair.getFirst().getgWriteVars();
      // the two nodes are statically dependent.
      assert (!Sets.intersection(assumeRVars, asgLHSWVars).isEmpty());

      // obtain the expression and statement from these two node.
      CExpression assExp = assumePair.getSecond();
      boolean isTrueBranch =
          ((CAssumeEdge) assumePair.getFirst().getBlockStartEdge()).getTruthAssumption();
      CExpressionAssignmentStatement asgStmt = asgStmt1Pair.getSecond();

      // (x > 0, x = a + b) => (a + b > 0)
      // (!(x > 0), x = x + 1) => (x + 1 <= 0)
      ReplaceVisitor replacer =
          new ReplaceVisitor(asgStmt.getLeftHandSide(), asgStmt.getRightHandSide());
      CExpression repRes = handleCMPOperator(assExp.accept(replacer), isTrueBranch);

      return new CondDepConstraints(
          Set.of(Pair.of(repRes, repRes.toASTString())),
          pUseCondDep,
          true);
    }
  }

  private boolean isOperatorTypeEqual(CExpression pExp1, CExpression pExp2) {
    boolean op1IsLogical = false, op1IsArithmetic = false, op1IsEmpty = true;
    boolean op2IsLogical = false, op2IsArithmetic = false, op2IsEmpty = true;

    if (pExp1 instanceof CBinaryExpression) {
      BinaryOperator op = ((CBinaryExpression) pExp1).getOperator();
      op1IsLogical = isLogicalOp(op);
      op1IsArithmetic = isArithmeticOp(op);
      op1IsEmpty = false;
    } else {
      op1IsLogical = false;
      op1IsArithmetic = false;
      op1IsEmpty = true;
    }

    if (pExp2 instanceof CBinaryExpression) {
      BinaryOperator op = ((CBinaryExpression) pExp2).getOperator();
      op2IsLogical = isLogicalOp(op);
      op2IsArithmetic = isArithmeticOp(op);
      op2IsEmpty = false;
    } else {
      op2IsLogical = false;
      op2IsArithmetic = false;
      op2IsEmpty = true;
    }

    return (op1IsLogical && op2IsLogical)
            || (op1IsArithmetic && op2IsArithmetic)
        || (op1IsEmpty && op2IsEmpty)
            || (op1IsArithmetic && op2IsEmpty)
            || (op1IsEmpty && op2IsArithmetic);
  }

  private boolean isArithmeticOp(BinaryOperator op) {
    switch (op) {
      case MULTIPLY:
      case DIVIDE:
      case PLUS:
      case MINUS:
        return true;
      default:
        return false;
    }
  }

  private boolean isLogicalOp(BinaryOperator op) {
    switch (op) {
      case LESS_THAN:
      case GREATER_THAN:
      case LESS_EQUAL:
      case GREATER_EQUAL:
      case BINARY_AND:
      case BINARY_XOR:
      case BINARY_OR:
      case EQUALS:
      case NOT_EQUALS:
        return true;
      default:
        return false;
    }
  }

  /*
   * Replace the comparison operator in pExp. NOTICE: Due to the expression of naive false branch is
   * the same as the true branch, we need to replace the operator of the false branch. e.g., x >= 0
   * => x < 0; !x => x == 0
   * 
   * @param pExp The expression that is needed to be replaced.
   * 
   * @param pIsTrueBranch Whether the given expression is the false branch. If not, we just return
   * the original expression; Otherwise, we replace the operator with corresponding inversed
   * operator.
   * 
   * @return The replaced operator.
   */
  private CExpression handleCMPOperator(CExpression pExp, boolean pIsTrueBranch) {
    if (pIsTrueBranch) {
      return pExp;
    } else {
      // false branch, we need to reverse the expression.
      if (pExp instanceof CBinaryExpression) {
        // e.g., x >= 0 => x < 0
        CBinaryExpression binExp = (CBinaryExpression) pExp;
        switch (binExp.getOperator()) {
          case LESS_THAN: // <
            return replaceCMPOperator(binExp, BinaryOperator.GREATER_EQUAL);
          case GREATER_THAN: // >
            return replaceCMPOperator(binExp, BinaryOperator.LESS_EQUAL);
          case LESS_EQUAL: // <=
            return replaceCMPOperator(binExp, BinaryOperator.GREATER_THAN);
          case GREATER_EQUAL: // >=
            return replaceCMPOperator(binExp, BinaryOperator.LESS_THAN);
          case EQUALS: // ==
            return replaceCMPOperator(binExp, BinaryOperator.NOT_EQUALS);
          case NOT_EQUALS: // !=
            return replaceCMPOperator(binExp, BinaryOperator.EQUALS);
          default:
            return pExp;
        }
      } else {
        // e.g., !x => x == 0
        return new CBinaryExpression(
            pExp.getFileLocation(),
            pExp.getExpressionType(),
            pExp.getExpressionType(),
            pExp,
            CIntegerLiteralExpression.ZERO,
            BinaryOperator.EQUALS);
      }
    }
  }

  /*
   * We just create a new binary expression since we cannot modify it.
   */
  private CBinaryExpression replaceCMPOperator(CBinaryExpression pExp, BinaryOperator pOp) {
    return new CBinaryExpression(
        pExp.getFileLocation(),
        pExp.getExpressionType(),
        pExp.getCalculationType(),
        pExp.getOperand1(),
        pExp.getOperand2(),
        pOp);
  }

}
