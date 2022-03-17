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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Joiner;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;

public class CondDepGraphExporter {

  public static void generateDOT(final Appendable pW, final ConditionalDepGraph pDG)
      throws IOException {
    List<String> nodes = new ArrayList<>();
    List<String> edges = new ArrayList<>();
    Table<EdgeVtx, EdgeVtx, CondDepConstraints> adjMtx = pDG.getDepGraph();

    for(EdgeVtx n : pDG.getAllNodes()) {
      nodes.add(getNodeString(n));
    }

    for (Cell<EdgeVtx, EdgeVtx, CondDepConstraints> e : adjMtx.cellSet()) {
      EdgeVtx dependentOn = checkNotNull(e.getRowKey());
      EdgeVtx dependingOn = checkNotNull(e.getColumnKey());
      CondDepConstraints constraints = checkNotNull(e.getValue());
      edges.add(formatEdge(dependentOn, dependingOn, constraints));
    }

    pW.append("digraph ConditionalDependenceGraph {\n");
    Joiner.on("\n").appendTo(pW, nodes);
    pW.append("\n");
    Joiner.on("\n").appendTo(pW, edges);
    pW.append("\n}");
  }

  private static String getNodeString(final EdgeVtx pNode) {
    String shape;

    switch (pNode.getBlockStartEdge().getEdgeType()) {
      case AssumeEdge:
        shape = "diamond";
        break;
      case FunctionCallEdge:
        shape = "ellipse\", peripheries=\"2";
        break;
      default:
        shape = "ellipse";
    }

    return formatNode(pNode, shape);
  }

  private static String getNodeRepresentation(final EdgeVtx pNode) {
    String nodeId = String.valueOf(pNode.hashCode());
    return "E" + nodeId.replaceAll("-", "");
  }

  private static String formatNode(final EdgeVtx pNode, final String pShape) {
    String nodeId = getNodeRepresentation(pNode);

    CFAEdge nodeEdge = pNode.getBlockStartEdge();
    String edgeDesc =
        pNode.getBlockEdgeNumber() == 2
            ? "(simple block): " + pNode.getEdge(1).toString()
            : nodeEdge.toString();
    String nodeDescription =
        (nodeEdge.getPredecessor().getFunctionName() + ": " + edgeDesc)
            .replaceAll("\\\"", "\\\\\"");

    return nodeId + " [shape=\"" + pShape + "\", label=\"" + nodeDescription + "\"]";
  }

  private static String formatEdge(
      final EdgeVtx pStart, final EdgeVtx pEnd, final CondDepConstraints pConst) {
    String csts = pConst.toString();
    String cstsRep = csts.equals("( )") ? null : csts.replaceAll("\\\"", "\\\\\"");
    String labelRep =
        cstsRep != null
            ? (" [label=\"" + cstsRep + "\" fontname=\"Courier New\" dir=both]")
            : " [label=\"UCD\" color=\"red\" dir=both]";

    String rep = getNodeRepresentation(pStart) + " -> " + getNodeRepresentation(pEnd) + labelRep;
    return rep;
  }
}
