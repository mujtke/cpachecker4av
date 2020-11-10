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

import com.google.common.base.Joiner;
import com.google.common.collect.BiMap;
import com.google.common.collect.Table;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.sosy_lab.common.io.IO;
import org.sosy_lab.cpachecker.util.dependence.DGNode;
import org.sosy_lab.cpachecker.util.dependence.DepTypeEnum;
import org.sosy_lab.cpachecker.util.dependence.DependenceGraph;

/**
 * This graph preserves the conditional dependence relation and constraints of edges.
 *
 * @implNote If two edges have no dependency relation, the conditional dependence constraint will be
 *     null.
 */
public class ConditionalDepGraph extends DependenceGraph {

  private final boolean complete;
  private final boolean useCondDep;
  private BiMap<Integer, EdgeVtx> nodes;
  private Map<Integer, EdgeVtx> blockNodes;
  private Table<EdgeVtx, EdgeVtx, CondDepConstraints> depGraph;

  public ConditionalDepGraph(
      final BiMap<Integer, EdgeVtx> pNodes,
      final Table<EdgeVtx, EdgeVtx, CondDepConstraints> pDepGraph,
      boolean pComplete,
      boolean pUseCondDep) {
    assert pNodes != null && pDepGraph != null;
    complete = pComplete;
    useCondDep = pUseCondDep;
    nodes = pNodes;
    depGraph = pDepGraph;

    blockNodes = new HashMap<>();
    nodes.values().forEach(n -> n.getBlockEdges().forEach(ne -> blockNodes.put(ne.hashCode(), n)));
  }

  @Override
  public DepTypeEnum getDependencyType() {
    return DepTypeEnum.Cond;
  }

  public BiMap<Integer, EdgeVtx> getNodes() {
    return nodes;
  }

  public Table<EdgeVtx, EdgeVtx, CondDepConstraints> getDepGraph() {
    return depGraph;
  }

  public Collection<EdgeVtx> getAllNodes() {
    return nodes.values();
  }

  public boolean isUseCondDep() {
    return useCondDep;
  }

  public boolean isComplete() {
    return complete;
  }

  public CondDepConstraints getCondDepConstraints(final Integer e1, final Integer e2) {
    return depGraph.get(nodes.get(e1), nodes.get(e2));
  }

  public void export(String pFilePath) {
    Path exportPath = Paths.get(pFilePath);

    if (exportPath != null) {
      try (Writer w = IO.openOutputFile(exportPath, Charset.defaultCharset())) {
        CondDepGraphExporter.generateDOT(w, this);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  @SuppressWarnings("resource")
  public void exportNodes(String pFilePath) {
    Path exportPath = Paths.get(pFilePath);

    if (exportPath != null) {
      try (Writer w = IO.openOutputFile(exportPath, Charset.defaultCharset())) {
        List<String> info = new ArrayList<>();

        for (Entry<Integer, EdgeVtx> node : nodes.entrySet()) {
          EdgeVtx nodeValue = node.getValue();
          info.add(nodeValue.getBlockStartEdge() + "\t" + nodeValue);
        }
        Joiner.on("\n").appendTo(w, info);

      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  @Override
  public Object dep(DGNode n1, DGNode n2) {
    if (!complete) {
      throw new AssertionError(
          "the conditional dependence graph is incomplete, please make "
              + "sure that the option 'depgraph.cond.buildClonedFunc' is enabled");
    }

    // the two nodes are independent.
    if (n1 == null || n2 == null) {
      return null;
    }

    // the two nodes may independent.
    // since we only preserved the upper right triangle of the dependence graph, we at most need to
    // compare twice.
    CondDepConstraints n1n2 = depGraph.get(n1, n2);
    if (n1n2 != null) {
      return n1n2;
    }
    return depGraph.get(n2, n1);
  }

  public boolean contains(final Integer pEdgeHash) {
    return nodes.containsKey(pEdgeHash);
  }

  public boolean blockContains(final Integer pEdgeHash) {
    return blockNodes.containsKey(pEdgeHash);
  }

  public DGNode getDGNode(final Integer pEdgeHash) {
    return nodes.get(pEdgeHash);
  }

  public DGNode getBlockDGNode(final Integer pEdgeHash) {
    if (pEdgeHash != null) {
      return blockNodes.get(pEdgeHash);
    }
    return null;
  }

}
