// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.util.dependence.conditional;

import org.sosy_lab.cpachecker.cfa.ast.c.CAddressOfLabelExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CArraySubscriptExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CComplexCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFieldReference;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CPointerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.DefaultCExpressionVisitor;
import org.sosy_lab.cpachecker.exceptions.NoException;

public class ReplaceVisitor extends DefaultCExpressionVisitor<CExpression, NoException> {

  private CExpression tgtExp;
  private CExpression repExp;

  public ReplaceVisitor(CExpression pTgtExp, CExpression pRepExp) {
    this.tgtExp = pTgtExp;
    this.repExp = pRepExp;
  }

  @Override
  public CExpression visit(CArraySubscriptExpression pE) throws NoException {
    if (pE.equals(tgtExp)) {
      return repExp;
    } else {
      CExpression arrExp = pE.getArrayExpression().accept(this),
          subExp = pE.getSubscriptExpression().accept(this);

      return new CArraySubscriptExpression(
          pE.getFileLocation(),
          pE.getExpressionType(),
          arrExp,
          subExp);
    }
  }

  @Override
  public CExpression visit(CBinaryExpression pE) throws NoException {
    CExpression op1 = pE.getOperand1().accept(this), op2 = pE.getOperand2().accept(this);
    return new CBinaryExpression(
        pE.getFileLocation(),
        pE.getExpressionType(),
        pE.getCalculationType(),
        op1,
        op2,
        pE.getOperator());
  }

  @Override
  public CExpression visit(CCastExpression pE) throws NoException {
    CExpression op = pE.getOperand().accept(this);
    return new CCastExpression(pE.getFileLocation(), pE.getExpressionType(), op);
  }

  @Override
  public CExpression visit(CComplexCastExpression pE) throws NoException {
    CExpression op = pE.getOperand().accept(this);
    return new CComplexCastExpression(
        pE.getFileLocation(),
        pE.getExpressionType(),
        op,
        pE.getType(),
        pE.isRealCast());
  }

  @Override
  public CExpression visit(CFieldReference pE) throws NoException {
    if (pE.equals(tgtExp)) {
      return repExp;
    } else {
      CExpression owner = pE.getFieldOwner().accept(this);

      return new CFieldReference(
          pE.getFileLocation(),
          pE.getExpressionType(),
          pE.getFieldName(),
          owner,
          pE.isPointerDereference());
    }
  }

  @Override
  public CExpression visit(CIdExpression pE) throws NoException {
    // the bottom expression.
    return pE.equals(tgtExp) ? repExp : pE;
  }

  @Override
  public CExpression visit(CUnaryExpression pE) throws NoException {
    CExpression op = pE.getOperand().accept(this);

    return new CUnaryExpression(pE.getFileLocation(), pE.getExpressionType(), op, pE.getOperator());
  }

  @Override
  public CExpression visit(CPointerExpression pE) throws NoException {
    if (pE.equals(tgtExp)) {
      return repExp;
    } else {
      CExpression op = pE.getOperand().accept(this);

      return new CPointerExpression(pE.getFileLocation(), pE.getExpressionType(), op);
    }
  }

  @Override
  public CExpression visit(CAddressOfLabelExpression pE) throws NoException {
    return pE.equals(tgtExp) ? repExp : pE;
  }

  @Override
  protected CExpression visitDefault(CExpression pExp) throws NoException {
    // just return the expression.
    return pExp;
  }

}
