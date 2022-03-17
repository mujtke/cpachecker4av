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
package org.sosy_lab.cpachecker.cpa.por.cpdpor;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.ast.AAssignment;
import org.sosy_lab.cpachecker.cfa.ast.AAstNode;
import org.sosy_lab.cpachecker.cfa.ast.c.CArrayDesignator;
import org.sosy_lab.cpachecker.cfa.ast.c.CArrayRangeDesignator;
import org.sosy_lab.cpachecker.cfa.ast.c.CArraySubscriptExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CAssignment;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CCharLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CComplexCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CDesignatedInitializer;
import org.sosy_lab.cpachecker.cfa.ast.c.CDesignator;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFieldDesignator;
import org.sosy_lab.cpachecker.cfa.ast.c.CFieldReference;
import org.sosy_lab.cpachecker.cfa.ast.c.CFloatLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CImaginaryLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializer;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerList;
import org.sosy_lab.cpachecker.cfa.ast.c.CIntegerLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CParameterDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CPointerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CReturnStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CSimpleDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CStringLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.DefaultCExpressionVisitor;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionSummaryStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.exceptions.NoException;
import org.sosy_lab.cpachecker.util.Pair;

/**
 * This static extractor for edges extracts the evaluation information of an edge. e.g., a = b, b =
 * x + func(c).
 */
public class EdgeEvaluationExtractor {

  // cache elements, edges and their content never change.
  private final Map<AAstNode, Set<?>> astCache = new IdentityHashMap<>();
  private LogManager logger;

  // blacklist of extern functions for extracting the evaluation information.
  private final ImmutableSet<String> excludeFunctions =
      ImmutableSet
          .of(
              "pthread_join",
              "pthread_exit",
              "pthread_key_create",
              "longjmp",
              "siglongjmp",
              "__builtin_va_arg");

  public EdgeEvaluationExtractor(LogManager pLogger) {
    logger = pLogger;
  }

  Set<?> extractEdgeEvaluationInfo(CFAEdge pEdge) {
    switch (pEdge.getEdgeType()) {
      case BlankEdge:
        return Set.of();
      case AssumeEdge:
        return extract(((CAssumeEdge) pEdge).getExpression());
      case StatementEdge:
        // we ignore this useless edge.
        if (pEdge instanceof CFunctionSummaryStatementEdge) {
          return Set.of();
        }
        return extract(((CStatementEdge) pEdge).getStatement());
      case DeclarationEdge:
        return extract(((CDeclarationEdge) pEdge).getDeclaration());
      case ReturnStatementEdge:
        Optional<? extends AAssignment> retStat = ((CReturnStatementEdge) pEdge).asAssignment();
        if (retStat.isPresent()) {
          return extract(retStat.get());
        }
        return Set.of();
      case FunctionCallEdge:
        return ((CFunctionCallEdge) pEdge).getRawAST().isPresent()
            ? extract(((CFunctionCallEdge) pEdge).getRawAST().get())
            : Set.of();
      case FunctionReturnEdge:
        CFunctionReturnEdge funcRetEdge = (CFunctionReturnEdge) pEdge;
        CFunctionCall retFuncCall = funcRetEdge.getSummaryEdge().getExpression();
        if (retFuncCall instanceof CFunctionCallAssignmentStatement) {
          Set<?> leftEvalInfo =
              extract(((CFunctionCallAssignmentStatement) retFuncCall).getLeftHandSide());
          Set<?> rightEvalInfo = extract(funcRetEdge.getFunctionEntry().getReturnVariable().get());
          return Set.of(Pair.of(leftEvalInfo, rightEvalInfo));
        }
        return Set.of();
      default:
        throw new AssertionError("unexpected edge: " + pEdge);
    }
  }

  private <T extends AAstNode> Set<?> extract(final T ast) {
    if (ast == null) {
      return Set.of();
    }

    if (astCache.containsKey(ast)) {
      return astCache.get(ast);
    }

    final Set<?> extractedInfo = extractInfoDirect(ast);
    astCache.put(ast, extractedInfo);

    return extractedInfo;
  }

  private <T extends AAstNode> Set<?> extractMany(final List<T> astList) {
    Set<?> tmpRes = new HashSet<>();
    for (T ast : astList) {
      tmpRes = Sets.union(tmpRes, extract(ast));
    }
    return tmpRes;
  }

  // extract the information of an AST node.
  private Set<?> extractInfoDirect(AAstNode ast) {

    if (ast instanceof CRightHandSide) {

      if (ast instanceof CExpression) {
        return ((CExpression) ast).accept(EdgeEvaluationVisitor.INSTANCE);
      } else if (ast instanceof CFunctionCallExpression) {
        CFunctionCallExpression func = (CFunctionCallExpression) ast;
        return extractFunctionCallEvalInfo(func);
      }

    } else if (ast instanceof CInitializer) {

      if (ast instanceof CInitializerExpression) {
        return extract(((CInitializerExpression) ast).getExpression());
      } else if (ast instanceof CInitializerList) {
        return extractMany(((CInitializerList) ast).getInitializers());
      } else if (ast instanceof CDesignatedInitializer) {
        CDesignatedInitializer di = (CDesignatedInitializer) ast;
        return extractInfoDirect(di.getRightHandSide());
      }

    } else if (ast instanceof CSimpleDeclaration) {

      if (ast instanceof CVariableDeclaration) {
        CVariableDeclaration decl = (CVariableDeclaration) ast;

        Set<String> leftEvalInfo = Set.of(decl.getQualifiedName());
        // this declaration have initializer.
        if (decl.getInitializer() != null) {
          Set<?> rightEvalInfo = extract(decl.getInitializer());
          return Set.of(Pair.of(leftEvalInfo, rightEvalInfo));
        } else {
          // un-initialized variable, it have no evaluation information.
          return Set.of();
        }
      } else if (ast instanceof CParameterDeclaration) {
        CParameterDeclaration decl = (CParameterDeclaration) ast;
        return Set.of(decl.getQualifiedName());
      } else {
        return Set.of();
      }

    } else if (ast instanceof CStatement) {

      if(ast instanceof CFunctionCallAssignmentStatement) {
        return extractFunctionCallEvalInfo(ast);
      } else if (ast instanceof CExpressionAssignmentStatement) {
        CExpressionAssignmentStatement stat = (CExpressionAssignmentStatement) ast;

        Set<?> leftEvalInfo = extract(stat.getLeftHandSide());
        Set<?> rightEvalInfo = extract(stat.getRightHandSide());

        return Set.of(Pair.of(leftEvalInfo, rightEvalInfo));
      } else if (ast instanceof CFunctionCallStatement) {
        return extractFunctionCallEvalInfo(ast);
      } else if (ast instanceof CExpressionStatement) {
        // we only process the function call and assignment statement.
        return Set.of();
      }

    } else if (ast instanceof CReturnStatement) {
      Optional<CExpression> returnExp = ((CReturnStatement) ast).getReturnValue();
      Optional<CAssignment> returnAsg = ((CReturnStatement) ast).asAssignment();

      Set<?> leftEvalInfo = returnExp.isPresent() ? extract(returnExp.get()) : Set.of();
      Set<?> rightEvalInfo = returnAsg.isPresent() ? extract(returnAsg.get()) : Set.of();

      return Set.of(Pair.of(leftEvalInfo, rightEvalInfo));
    } else if (ast instanceof CDesignator) {

      if (ast instanceof CArrayDesignator) {
        // int a[10] = { [x + 1] = 1 } => x + 1
        return extract(((CArrayDesignator) ast).getSubscriptExpression());
      } else if (ast instanceof CArrayRangeDesignator) {
        // int a[10] = { [1 ... 4] = x } => 1 ... 4
        Set<?> floorEvalInfo = extract(((CArrayRangeDesignator) ast).getFloorExpression());
        Set<?> ceilEvalInfo = extract(((CArrayRangeDesignator) ast).getCeilExpression());

        return Sets.union(floorEvalInfo, ceilEvalInfo);
      } else if (ast instanceof CFieldDesignator) {
        // struct Foo { int a; }; struct Foo foo = { .a = 1; } => .a
        return Set.of();
      }

    }

    throw new AssertionError("unhandled ASTNode " + ast + " of " + ast.getClass());
  }

  /**
   * This function extract the evaluation information of function calls.
   *
   * @param ast The function-call statement.
   * @return The information that needed to be evaluated.
   *
   * @implNote Some functions have no definition, they are just external declaration. e.g. extern
   *           int func(int a);
   * @implNote Some functions have no formal parameters. e.g. int func();
   */
  private Set<?> extractFunctionCallEvalInfo(AAstNode ast) {
    CFunctionDeclaration funcDef =
        (ast instanceof CFunctionCall)
            ? ((CFunctionCall) ast).getFunctionCallExpression().getDeclaration()
            : (((ast instanceof CFunctionCallExpression)
                ? ((CFunctionCallExpression) ast).getDeclaration()
                : null));

    if (funcDef != null) {
      // exclude some function call.
      if (excludeFunctions.contains(funcDef.getName())) {
        return Set.of();
      }

      // get the formal parameters of this function.
      List<CParameterDeclaration> funcFormalParams = funcDef.getParameters();
      // fix the bug caused by the cloned function (for concurrent program only).
      funcFormalParams = preprocess(funcFormalParams, funcDef.getQualifiedName());
      if (funcFormalParams != null) {
        // get the actual parameters of this function.
        List<CExpression> funcActualParams =
            (ast instanceof CFunctionCallAssignmentStatement)
                ? ((CFunctionCallAssignmentStatement) ast).getRightHandSide() // CFunctionCallAssignmentStatement
                    .getParameterExpressions()
                : ((ast instanceof CFunctionCallStatement)
                    ? ((CFunctionCallStatement) ast).getFunctionCallExpression() // CFunctionCallStatement
                        .getParameterExpressions()
                    : ((ast instanceof CFunctionCallExpression) // CFunctionCallExpression
                        ? ((CFunctionCallExpression) ast).getParameterExpressions()
                        : null));

        if(funcActualParams != null) {
          // get the function name.
          String funcName = funcDef.getQualifiedName();

          // check whether these parameters are match.
          if (funcFormalParams.size() != funcActualParams.size()) {
            logger.log(
                Level.WARNING,
                "the formal/actual parameter list of function '"
                    + funcName
                    + "' are not match: formal("
                    + funcFormalParams
                    + "), actual("
                    + funcActualParams
                    + ")!");
          }

          Set<Pair<Set<?>, Set<?>>> results = new HashSet<>();
          // process every formal/actual parameters.
          for (int i = 0; i < funcFormalParams.size(); ++i) {
            // get formal/actual parameter.
            CParameterDeclaration formalParam = funcFormalParams.get(i);
            CExpression actualParam = funcActualParams.get(i);

            // generate the assignment pair.
            results.add(Pair.of(extract(formalParam), extract(actualParam)));
          }

          return results;
        }
      }
    }

    return Set.of();
  }

  /**
   * This function fix the bug of the name of formal parameters of the cloned functions. (for
   * concurrent program verification only.)
   *
   * @param pParams The formal parameters of the function pFuncName.
   * @param pFuncName The real function name of the cloned function.
   * @return The fixed parameters of the cloned function.
   * @implNote original: void FuncName_cloned_function_1(int expression); => FuncName::expression
   * @implNote now: void FuncName_cloned_function_1(int expression); =>
   *     FuncName_cloned_function_1::expression
   * @implSpec When a function is cloned, its qualified name of the formal parameters should
   *     consistent with cloned function. However, in this version of the CPAChecker, the fact is
   *     not true.
   * @implNote INTRODUCE CASE: pthread-wmm/thin000_rmo.oepc_false-unreach-call.i::5
   */
  private List<CParameterDeclaration> preprocess(
      List<CParameterDeclaration> pParams, String pFuncName) {
    List<CParameterDeclaration> results = new ArrayList<>();

    for (CParameterDeclaration param : pParams) {
      CParameterDeclaration tmpParam =
          new CParameterDeclaration(param.getFileLocation(), param.getType(), param.getName());
      tmpParam.setQualifiedName(pFuncName + "::" + tmpParam.getName());
      results.add(tmpParam);
    }

    return results;
  }

  private static class EdgeEvaluationVisitor
      extends DefaultCExpressionVisitor<Set<String>, NoException> {

    //
    final static EdgeEvaluationVisitor INSTANCE = new EdgeEvaluationVisitor();

    @Override
    public Set<String> visit(CArraySubscriptExpression pE) throws NoException {
      Set<String> arrExpEvalInfo = pE.getArrayExpression().accept(this);
      Set<String> subExpEvalInfo = pE.getSubscriptExpression().accept(this);

      return Sets.union(arrExpEvalInfo, subExpEvalInfo);
    }

    @Override
    public Set<String> visit(CBinaryExpression pE) throws NoException {
      return Sets.union(pE.getOperand1().accept(this), pE.getOperand2().accept(this));
    }

    @Override
    public Set<String> visit(CCastExpression pE) throws NoException {
      return pE.getOperand().accept(this);
    }

    @Override
    public Set<String> visit(CComplexCastExpression pE) throws NoException {
      return pE.getOperand().accept(this);
    }

    @Override
    public Set<String> visit(CFieldReference pE) throws NoException {
      return pE.getFieldOwner().accept(this);
    }

    @Override
    public Set<String> visit(CIdExpression pE) throws NoException {
      return Set.of(pE.getDeclaration().getQualifiedName());
    }

    @Override
    public Set<String> visit(CCharLiteralExpression pE) throws NoException {
      return Set.of();
    }

    @Override
    public Set<String> visit(CImaginaryLiteralExpression pE) throws NoException {
      return Set.of();
    }

    @Override
    public Set<String> visit(CFloatLiteralExpression pE) throws NoException {
      return Set.of();
    }

    @Override
    public Set<String> visit(CIntegerLiteralExpression pE) throws NoException {
      return Set.of();
    }

    @Override
    public Set<String> visit(CStringLiteralExpression pE) throws NoException {
      return Set.of();
    }

    @Override
    public Set<String> visit(CUnaryExpression pE) throws NoException {
      return pE.getOperand().accept(this);
    }

    @Override
    public Set<String> visit(CPointerExpression pE) throws NoException {
      return pE.getOperand().accept(this);
    }

    @Override
    protected Set<String> visitDefault(CExpression pExp) throws NoException {
      return Set.of();
    }

  }
}
