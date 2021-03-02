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

import org.sosy_lab.cpachecker.cfa.ast.c.CArraySubscriptExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CCharLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CComplexCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFieldReference;
import org.sosy_lab.cpachecker.cfa.ast.c.CFloatLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CImaginaryLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIntegerLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CPointerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CStringLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.DefaultCExpressionVisitor;
import org.sosy_lab.cpachecker.exceptions.NoException;

public class ExpToStringVisitor extends DefaultCExpressionVisitor<String, NoException> {

  private static ExpToStringVisitor instance;

  public static ExpToStringVisitor getInstance() {
    if (instance == null) {
      instance = new ExpToStringVisitor();
    }
    return instance;
  }

  private ExpToStringVisitor() {}

  @Override
  protected String visitDefault(CExpression pExp) throws NoException {
    return "";
  }

  @Override
  public String visit(CArraySubscriptExpression pE) throws NoException {
    String arr = pE.getArrayExpression().accept(this),
        subscript = pE.getSubscriptExpression().accept(this);
    return arr + "[" + subscript + "]";
  }

  @Override
  public String visit(CBinaryExpression pE) throws NoException {
    String op1 = pE.getOperand1().accept(this), op2 = pE.getOperand2().accept(this);
    String opt = pE.getOperator().getOperator();
    return op1 + " " + opt + " " + op2;
  }

  @Override
  public String visit(CCastExpression pE) throws NoException {
    String castType = pE.getCastType().toString();
    String castOperand = pE.getOperand().accept(this);
    return "(" + castType + ") " + castOperand + "";
  }

  @Override
  public String visit(CComplexCastExpression pE) throws NoException {
    return pE.getOperand().accept(this);
  }

  @Override
  public String visit(CFieldReference pE) throws NoException {
    String owner = pE.getFieldOwner().accept(this);
    String field = pE.getFieldName();
    return pE.isPointerDereference() ? (owner + "->" + field) : (owner + "." + field);
  }

  @Override
  public String visit(CIdExpression pE) throws NoException {
    return pE.getName();
  }

  @SuppressWarnings("deprecation")
  @Override
  public String visit(CCharLiteralExpression pE) throws NoException {
    return pE.getValue() + "";
  }

  @Override
  public String visit(CImaginaryLiteralExpression pE) throws NoException {
    return pE.getValue() + "";
  }

  @Override
  public String visit(CFloatLiteralExpression pE) throws NoException {
    return pE.getValue() + "";
  }

  @Override
  public String visit(CIntegerLiteralExpression pE) throws NoException {
    return pE.getValue() + "";
  }

  @Override
  public String visit(CStringLiteralExpression pE) throws NoException {
    return pE.getValue() + "";
  }

  @Override
  public String visit(CUnaryExpression pE) throws NoException {
    String expName = pE.getOperand().accept(this);
    switch(pE.getOperator()) {
      case MINUS:
        return "-" + expName + "";
      case AMPER:
        return "&" + expName + "";
      case TILDE:
        return "~" + expName + "";
      case SIZEOF:
        return "sizeof(" + expName + ")";
      case ALIGNOF:
        return "__alignof__(" + expName + ")";
      default:
        throw new AssertionError("unhandled expression " + pE + " of " + pE.getClass());
    }
  }

  @Override
  public String visit(CPointerExpression pE) throws NoException {
    return pE.getOperand().accept(this);
  }

}
