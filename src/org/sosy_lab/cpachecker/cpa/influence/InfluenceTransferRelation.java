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
package org.sosy_lab.cpachecker.cpa.influence;

import com.google.common.collect.ImmutableSet;
import de.uni_freiburg.informatik.ultimate.smtinterpol.util.ArrayQueue;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.ast.ADeclaration;
import org.sosy_lab.cpachecker.cfa.ast.AExpression;
import org.sosy_lab.cpachecker.cfa.ast.AFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.AParameterDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.AStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CAssignment;
import org.sosy_lab.cpachecker.cfa.ast.c.CDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializer;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CLeftHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CParameterDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.model.ADeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.AReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.AStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.BlankEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.model.FunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.model.FunctionSummaryEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionSummaryEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.core.defaults.ForwardingTransferRelation;
import org.sosy_lab.cpachecker.core.defaults.precision.VariableTrackingPrecision;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.UnsupportedCodeException;
import org.sosy_lab.cpachecker.util.variableclassification.VariableClassification;


public class InfluenceTransferRelation
    extends ForwardingTransferRelation<InfluenceState, InfluenceState, VariableTrackingPrecision> {

  private final VariableClassification varClass;

  private Map<Integer, GlobalVarReadWritePair> edgeRWVarCache;

  public InfluenceTransferRelation(CFA cfa) {
    assert cfa.getVarClassification().isPresent();
    varClass = cfa.getVarClassification().orElseThrow();

    try {
      edgeRWVarCache = buildEdgeRWVarCache(cfa);
    } catch (UnsupportedCodeException e) {
      e.printStackTrace();
    }
  }

  /**
   * This function builds the read/write variable cache.
   *
   * @throws UnsupportedCodeException
   */
  private Map<Integer, GlobalVarReadWritePair> buildEdgeRWVarCache(final CFA cfa)
      throws UnsupportedCodeException {
    assert cfa != null : "the given cfa is null!";

    Map<Integer, GlobalVarReadWritePair> cache = new HashMap<>();

    // get entry points of all the functions.
    Iterator<FunctionEntryNode> funcIter = cfa.getAllFunctionHeads().iterator();
    Set<Integer> processedEdge = new HashSet<>();
    // iterate all the functions.
    while (funcIter.hasNext()) {
      // function entry point & the edges that need to be processed.
      FunctionEntryNode func = funcIter.next();
      Queue<CFAEdge> edgeQueue = new ArrayQueue<>();

      // enter the first edge.
      edgeQueue.add(func.getLeavingEdge(0));
      while (!edgeQueue.isEmpty()) {
        // get the information of an edge.
        CFAEdge edge = edgeQueue.remove();
        Integer edgeHash = edge.hashCode();

        // check whether this edge have been processed.
        if (!processedEdge.contains(edgeHash)) {
          cache.put(edgeHash, extractEgdeRWVarInfo(edge));
          processedEdge.add(edgeHash);

          CFANode edgeSucNode = edge.getSuccessor();
          for (int i = 0; i < edgeSucNode.getNumLeavingEdges(); ++i) {
            edgeQueue.add(edgeSucNode.getLeavingEdge(i));
          }
        }
      }
    }

    return cache;
  }

  private GlobalVarReadWritePair extractEgdeRWVarInfo(final CFAEdge edge)
      throws UnsupportedCodeException {
    assert edge != null : "the given edge is null!";

    switch (edge.getEdgeType()) {
      case BlankEdge:
        return extractBlankEdgeRWVar((BlankEdge) edge);
      case AssumeEdge:
        final AssumeEdge assumption = (AssumeEdge) edge;
        return extractAssumeEdgeRWVar(
            assumption,
            assumption.getExpression(),
            assumption.getTruthAssumption());
      case StatementEdge:
        final AStatementEdge statementEdge = (AStatementEdge) edge;
        return extractStatementEdgeRWVar(statementEdge, statementEdge.getStatement());
      case DeclarationEdge:
        final ADeclarationEdge declarationEdge = (ADeclarationEdge) edge;
        return extractDeclarationEdgeRWVar(declarationEdge, declarationEdge.getDeclaration());
      case ReturnStatementEdge:
        final AReturnStatementEdge returnEdge = (AReturnStatementEdge) edge;
        return extractReturnStatementEdgeRWVar(returnEdge);
      case FunctionCallEdge:
        final FunctionCallEdge fnkCall = (FunctionCallEdge) edge;
        final FunctionEntryNode succ = fnkCall.getSuccessor();
        final String calledFunctionName = succ.getFunctionName();
        return extractFunctionCallEdgeRWVar(
            fnkCall,
            fnkCall.getArguments(),
            succ.getFunctionParameters(),
            calledFunctionName);
      case FunctionReturnEdge:
        final String callerFunctionName = edge.getSuccessor().getFunctionName();
        final FunctionReturnEdge fnkReturnEdge = (FunctionReturnEdge) edge;
        final FunctionSummaryEdge summaryEdge = fnkReturnEdge.getSummaryEdge();
        return extractFunctionReturnEdgeRWVar(
            fnkReturnEdge,
            summaryEdge,
            summaryEdge.getExpression(),
            callerFunctionName);
      default:
        return GlobalVarReadWritePair.empty();
    }
  }

  protected GlobalVarReadWritePair extractBlankEdgeRWVar(final BlankEdge edge) {
    return GlobalVarReadWritePair.empty();
  }

  protected GlobalVarReadWritePair extractAssumeEdgeRWVar(
      AssumeEdge pCfaEdge,
      AExpression pExpression,
      boolean pTruthAssumption)
      throws UnsupportedCodeException {
    if (pCfaEdge instanceof CAssumeEdge) {
      Set<String> gRVars =
          ((CExpression) pExpression).accept(new InfluenceVarCollectCExpressionVisitor(precision));
      return new GlobalVarReadWritePair(gRVars, ImmutableSet.of());
    }
    // java code not implemented.

    return GlobalVarReadWritePair.empty();
  }

  protected GlobalVarReadWritePair
      extractStatementEdgeRWVar(AStatementEdge pCfaEdge, AStatement pStatement)
          throws UnsupportedCodeException {
    if (pCfaEdge instanceof CStatementEdge) {
      if (pStatement instanceof CAssignment) {
        // normal assignment, "a = ..."
        return handleAssignment((CStatementEdge) pCfaEdge, (CAssignment) pStatement);
      } else if (pStatement instanceof CFunctionCallStatement) {
        // call of external function, "func(...)" without assignment.
        CFunctionCallExpression funcExp =
            ((CFunctionCallStatement) pStatement).getFunctionCallExpression();

        return handleExternalFunctionCall(
            (CStatementEdge) pCfaEdge,
            funcExp,
            funcExp.getParameterExpressions());
      }
    }

    return GlobalVarReadWritePair.empty();
  }

  protected GlobalVarReadWritePair
      extractDeclarationEdgeRWVar(ADeclarationEdge cfaEdge, ADeclaration decl)
          throws UnsupportedCodeException {
    if (decl instanceof CVariableDeclaration) {
      CVariableDeclaration vdecl = (CVariableDeclaration) decl;

      CInitializer initializer = vdecl.getInitializer();
      CExpression init = null;
      if (initializer instanceof CInitializerExpression) {
        init = ((CInitializerExpression) initializer).getExpression();
      }

      // process left hand side of declaration.
      Set<String> lhsGWVar = new HashSet<>();
      String lhsVarName = vdecl.getQualifiedName();
      if (!lhsVarName.contains("::")) {
        // the declared variable is a global variable.
        lhsGWVar.add(lhsVarName);
      }

      // process right hand side of declaration.
      Set<String> rhsGRVars = new HashSet<>();
      if (init != null) {
        rhsGRVars.addAll(init.accept(new InfluenceVarCollectCExpressionVisitor(precision)));
      }

      return new GlobalVarReadWritePair(rhsGRVars, lhsGWVar);
    }

    return GlobalVarReadWritePair.empty();
  }

  protected GlobalVarReadWritePair extractReturnStatementEdgeRWVar(AReturnStatementEdge cfaEdge)
      throws UnsupportedCodeException {
    if (cfaEdge instanceof CReturnStatementEdge) {
      // the returned value is evaluated by an expression.
      if (((CReturnStatementEdge) cfaEdge).getExpression().isPresent()) {
        CExpression rhsExp = ((CReturnStatementEdge) cfaEdge).getExpression().get();
        Set<String> rhsGRVars = rhsExp.accept(new InfluenceVarCollectCExpressionVisitor(precision));

        return new GlobalVarReadWritePair(rhsGRVars, ImmutableSet.of());
      }
    }

    return GlobalVarReadWritePair.empty();
  }

  @SuppressWarnings("unchecked")
  protected GlobalVarReadWritePair extractFunctionCallEdgeRWVar(
      FunctionCallEdge cfaEdge,
      List<? extends AExpression> arguments,
      List<? extends AParameterDeclaration> parameters,
      String calledFunctionName)
      throws UnsupportedCodeException {
    if(cfaEdge instanceof CFunctionCallEdge) {
      // TODO Auto-generated method stub
      Set<String> gRVars = new HashSet<>();

      for (final CExpression arg : (List<? extends CExpression>) arguments) {
        gRVars.addAll(arg.accept(new InfluenceVarCollectCExpressionVisitor(precision)));
      }

      //
      if (calledFunctionName.contains("__VERIFIER_atomic")) {
        gRVars.add(calledFunctionName);
      }

      return new GlobalVarReadWritePair(gRVars, ImmutableSet.of());
    }

    return GlobalVarReadWritePair.empty();
  }

  protected GlobalVarReadWritePair
      extractFunctionReturnEdgeRWVar(
          FunctionReturnEdge cfaEdge,
          FunctionSummaryEdge fnkCall,
          AFunctionCall summaryExpr,
          String callerFunctionName)
          throws UnsupportedCodeException {
    if (summaryExpr instanceof CFunctionCallAssignmentStatement) {
      CLeftHandSide lhs = ((CFunctionCallAssignmentStatement) summaryExpr).getLeftHandSide();
      Set<String> lhsGWVars = lhs.accept(new InfluenceVarCollectCExpressionVisitor(precision));

      return new GlobalVarReadWritePair(ImmutableSet.of(), lhsGWVars);
    }

    return GlobalVarReadWritePair.empty();
  }

  protected GlobalVarReadWritePair handleAssignment(CStatementEdge pCfaEdge, CAssignment pStatement)
          throws UnsupportedCodeException {
    CExpression lhs = pStatement.getLeftHandSide();
    Set<String> lhsGWVars = lhs.accept(new InfluenceVarCollectCExpressionVisitor(precision));

    CRightHandSide rhs = pStatement.getRightHandSide();
    if (rhs instanceof CExpression) {
      // right hand side is a normal statement.
      final CExpression rhsExp = (CExpression) rhs;
      Set<String> rhsGRVars = rhsExp.accept(new InfluenceVarCollectCExpressionVisitor(precision));

      return new GlobalVarReadWritePair(rhsGRVars, lhsGWVars);
    } else if (rhs instanceof CFunctionCallExpression) {
      CFunctionCallExpression rhsExp = (CFunctionCallExpression) rhs;

      // get the global variable set that read by the function call.
      GlobalVarReadWritePair rhsRWVars =
          handleExternalFunctionCall(pCfaEdge, rhsExp, rhsExp.getParameterExpressions());
      Set<String> rhsGRVars = rhsRWVars.getgReadVars();

      return new GlobalVarReadWritePair(rhsGRVars, lhsGWVars);
    } else {
      throw new AssertionError("unhandled assignment: " + pCfaEdge.getRawStatement());
    }
  }

  /**
   * This function collects all the global variables in formal parameters.
   *
   * @implNote function call only read global variables.
   */
  protected GlobalVarReadWritePair handleExternalFunctionCall(
      CStatementEdge pCfaEdge,
      final CFunctionCallExpression pFuncExp,
      final List<CExpression> params)
      throws UnsupportedCodeException {
    Set<String> gRVars = new HashSet<>();

    for (final CExpression param : params) {
      gRVars.addAll(param.accept(new InfluenceVarCollectCExpressionVisitor(precision)));
    }

    // get function name.
    String funcName = pFuncExp.getFunctionNameExpression().toQualifiedASTString();

    if (funcName.contains("__VERIFIER_atomic")) {
      gRVars.add(funcName);
    }

    return new GlobalVarReadWritePair(gRVars, ImmutableSet.of());
  }



  /**
   * This function handles assumption like "if(x + y > 1)".
   *
   * @implNote All the global variables in this expression is collected into the global read set.
   */
  @Override
  protected @Nullable InfluenceState
      handleAssumption(CAssumeEdge pCfaEdge, CExpression pExpression, boolean pTruthAssumption)
          throws CPATransferException, InterruptedException {
    return InfluenceState.forInfluence(edgeRWVarCache.get(pCfaEdge.hashCode()), false, false);
  }

  /**
   * This function handles functioncalls like "f(x)", that calls "f(int a)". Therefore each arg
   * ("x") is collected if it is a global variable.
   */
  @Override
  protected InfluenceState handleFunctionCallEdge(
      CFunctionCallEdge pCfaEdge,
      List<CExpression> pArguments,
      List<CParameterDeclaration> pParameters,
      String pCalledFunctionName)
      throws CPATransferException {
    return InfluenceState.forInfluence(edgeRWVarCache.get(pCfaEdge.hashCode()), false, false);
  }

  /**
   * This function handles functionReturns like "y=f(x)".
   *
   * @implNote This function only process the left side of CFunctionCall (e.g., the variable 'y').
   */
  @Override
  protected InfluenceState handleFunctionReturnEdge(
      CFunctionReturnEdge pCfaEdge,
      CFunctionSummaryEdge pFnkCall,
      CFunctionCall pSummaryExpr,
      String pCallerFunctionName)
      throws CPATransferException {
    return InfluenceState.forInfluence(edgeRWVarCache.get(pCfaEdge.hashCode()), false, false);
  }

  /**
   * This function handles declarations like "int a = 0;" and "int b = !a;"
   */
  @Override
  protected InfluenceState handleDeclarationEdge(CDeclarationEdge pCfaEdge, CDeclaration pDecl)
      throws CPATransferException {
    return InfluenceState.forInfluence(edgeRWVarCache.get(pCfaEdge.hashCode()), false, false);
  }


  /**
   * This function handles statements like "a = 0;" and "b != a;" and calls of external functions.
   */
  @Override
  protected InfluenceState handleStatementEdge(CStatementEdge pCfaEdge, CStatement pStatement)
      throws CPATransferException {
    return InfluenceState.forInfluence(edgeRWVarCache.get(pCfaEdge.hashCode()), false, false);
  }

  /**
   * This function handles functionStatements like "return (x)".
   *
   * @implNote This function only process the right hand side of the return statement (e.g., x).
   */
  @Override
  protected InfluenceState handleReturnStatementEdge(CReturnStatementEdge pCfaEdge)
      throws CPATransferException {
    return InfluenceState.forInfluence(edgeRWVarCache.get(pCfaEdge.hashCode()), false, false);
  }

  @Override
  protected InfluenceState handleBlankEdge(BlankEdge pCfaEdge) throws CPATransferException {
    return InfluenceState.forInfluence(edgeRWVarCache.get(pCfaEdge.hashCode()), false, false);
  }


  @Override
  protected Collection<InfluenceState>
      postProcessing(@Nullable InfluenceState pSuccessor, CFAEdge pEdge) {
    // TODO Auto-generated method stub
    // System.out.println("edge: " + pEdge + "\t\tinfluence: " + pSuccessor);
    return super.postProcessing(pSuccessor, pEdge);
  }

}
