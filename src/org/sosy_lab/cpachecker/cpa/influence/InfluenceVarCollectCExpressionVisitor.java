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
import com.google.common.collect.Sets;
import java.util.HashSet;
import java.util.Set;
import org.sosy_lab.cpachecker.cfa.ast.c.CAddressOfLabelExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CArraySubscriptExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CCharLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CComplexCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFieldReference;
import org.sosy_lab.cpachecker.cfa.ast.c.CFloatLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CImaginaryLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIntegerLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CPointerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CSimpleDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CStringLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CTypeIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.DefaultCExpressionVisitor;
import org.sosy_lab.cpachecker.core.defaults.precision.VariableTrackingPrecision;
import org.sosy_lab.cpachecker.exceptions.UnsupportedCodeException;

public class InfluenceVarCollectCExpressionVisitor
    extends DefaultCExpressionVisitor<Set<String>, UnsupportedCodeException> {

  protected final VariableTrackingPrecision precision;

  protected InfluenceVarCollectCExpressionVisitor(final VariableTrackingPrecision pPrecision) {
    this.precision = pPrecision;
  }

  @Override
  protected Set<String> visitDefault(CExpression pExp) throws UnsupportedCodeException {
    // TODO Auto-generated method stub
    return ImmutableSet.of();
  }

  /**
   * This visit function process expression like: a[0]. e.g., a[0] --> a[0] a[b+1] --> a[b+1], b (if
   * b is a global variable)
   */
  @Override
  public Set<String> visit(CArraySubscriptExpression pE) throws UnsupportedCodeException {
    // TODO Auto-generated method stub
    Set<String> arrExpGVar = pE.getArrayExpression().accept(this);
    Set<String> subExpGVar = pE.getSubscriptExpression().accept(this);

    return Sets.union(arrExpGVar, subExpGVar);
  }

  /**
   * This visit function process the exprssion like: a + b; This is not a bottom expression.
   */
  @Override
  public Set<String> visit(CBinaryExpression pE) throws UnsupportedCodeException {
    // TODO Auto-generated method stub
    Set<String> lgVar = pE.getOperand1().accept(this);
    Set<String> rgVar = pE.getOperand2().accept(this);

    return Sets.union(lgVar, rgVar);
  }

  @Override
  public Set<String> visit(CCastExpression pE) throws UnsupportedCodeException {
    // TODO Auto-generated method stub
    return pE.getOperand().accept(this);
  }

  @Override
  public Set<String> visit(CComplexCastExpression pE) throws UnsupportedCodeException {
    // TODO Auto-generated method stub
    return pE.getOperand().accept(this);
  }

  /**
   * This visit function process the struct expression like: aa.bb->c.dd (e.g., aa.bb->c.dd --> aa);
   * This is not a bottom expression.
   *
   * @implNote When a global struct variable is declared, the member name will not be the same as
   *           the struct' declaration. e.g., struct A gs; -> ([], [gs]); gs.a = 1; -> ([], [gs.a]);
   *           Thus, we use the filed owner name all the time.
   */
  @Override
  public Set<String> visit(CFieldReference pE) throws UnsupportedCodeException {
    // TODO Auto-generated method stub
    return pE.getFieldOwner().accept(this);
  }

  /**
   * This visit function process the expression like: int a; This is a bottom expression. i.e., no
   * further visit need to be applied.
   *
   * @implNote This function exclude a function as formal parameter.
   */
  @Override
  public Set<String> visit(CIdExpression pE) throws UnsupportedCodeException {
    // TODO Auto-generated method stub
    Set<String> gVar = new HashSet<>();

    // exclude function declaration.
    CSimpleDeclaration decl = pE.getDeclaration();
    if (!(decl instanceof CFunctionDeclaration)) {
      String varName = pE.getDeclaration().getQualifiedName();

      // the qualified name of a global variable does not contain "::".
      if (!varName.contains("::")) {
        gVar.add(varName);
      }
    }

    return gVar;
  }

  @Override
  public Set<String> visit(CCharLiteralExpression pE) throws UnsupportedCodeException {
    // TODO Auto-generated method stub
    return ImmutableSet.of();
  }

  @Override
  public Set<String> visit(CImaginaryLiteralExpression pE) throws UnsupportedCodeException {
    // TODO Auto-generated method stub
    return ImmutableSet.of();
  }

  @Override
  public Set<String> visit(CFloatLiteralExpression pE) throws UnsupportedCodeException {
    // TODO Auto-generated method stub
    return ImmutableSet.of();
  }

  @Override
  public Set<String> visit(CIntegerLiteralExpression pE) throws UnsupportedCodeException {
    // TODO Auto-generated method stub
    return ImmutableSet.of();
  }

  @Override
  public Set<String> visit(CStringLiteralExpression pE) throws UnsupportedCodeException {
    // TODO Auto-generated method stub
    return ImmutableSet.of();
  }

  @Override
  public Set<String> visit(CTypeIdExpression pE) throws UnsupportedCodeException {
    // TODO Auto-generated method stub
    return ImmutableSet.of();
  }

  @Override
  public Set<String> visit(CUnaryExpression pE) throws UnsupportedCodeException {
    // TODO Auto-generated method stub
    return pE.getOperand().accept(this);
  }

  @Override
  public Set<String> visit(CPointerExpression pE) throws UnsupportedCodeException {
    // TODO Auto-generated method stub
    return super.visit(pE);
  }

  @Override
  public Set<String> visit(CAddressOfLabelExpression pE) throws UnsupportedCodeException {
    // TODO Auto-generated method stub
    return ImmutableSet.of();
  }

}
