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

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.BiMap;
import com.google.common.collect.Sets;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.sosy_lab.cpachecker.cfa.ast.AAstNode;
import org.sosy_lab.cpachecker.cfa.ast.ADeclaration;
import org.sosy_lab.cpachecker.cfa.ast.AExpression;
import org.sosy_lab.cpachecker.cfa.ast.AFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.AParameterDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.AStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CArrayDesignator;
import org.sosy_lab.cpachecker.cfa.ast.c.CArrayRangeDesignator;
import org.sosy_lab.cpachecker.cfa.ast.c.CArraySubscriptExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CAssignment;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CComplexCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CDesignatedInitializer;
import org.sosy_lab.cpachecker.cfa.ast.c.CDesignator;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFieldDesignator;
import org.sosy_lab.cpachecker.cfa.ast.c.CFieldReference;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializer;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerList;
import org.sosy_lab.cpachecker.cfa.ast.c.CLeftHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CParameterDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CPointerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CReturnStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CSimpleDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.DefaultCExpressionVisitor;
import org.sosy_lab.cpachecker.cfa.model.ADeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.AReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.AStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.FunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.model.FunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.model.FunctionSummaryEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionSummaryStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.exceptions.NoException;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.dependence.DGNode;

public class EdgeSharedVarAccessExtractor {

  private static Function<Set<Var>, Set<Var>> sharedVarAccessFilter =
      ss -> from(ss).filter(s -> s.isGlobal()).toSet();
  private static Pair<Set<Var>, Set<Var>> emptyRWVarSet = Pair.of(Set.of(), Set.of());

  private final BiMap<String, String> specialBlockFuncPairs;
  private final String specialSelfBlockFunc;


  // cache elements, edges and their content never change (the structure of a mapped is <gRVars,
  // gWVars>).
  private final Map<AAstNode, Pair<Set<Var>, Set<Var>>> astCache = new IdentityHashMap<>();

  public EdgeSharedVarAccessExtractor(
      BiMap<String, String> pSpecialBlockFuncPair, String pSpecialSelfBlockFunc) {
    specialBlockFuncPairs = pSpecialBlockFuncPair;
    specialSelfBlockFunc = pSpecialSelfBlockFunc;
  }

  public DGNode extractESVAInfo(final CFAEdge pEdge) {
    switch (pEdge.getEdgeType()) {
      case BlankEdge:
        return null;
      case AssumeEdge:
        final AssumeEdge assumeEdge = (AssumeEdge) pEdge;
        return handleAssumeEdge(
            assumeEdge, assumeEdge.getExpression(), assumeEdge.getTruthAssumption());
      case StatementEdge:
        final AStatementEdge stmtEdge = (AStatementEdge) pEdge;
        return handleStatementEdge(stmtEdge, stmtEdge.getStatement());
      case DeclarationEdge:
        final ADeclarationEdge declEdge = (ADeclarationEdge) pEdge;
        return handleDeclarationEdge(declEdge, declEdge.getDeclaration());
      case ReturnStatementEdge:
        final AReturnStatementEdge retEdge = (AReturnStatementEdge) pEdge;
        return handleReturnStatementEdge(retEdge);
      case FunctionCallEdge:
        final FunctionCallEdge funCallEdge = (FunctionCallEdge) pEdge;
        final FunctionEntryNode funEntry = funCallEdge.getSuccessor();
        return handleFunctionCallEdge(
            funCallEdge,
            funCallEdge.getArguments(),
            funEntry.getFunctionParameters(),
            funEntry.getFunctionName());
      case FunctionReturnEdge:
        final FunctionReturnEdge funRetEdge = (FunctionReturnEdge) pEdge;
        final FunctionSummaryEdge funSummaryEdge = funRetEdge.getSummaryEdge();
        return handleFunctionReturnEdge(
            funRetEdge,
            funSummaryEdge,
            funSummaryEdge.getExpression(),
            pEdge.getSuccessor().getFunctionName());
      default:
        return null;
    }
  }

  /** Edge Level */
  @SuppressWarnings("unused")
  private DGNode handleAssumeEdge(
      AssumeEdge pAssumeEdge, AExpression pExpression, boolean pTruthAssumption) {
    if (pAssumeEdge instanceof CAssumeEdge) {
      // the access type of variables in assume edge are read.
      Set<Var> gRVars = sharedVarAccessFilter.apply(extract(pExpression).getFirst());
      return gRVars.isEmpty()
          ? null
          : new EdgeVtx(pAssumeEdge, List.of(pAssumeEdge), gRVars, Set.of(), true, 1);
    }

    throw new AssertionError(
        "this method is not implemented in subclass " + pAssumeEdge.getClass().getSimpleName());
  }

  private DGNode handleStatementEdge(AStatementEdge pStmtEdge, AStatement pStatement) {
    if (pStmtEdge instanceof CStatementEdge) {
      // we do not need to process the summary edges.
      if (pStmtEdge instanceof CFunctionSummaryStatementEdge) {
        return null;
      }

      Pair<Set<Var>, Set<Var>> vars = extract(pStatement);
      Set<Var> gRVars = sharedVarAccessFilter.apply(vars.getFirst()),
          gWVars = sharedVarAccessFilter.apply(vars.getSecond());

      if (pStatement instanceof CFunctionCallStatement) {
        String funcName =
            ((CFunctionCallStatement) pStatement)
                .getFunctionCallExpression()
                .getFunctionNameExpression()
                .toString();
        if (specialBlockFuncPairs.values().contains(funcName)
            || specialBlockFuncPairs.keySet().contains(funcName)
            || funcName.startsWith(specialSelfBlockFunc)) {
          return new EdgeVtx(pStmtEdge, List.of(), gRVars, Set.of(), true, 1);
        }
      }

      return (gRVars.isEmpty() && gWVars.isEmpty())
          ? null
          : new EdgeVtx(pStmtEdge, List.of(pStmtEdge), gRVars, gWVars, true, 1);
    }

    throw new AssertionError(
        "this method is not implemented in subclass " + pStmtEdge.getClass().getSimpleName());
  }

  private DGNode handleDeclarationEdge(ADeclarationEdge pDeclEdge, ADeclaration pDeclaration) {
    if (pDeclEdge instanceof CDeclarationEdge) {
      Pair<Set<Var>, Set<Var>> vars = extract(pDeclaration);
      Set<Var> gRVars = sharedVarAccessFilter.apply(vars.getFirst()),
          gWVars = sharedVarAccessFilter.apply(vars.getSecond());
      return (gRVars.isEmpty() && gWVars.isEmpty())
          ? null
          : new EdgeVtx(pDeclEdge, List.of(pDeclEdge), gRVars, gWVars, true, 1);
    }

    throw new AssertionError(
        "this method is not implemented in subclass " + pDeclEdge.getClass().getSimpleName());
  }

  private DGNode handleReturnStatementEdge(AReturnStatementEdge pRetEdge) {
    if (pRetEdge instanceof CReturnStatementEdge) {
      Optional<CAssignment> retStat = ((CReturnStatementEdge) pRetEdge).asAssignment();

      if (retStat.isPresent()) {
        // note: the lhs variable is a build-in local variable (e.g., func::__retval__).
        Set<Var> gRVars = sharedVarAccessFilter.apply(extract(retStat.get()).getFirst());
        return gRVars.isEmpty()
            ? null
            : new EdgeVtx(pRetEdge, List.of(pRetEdge), gRVars, Set.of(), true, 1);
      }

      return null;
    }

    throw new AssertionError(
        "this method is not implemented in subclass " + pRetEdge.getClass().getSimpleName());
  }

  /**
   * This function handles the function call edges.
   *
   * @param pFunCallEdge The function call edge.
   * @param pArguments The actual parameters of this function.
   * @param pFunctionParameters The formal parameters of this function.
   * @param pFunctionName The function name of the calling function.
   * @return A vertex node of a dependent graph.
   * @implNote For some special function call, like "__VERIFIER_atomic_xxx" and "pthread_mutex_xxx",
   *     this function may return {@code emptyRWVarSet}, hence, these function should be carefully
   *     process in the caller functions.
   */
  private DGNode handleFunctionCallEdge(
      FunctionCallEdge pFunCallEdge,
      List<? extends AExpression> pArguments,
      List<? extends AParameterDeclaration> pFunctionParameters,
      String pFunctionName) {
    if (pFunCallEdge instanceof CFunctionCallEdge) {
      Set<Var> gRVars = sharedVarAccessFilter.apply(extractMany(pArguments).getFirst());

      if (specialBlockFuncPairs.values().contains(pFunctionName)
          || specialBlockFuncPairs.keySet().contains(pFunctionName)
          || pFunctionName.startsWith(specialSelfBlockFunc)) {
        return new EdgeVtx(pFunCallEdge, List.of(pFunCallEdge), gRVars, Set.of(), true, 1);
      } else {
        return gRVars.isEmpty()
            ? null
            : new EdgeVtx(pFunCallEdge, List.of(pFunCallEdge), gRVars, Set.of(), true, 1);
      }
    }

    throw new AssertionError(
        "this method is not implemented in subclass " + pFunCallEdge.getClass().getSimpleName());
  }

  @SuppressWarnings("unused")
  private DGNode handleFunctionReturnEdge(
      FunctionReturnEdge pFunRetEdge,
      FunctionSummaryEdge pFunSummaryEdge,
      AFunctionCall pSummaryExpr,
      String pFunctionName) {
    if (pFunRetEdge instanceof CFunctionReturnEdge) {
      if (pSummaryExpr instanceof CFunctionCallAssignmentStatement) {
        CLeftHandSide lhsExpr = ((CFunctionCallAssignmentStatement) pSummaryExpr).getLeftHandSide();
        Set<Var> gRVars = sharedVarAccessFilter.apply(extract(lhsExpr).getFirst());
        return gRVars.isEmpty()
            ? null
            : new EdgeVtx(pFunRetEdge, List.of(pFunRetEdge), Set.of(), gRVars, true, 1);
      }

      return null;
    }

    throw new AssertionError(
        "this method is not implemented in subclass " + pFunRetEdge.getClass().getSimpleName());
  }

  /** Statement Level */
  private <T extends AAstNode> Pair<Set<Var>, Set<Var>> extract(final T ast) {
    if (ast == null) {
      return emptyRWVarSet;
    }

    if (astCache.containsKey(ast)) {
      return astCache.get(ast);
    }

    final Pair<Set<Var>, Set<Var>> vars = extractDirect(ast);
    astCache.put(ast, vars);

    return vars;
  }

  private <T extends AAstNode> Pair<Set<Var>, Set<Var>> extractMany(final List<T> astList) {
    Pair<Set<Var>, Set<Var>> tmpRes = Pair.of(Set.of(), Set.of());
    for (T ast : astList) {
      Pair<Set<Var>, Set<Var>> vars = extract(ast);
      tmpRes =
          Pair.of(
              Sets.union(tmpRes.getFirst(), vars.getFirst()),
              Sets.union(tmpRes.getSecond(), vars.getSecond()));
    }
    return tmpRes;
  }

  private Pair<Set<Var>, Set<Var>> extractDirect(final AAstNode ast) {

    if (ast instanceof CRightHandSide) {

      if (ast instanceof CExpression) {
        // note: the variables in CExpression are read access variables by default.
        return Pair.of(((CExpression) ast).accept(VarVisitor.instance), Set.of());
      } else if (ast instanceof CFunctionCallExpression) {
        return extractFunctionCall(ast);
      }

    } else if (ast instanceof CInitializer) {

      if (ast instanceof CInitializerExpression) {
        return extract(((CInitializerExpression) ast).getExpression());
      } else if (ast instanceof CInitializerList) {
        return extractMany(((CInitializerList) ast).getInitializers());
      } else if (ast instanceof CDesignatedInitializer) {
        return extract(((CDesignatedInitializer) ast).getRightHandSide());
      }

    } else if (ast instanceof CSimpleDeclaration) {

      if (ast instanceof CVariableDeclaration) {
        CVariableDeclaration decl = (CVariableDeclaration) ast;
        CIdExpression varDeclExp =
            new CIdExpression(decl.getFileLocation(), decl.getType(), decl.getName(), decl);

        CInitializer declInitializer = decl.getInitializer();
        if (declInitializer != null) {
          Set<Var> declVar = varDeclExp.accept(VarVisitor.instance);
          Pair<Set<Var>, Set<Var>> initVars = extract(declInitializer);

          return Pair.of(initVars.getFirst(), declVar); // error
        } else {
          // un-initialized variable (just a declaration), we need not extract it.
          return emptyRWVarSet;
        }
      } else {
        // we do not process other kind of declarations.
        return emptyRWVarSet;
      }

    } else if (ast instanceof CStatement) {

      if (ast instanceof CFunctionCallAssignmentStatement) {
        return extractFunctionCall(ast);
      } else if (ast instanceof CExpressionAssignmentStatement) {
        CExpressionAssignmentStatement stat = (CExpressionAssignmentStatement) ast;
        return Pair.of(
            extract(stat.getRightHandSide()).getFirst(),
            extract(stat.getLeftHandSide()).getFirst());
      } else if (ast instanceof CFunctionCallStatement) {
        return extractFunctionCall(ast);
      } else if (ast instanceof CExpressionStatement) {
        // we only process the function call and assignment statement.
        return emptyRWVarSet;
      }

    } else if (ast instanceof CReturnStatement) {
      Optional<CAssignment> retAsg = ((CReturnStatement) ast).asAssignment();

      if (retAsg.isPresent()) {
        return extract(retAsg.get());
      }
      return emptyRWVarSet;
    } else if (ast instanceof CDesignator) {

      if (ast instanceof CArrayDesignator) {
        // int a[10] = { [x + 1] = 1 }  => x + 1
        return extract(((CArrayDesignator) ast).getSubscriptExpression());
      } else if (ast instanceof CArrayRangeDesignator) {
        // int a[10] = { [1 ... x] = 2 }    =>  1 ... x
        Pair<Set<Var>, Set<Var>> floorExpVars =
            extract(((CArrayRangeDesignator) ast).getFloorExpression());
        Pair<Set<Var>, Set<Var>> ceilExpVars =
            extract(((CArrayRangeDesignator) ast).getCeilExpression());

        return Pair.of(Sets.union(floorExpVars.getFirst(), ceilExpVars.getFirst()), Set.of());
      } else if (ast instanceof CFieldDesignator) {
        // struct Foo { int a; }; struct Foo foo = { .a = 1; }  => .a
        return emptyRWVarSet;
      }
    }

    throw new AssertionError("unhandled ASTNode " + ast + " of " + ast.getClass());
  }

  private Pair<Set<Var>, Set<Var>> extractFunctionCall(AAstNode ast) {
    assert (ast != null)
        && ((ast instanceof CFunctionCallAssignmentStatement)
            || (ast instanceof CFunctionCallStatement)
            || (ast instanceof CFunctionCallExpression));

    if (ast instanceof CFunctionCallAssignmentStatement) {
      // note: only the parameters are need to be processed (read access variables), and notice
      // that, the 'assignment' operation is not performed when the function called immediately, it
      // will be process when the result is returned (i.e., processed in CReturnStatement).
      return extractFunctionCall(
          ((CFunctionCallAssignmentStatement) ast).getRightHandSide()); // CFunctionCallExpression
    } else if (ast instanceof CFunctionCallStatement) {
      // note: only the parameters are need to be processed (read access variables)
      return extractFunctionCall(
          ((CFunctionCallStatement) ast).getFunctionCallExpression()); // CFunctionCallExpression
    } else if (ast instanceof CFunctionCallExpression) {
      // note: only the parameters are need to be processed (read access variables)
      List<CExpression> funActualParams = ((CFunctionCallExpression) ast).getParameterExpressions();
      if (funActualParams != null) {
        return extractMany(funActualParams);
      }
      return emptyRWVarSet;
    }

    throw new AssertionError("unhandled ASTNode " + ast + " of " + ast.getClass());
  }

  /** Expression Level */
  public static class VarVisitor extends DefaultCExpressionVisitor<Set<Var>, NoException> {

    public static final VarVisitor instance = new VarVisitor();

    /**
     * This function returns a set of variables (array and its subscript).
     *
     * @implNote If the result set only contains one variable, the subscript should be a literal
     *     expression.
     */
    @Override
    public Set<Var> visit(CArraySubscriptExpression pE) throws NoException {
      // process array.
      Set<Var> arrVar = pE.getArrayExpression().accept(this);
      Var arr = replace(arrVar.iterator().next(), pE);
      // process subscript.
      Set<Var> subExpInfo = pE.getSubscriptExpression().accept(this);

      return Sets.union(Set.of(arr), subExpInfo);
    }

    @Override
    public Set<Var> visit(CBinaryExpression pE) throws NoException {
      return Sets.union(pE.getOperand1().accept(this), pE.getOperand2().accept(this));
    }

    @Override
    public Set<Var> visit(CCastExpression pE) throws NoException {
      return pE.getOperand().accept(this);
    }

    @Override
    public Set<Var> visit(CComplexCastExpression pE) throws NoException {
      return pE.getOperand().accept(this);
    }

    @Override
    public Set<Var> visit(CFieldReference pE) throws NoException {
      Set<Var> var = pE.getFieldOwner().accept(this);
      return var.isEmpty() ? Set.of() : Set.of(replace(var.iterator().next(), pE));
    }

    /**
     * We only process {@link CVariableDeclaration} and {@link CParameterDeclaration}, since only
     * these two {@link CIdExpression} are related to global or local variable.
     *
     * @implNote When {@link CIdExpression} is a instance of {@link CVariableDeclaration} or {@link
     *     CParameterDeclaration}, we do not process its initializer, since this procedure are
     *     performed in extract(...) function call.
     */
    @Override
    public Set<Var> visit(CIdExpression pE) throws NoException {
      CSimpleDeclaration decl = pE.getDeclaration();

      // note that the instance of CParameterDeclaration should be local variable.
      if (decl instanceof CVariableDeclaration) {
        CVariableDeclaration varDecl = (CVariableDeclaration) decl;
        return Set.of(new Var(varDecl.getQualifiedName(), pE, decl.getType(), varDecl.isGlobal()));
      } else if (decl instanceof CParameterDeclaration) {
        CParameterDeclaration paraDecl = (CParameterDeclaration) decl;
        return Set.of(new Var(paraDecl.getQualifiedName(), pE, decl.getType(), false));
      }

      return Set.of();
    }

    @Override
    public Set<Var> visit(CUnaryExpression pE) throws NoException {
      CExpression operand = pE.getOperand();

      Set<Var> vars = operand.accept(this);
      return vars.isEmpty() ? Set.of() : Set.of(replace(vars.iterator().next(), pE));
    }

    @Override
    public Set<Var> visit(CPointerExpression pE) throws NoException {
      //      return pE.getOperand().accept(this);
      Set<Var> var = pE.getOperand().accept(this);
      return var.isEmpty() ? Set.of() : Set.of(replace(var.iterator().next(), pE));
    }

    @Override
    protected Set<Var> visitDefault(CExpression pExp) throws NoException {
      return Set.of();
    }

    private Var replace(Var pVar, CExpression pExp) {
      assert pVar != null && pExp != null;
      return new Var(pVar.getName(), pExp, pVar.getVarType(), pVar.isGlobal());
    }

  }
}
