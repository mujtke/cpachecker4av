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

import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Table;
import de.uni_freiburg.informatik.ultimate.smtinterpol.util.ArrayQueue;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Stack;
import java.util.logging.Level;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.io.IO;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.ast.AFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.AStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionDeclaration;
import org.sosy_lab.cpachecker.cfa.model.AStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.model.FunctionExitNode;
import org.sosy_lab.cpachecker.cfa.model.FunctionSummaryEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionSummaryStatementEdge;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.dependence.DGNode;
import org.sosy_lab.cpachecker.util.dependencegraph.DepConstraintBuilder;

/** Factory for creating a {@link ConditionalDepGraph} from a {@link CFA}. */
@Options(prefix = "depgraph.cond")
public class ConditionalDepGraphBuilder implements StatisticsProvider {

  private final CFA cfa;
  private final LogManager logger;
  private final CondDepGraphBuilderStatistics statistics;

  @Option(
      secure = true,
      name = "useCondDep",
      description =
          "Whether to consider conditional dependencies. If not, then two depedent "
              + "nodes will allways be un-conditionally dependent.")
  private boolean useConditionalDep = false;

  @Option(
      secure = true,
      name = "buildClonedFunc",
      description =
          "Whether consider to build the depedence relation for cloned functions (this option "
              + "is mainly used for debugging). If not enabled, the conditional dependence "
              + "graph is incompelete, and it should not be used in program verification!")
  private boolean buildForClonedFunctions = true;

  @Option(
      secure = true,
      name = "blockfunc",
      description =
          "A list of function pairs that could forms an 'atomic' block, only one thread "
              + "could executes the instructions in this block.")
  private BiMap<String, String> specialBlockFunctionPairs =
      HashBiMap.create(
          ImmutableMap.of(
              "__VERIFIER_atomic_begin",
              "__VERIFIER_atomic_end",
              "pthread_mutex_lock",
              "pthread_mutex_unlock"));

  @Option(
      secure = true,
      name = "mainfunc",
      description =
          "This option specificies the name of the main function (it is mainly used for "
              + "decrease the size of the dependence graph)")
  private String mainFunctionName = "main";

  @Option(
      secure = true,
      description =
          "File to export dependence graph to. If `null`, dependence"
              + " graph will not be exported as dot.")
  @FileOption(FileOption.Type.OUTPUT_FILE)
  private Path exportDot = Paths.get("./output/CondDependenceGraph.dot");

  private static final String specialSelfBlockFunction = "__VERIFIER_atomic_";
  private static final String cloneFunction = "__cloned_function__";

  private BiMap<CFAEdge, EdgeVtx> nodes;
  private Table<EdgeVtx, EdgeVtx, CondDepConstraints> depGraph;
  private Map<String, EdgeVtx> selfBlockFunVarCache;

  public ConditionalDepGraphBuilder(
      final CFA pCfa, final Configuration pConfig, final LogManager pLogger)
      throws InvalidConfigurationException {
    pConfig.inject(this);

    cfa = pCfa;
    logger = pLogger;
    statistics = new CondDepGraphBuilderStatistics();
  }

  /**
   * This function builds the conditional dependence graph of the given program.
   *
   * @return The conditional dependence graph.
   * @implNote When a {@link CFANode} have many successor edges, there is no block function in these
   *     edges.
   */
  public ConditionalDepGraph build() {
    selfBlockFunVarCache = new HashMap<>();

    // firstly, extract all the DGNode by processing all the function (DFS strategy).
    statistics.nodeBuildTimer.start();
    nodes = buildDependenceGraphNodes();
    statistics.nodeBuildTimer.stop();
    statistics.gVarAccessNodeNumber.setNextValue(nodes.size());

    // secondly, build the conditional dependence graph.
    statistics.depGraphBuildTimer.start();
    depGraph = buildDependenceGraph(nodes);
    statistics.depGraphBuildTimer.stop();

    return new ConditionalDepGraph(nodes, depGraph, buildForClonedFunctions, useConditionalDep);
  }

  /**
   * This function builds all the nodes of the dependence graph.
   *
   * @return The set of DGNode, where each DNode corresponds to an edge.
   * @implNote For self-block functions, all the read/write variables in these functions construct a
   *     single DGNode.
   */
  private BiMap<CFAEdge, EdgeVtx> buildDependenceGraphNodes() {
    HashBiMap<CFAEdge, EdgeVtx> tmpDGNode = HashBiMap.create();
    EdgeSharedVarAccessExtractor extractor =
        new EdgeSharedVarAccessExtractor(specialBlockFunctionPairs, specialSelfBlockFunction);
    Set<CFANode> visitedNodes = new HashSet<>();

    Pair<Set<FunctionEntryNode>, Set<FunctionEntryNode>> funcSplit = splitSelfBlockFunction(cfa);
    Set<FunctionEntryNode> selfBlockFunSet = funcSplit.getFirst(),
        noneSelfBlockFunSet = funcSplit.getSecond();

    // process self-block functions first.
    Iterator<FunctionEntryNode> selfBlockFunIter = selfBlockFunSet.iterator();
    while (selfBlockFunIter.hasNext()) {
      FunctionEntryNode func = selfBlockFunIter.next();
      handleSelfBlockFunctionImpl(func, extractor, visitedNodes);
    }

    // process none self-block functions.
    Iterator<FunctionEntryNode> noneSelfBlockFunIter = noneSelfBlockFunSet.iterator();
    while (noneSelfBlockFunIter.hasNext()) {
      FunctionEntryNode func = noneSelfBlockFunIter.next();
      handleNoneSelfBlockFunctionImpl(func, extractor, visitedNodes, tmpDGNode);
    }

    return tmpDGNode;
  }

  /**
   * This function builds a single {@link DGNode} corresponding to all its edges.
   *
   * @param pFuncEntry The entry node of this function.
   * @param pExtractor The {@link DGNode} extractor.
   * @param pVisitedNodes The set of visited nodes.
   * @implNote The result {@link DGNode} will not add into the dependence graph, since it's only a
   *     function definition and when a self-block function is called, only one thread is executing.
   */
  private void handleSelfBlockFunctionImpl(
      FunctionEntryNode pFuncEntry,
      EdgeSharedVarAccessExtractor pExtractor,
      Set<CFANode> pVisitedNodes) {
    String funcName = pFuncEntry.getFunctionName();
    if (!buildForClonedFunctions && funcName.contains(cloneFunction)) {
      // we do not process the cloned function for sake of the size of dependence graph.
      return;
    }

    // process self block function (e.g., __VERIFIER_atomic_lock_release(...)).
    // note: the self-block function DGNode should not placed in the dependence node, it just a
    // function block.
    EdgeVtx blockDepNode = handleSelfBlockFunction(pFuncEntry, pExtractor, pVisitedNodes);
  }

  /**
   * This function builds {@link DGNode} for none self-block functions.
   *
   * @param pFuncEntry The entry node of this function.
   * @param pExtractor The {@link DGNode} extractor.
   * @param pVisitedNodes The set of visited nodes.
   * @param pDGNodes The set of {@link DGNode}, where all generated {@link DGNode} will be placed
   *     into it.
   * @implNote For the implementation of CPAChecker, the initialization part of global variables are
   *     placed into the 'main' function, we need not build the {@link DGNode} for these
   *     initialization codes.
   */
  private void handleNoneSelfBlockFunctionImpl(
      FunctionEntryNode pFuncEntry,
      EdgeSharedVarAccessExtractor pExtractor,
      Set<CFANode> pVisitedNodes,
      HashBiMap<CFAEdge, EdgeVtx> pDGNodes) {
    String funcName = pFuncEntry.getFunctionName();
    if (!buildForClonedFunctions && funcName.contains(cloneFunction)) {
      // we do not process the cloned function for sake of the size of dependence graph.
      return;
    }

    // exploration data structure.
    Queue<CFANode> waitlist = new ArrayQueue<>();

    boolean extractStart = false;
    // BFS strategy.
    waitlist.add(pFuncEntry);
    while (!waitlist.isEmpty()) {
      CFANode node = waitlist.remove();

      for (int i = 0; i < node.getNumLeavingEdges(); ++i) {
        CFAEdge edge = node.getLeavingEdge(i);
        String edgeFuncName = getEdgeFunctionName(edge);

        if (node.getFunctionName().contains("P1__cloned_function__2")) {
          System.out.println(node.getFunctionName() + ": " + node.getLeavingEdge(i));
        }

        // special optimization for main function.
        if (funcName.equals(mainFunctionName) && !extractStart) {
          // we skip the global variable initialization part for the sake of size of dependence
          // graph, since the initialization codes only execute once, and have no affect to other
          // threads.
          if ((edge instanceof CDeclarationEdge)) {
            CDeclaration decl = ((CDeclarationEdge) edge).getDeclaration();
            if ((decl instanceof CFunctionDeclaration) && decl.getName().equals(mainFunctionName)) {
              extractStart = true;
            }
          }
          waitlist.add(edge.getSuccessor());
          continue;
        }

        if (isBlockStartPoint(edgeFuncName)) {
          // handle block.
          EdgeVtx blockDepNode =
              handleBlockNode(node, edge, edgeFuncName, pExtractor, waitlist, pVisitedNodes);
          if (blockDepNode != null) {
            pDGNodes.put(edge, blockDepNode);
          }
        } else {
          // handle none block.
          EdgeVtx noneBlockDepNode =
              handleNoBlockNode(node, edge, edgeFuncName, pExtractor, waitlist, pVisitedNodes);
          if (noneBlockDepNode != null) {
            pDGNodes.put(edge, noneBlockDepNode);
            //            System.out.println(noneBlockDepNode);
          }
        }
      }
    }
  }

  /**
   * This function splits all the functions in the given program into two parts, i.e., self-block
   * functions and none self-block functions.
   *
   * @param pCfa The {@link CFA} corresponding to a program.
   * @return The pair of self-block functions and none self-block functions.
   */
  private Pair<Set<FunctionEntryNode>, Set<FunctionEntryNode>> splitSelfBlockFunction(CFA pCfa) {
    assert pCfa != null;

    Set<FunctionEntryNode> selfBlockFunEntry = new HashSet<>(),
        noSelfBlockFunEntry = new HashSet<>();
    Iterator<FunctionEntryNode> funcIter = cfa.getAllFunctionHeads().iterator();

    while (funcIter.hasNext()) {
      FunctionEntryNode funcEntry = funcIter.next();
      if (isSelfBlockFunction(funcEntry.getFunctionName())) {
        selfBlockFunEntry.add(funcEntry);
      } else {
        noSelfBlockFunEntry.add(funcEntry);
      }
    }

    return Pair.of(selfBlockFunEntry, noSelfBlockFunEntry);
  }

  /**
   * This function checks whether the given function name is a block start point.
   *
   * @param pFunName The function name that need to be checked.
   * @return Return true if this function name is in the special block function list.
   */
  private boolean isBlockStartPoint(String pFunName) {
    return pFunName != null ? specialBlockFunctionPairs.keySet().contains(pFunName) : false;
  }

  /**
   * If the given edge is a function call related edge, this function returns it function name,
   * otherwise returns null.
   *
   * @param pEdge The edge that need to be extracted.
   * @return The function name or null.
   */
  private String getEdgeFunctionName(CFAEdge pEdge) {
    switch(pEdge.getEdgeType()) {
      case StatementEdge: {
          final AStatement stmt = ((AStatementEdge) pEdge).getStatement();
          if (stmt instanceof AFunctionCallStatement) {
            String funcName =
                ((AFunctionCallStatement) stmt)
                    .getFunctionCallExpression()
                    .getFunctionNameExpression()
                    .toString();
            return funcName;
          }
          return null;
      }
      case FunctionCallEdge: {
          return ((FunctionCallEdge) pEdge).getSuccessor().getFunctionName();
      }
      default:
        return null;
    }
  }

  /**
   * This function only builds a single {@link DGNode} for an edge pEdge.
   *
   * @param pEdgePreNode The precursor of the pEdge.
   * @param pEdge The edge that need to build an {@link DGNode}.
   * @param pEdgeFunName The function name of pEdge if it is a function call related edge, otherwise
   *     it values null.
   * @param pExtractor The {@link DGNode} extractor.
   * @param pWaitlist The wait list of {@link DGNode} that need to be processed.
   * @param pVisitedNodes The list of visited nodes.
   * @return The {@link DGNode} of this edge.
   * @implNote If pEdge is a self-block function call edge, we only combine the {@link DGNode} of
   *     parameters and self-block content of this function.
   */
  private EdgeVtx handleNoBlockNode(
      CFANode pEdgePreNode,
      CFAEdge pEdge,
      String pEdgeFunName,
      EdgeSharedVarAccessExtractor pExtractor,
      Queue<CFANode> pWaitlist,
      Set<CFANode> pVisitedNodes) {
    CFANode edgeSucNode = pEdge.getSuccessor();

    // we do not need to process CFunctionSummaryStatementEdge.
    if (!(pEdge instanceof CFunctionSummaryStatementEdge)) {
      // process self-block function call.
      if ((pEdgeFunName != null) && selfBlockFunVarCache.containsKey(pEdgeFunName)) {
        // get the block parameter DGNode.
        EdgeVtx selfBlockParamDGNode = (EdgeVtx) pExtractor.extractESVAInfo(pEdge);
        // get the block content DGNode.
        EdgeVtx selfBlockContentDGNode = selfBlockFunVarCache.get(pEdgeFunName);
        // replace the function call edge of this DGNode, since other caller use pEdge to call the
        // self-block function.
        selfBlockParamDGNode =
            selfBlockParamDGNode != null
                ? new EdgeVtx(
                    pEdge,
                    selfBlockParamDGNode.getgReadVars(),
                    selfBlockParamDGNode.getgWriteVars(),
                    selfBlockParamDGNode.isSimpleEdgeVtx())
                : null;
        selfBlockContentDGNode =
            selfBlockContentDGNode != null
                ? new EdgeVtx(
                    pEdge,
                    selfBlockContentDGNode.getgReadVars(),
                    selfBlockContentDGNode.getgWriteVars(),
                    selfBlockContentDGNode.isSimpleEdgeVtx())
                : null;
        // get the return node of this self-block function.
        CFANode sucNode =
            Preconditions.checkNotNull(pEdgePreNode.getLeavingSummaryEdge()).getSuccessor();
        pWaitlist.add(sucNode);

        EdgeVtx resDGNode =
            selfBlockContentDGNode != null
                ? (selfBlockParamDGNode != null
                    ? selfBlockContentDGNode.mergeGlobalRWVarsOnly(selfBlockParamDGNode)
                    : selfBlockContentDGNode)
                : selfBlockParamDGNode;
        return resDGNode;
      }

      if (!pVisitedNodes.contains(edgeSucNode) && !(edgeSucNode instanceof FunctionExitNode)) {
        pWaitlist.add(edgeSucNode);
      }
      pVisitedNodes.add(pEdgePreNode);

      return (EdgeVtx) pExtractor.extractESVAInfo(pEdge);
    }

    return null;
  }

  /**
   * This function builds a single {@link DGNode} for a normal block.
   *
   * @param pEdgePreNode The precursor of this pBlockStartEdge.
   * @param pBlockStartEdge The starting edge of this block.
   * @param pBlockStartFunName The called function name of this block.
   * @param pExtractor The {@link DGNode} extractor.
   * @param pWaitlist The wait list of {@link DGNode} that need to be processed.
   * @param pVisitedNodes The list of visited nodes.
   * @return The {@link DGNode} of this block.
   * @implNote All the globally read/write variables are collected in the result {@link DGNode}, and
   *     it is the largest {@link DGNode} (the most conservation node) of this block if there are
   *     some branches which may lead to different block end.
   */
  private EdgeVtx handleBlockNode(
      CFANode pEdgePreNode,
      CFAEdge pBlockStartEdge,
      String pBlockStartFunName,
      EdgeSharedVarAccessExtractor pExtractor,
      Queue<CFANode> pWaitlist,
      Set<CFANode> pVisitedNodes) {
    assert pEdgePreNode.getNumLeavingEdges() == 1
        && specialBlockFunctionPairs.keySet().contains(pBlockStartFunName);

    String blockStopFunName = specialBlockFunctionPairs.get(pBlockStartFunName);
    Stack<Pair<CFANode, Integer>> blockStack = new Stack<>();
    Set<CFAEdge> visitedEdges = new HashSet<>();
    EdgeVtx resDepNode = (EdgeVtx) pExtractor.extractESVAInfo(pBlockStartEdge);
    int innerProcEdgeNumber = 0;

    // DFS strategy，each pair in the block stack represents the CFANode and the next successor index
    // of this node.
    blockStack.push(Pair.of(pBlockStartEdge.getSuccessor(), 0));
    while (!blockStack.isEmpty()) {
      Pair<CFANode, Integer> nodeStatus = blockStack.peek();
      CFANode curNode = nodeStatus.getFirst();
      int curNodeNextIndex = nodeStatus.getSecond();

      // reach successor limit or block end.
      if ((curNodeNextIndex >= curNode.getNumLeavingEdges())
          || curNode instanceof FunctionExitNode) {
        blockStack.pop();
        continue;
      }

      CFAEdge nextEdge = curNode.getLeavingEdge(curNodeNextIndex);
      CFANode nextEdgeSucNode = nextEdge.getSuccessor();
      if (visitedEdges.contains(nextEdge)) {
        nodeStatus = blockStack.pop();
        blockStack.push(Pair.of(nodeStatus.getFirst(), nodeStatus.getSecond() + 1));
        continue;
      }
      String nextEdgeFunName = getEdgeFunctionName(nextEdge);

      // process function call.
      if (nextEdgeFunName != null) {
        // process block end.
        if (nextEdgeFunName.equals(blockStopFunName)) {
          EdgeVtx tmpDepNode = (EdgeVtx) pExtractor.extractESVAInfo(nextEdge);
          ++innerProcEdgeNumber;
          if (tmpDepNode != null) {
            resDepNode = resDepNode.mergeGlobalRWVarsOnly(tmpDepNode);
          }

          visitedEdges.add(nextEdge);
          pWaitlist.add(nextEdgeSucNode);
          pVisitedNodes.add(nextEdgeSucNode);
          continue;
        } else {
          // process block inner function call (leaving summary edge)
          FunctionSummaryEdge leavingSummaryEdge = curNode.getLeavingSummaryEdge();
          if (leavingSummaryEdge != null) {
            // some function call have no leaving summary edge.
            EdgeVtx tmpDepNode = (EdgeVtx) pExtractor.extractESVAInfo(leavingSummaryEdge);
            if (tmpDepNode != null) {
              resDepNode = resDepNode.mergeGlobalRWVarsOnly(tmpDepNode);
            }

            CFANode summarySucNode = leavingSummaryEdge.getSuccessor();
            blockStack.push(Pair.of(summarySucNode, 0));
            visitedEdges.add(leavingSummaryEdge);
            pVisitedNodes.add(summarySucNode);
          }
        }
      }

      // process block internals.
      EdgeVtx tmpDepNode = (EdgeVtx) pExtractor.extractESVAInfo(nextEdge);
      ++innerProcEdgeNumber;
      if (tmpDepNode != null) {
        resDepNode = resDepNode.mergeGlobalRWVarsOnly(tmpDepNode);
      }
      blockStack.push(Pair.of(nextEdgeSucNode, 0));
      visitedEdges.add(nextEdge);
      pVisitedNodes.add(nextEdgeSucNode);
    }

    // mark that this block is not a simple block.
    if (innerProcEdgeNumber == 2) {
      resDepNode =
          new EdgeVtx(
              resDepNode.getEdge(), resDepNode.getgReadVars(), resDepNode.getgWriteVars(), true);
    } else {
      resDepNode =
          new EdgeVtx(
              resDepNode.getEdge(), resDepNode.getgReadVars(), resDepNode.getgWriteVars(), false);
    }

    // empty block, we should not put it into the dependence graph.
    if (resDepNode.getgReadVars().isEmpty() && resDepNode.getgWriteVars().isEmpty()) {
      return null;
    }

    return resDepNode;
  }

  /**
   * This function checks whether the given function is a self block function.
   *
   * @param pFuncName The function name of the entry function.
   * @return Return true iff the function starts with '__VERIFIER_atomic_' but not in the special
   *     block function map (e.g., '__VERIFIER_atomic_lock_release').
   */
  private boolean isSelfBlockFunction(String pFuncName) {
    if (pFuncName != null
        && pFuncName.startsWith(specialSelfBlockFunction)
        && !specialBlockFunctionPairs.keySet().contains(pFuncName)
        && !specialBlockFunctionPairs.values().contains(pFuncName)) {
      return true;
    }

    return false;
  }

  /**
   * This function builds the DGNode of the function, it extracts all the globally read variables
   * and write variables from its edges.
   *
   * @param pFunEntry The entry node of this function.
   * @param pExtractor The globally read/write variable extractor.
   * @param pVisitedNodes The globally visited nodes, which is used for avoiding the exploration of
   *     visited nodes.
   * @return The DGNode of this function, which only contains the globally read/write variables.
   */
  private EdgeVtx handleSelfBlockFunction(
      FunctionEntryNode pFunEntry,
      EdgeSharedVarAccessExtractor pExtractor,
      Set<CFANode> pVisitedNodes) {
    assert pFunEntry != null;
    String funcName = pFunEntry.getFunctionName();

    Queue<CFANode> waitlist = new ArrayQueue<>();
    if(selfBlockFunVarCache.keySet().contains(funcName)) {
      return selfBlockFunVarCache.get(funcName);
    } else {

      // build the DGNode of this function.
      EdgeVtx resDGNode = new EdgeVtx(pFunEntry.getEnteringEdge(0), Set.of(), Set.of(), false);

      waitlist.add(pFunEntry);
      while (!waitlist.isEmpty()) {
        CFANode node = waitlist.remove();

        for (int i = 0; i < node.getNumLeavingEdges(); ++i) {
          CFAEdge edge = node.getLeavingEdge(i);
          EdgeVtx tmpDGNode = (EdgeVtx) pExtractor.extractESVAInfo(edge);

          if (tmpDGNode != null) {
            resDGNode = resDGNode.mergeGlobalRWVarsOnly(tmpDGNode);
          }

          CFANode edgeSucNode = edge.getSuccessor();
          // skip the visited and function exit nodes.
          if (!pVisitedNodes.contains(edgeSucNode) && !(edgeSucNode instanceof FunctionExitNode)) {
            waitlist.add(edgeSucNode);
          }
        }
      }

      // for empty self block function (i.e., this self block do not access global variables), we
      // need not preserve it.
      if (resDGNode.getgReadVars().isEmpty() && resDGNode.getgWriteVars().isEmpty()) {
        resDGNode = null;
      }
      selfBlockFunVarCache.put(funcName, resDGNode);
      return resDGNode;
    }
  }

  private Table<EdgeVtx, EdgeVtx, CondDepConstraints> buildDependenceGraph(
      BiMap<CFAEdge, EdgeVtx> pDGNodes) {
    HashBasedTable<EdgeVtx, EdgeVtx, CondDepConstraints> resDepGraph = HashBasedTable.create();
    DepConstraintBuilder builder = DepConstraintBuilder.getInstance();
    List<EdgeVtx> dgNodes = new ArrayList<>(pDGNodes.values());

    // actually, we only need to compute the upper triangular matrix of the dependence graph.
    for (int i = 0; i < dgNodes.size(); ++i) {
      for (int j = i; j < dgNodes.size(); ++j) {
        EdgeVtx rowNode = dgNodes.get(i), colNode = dgNodes.get(j);
        String rowFun = rowNode.getEdge().getPredecessor().getFunctionName(),
            colFun = colNode.getEdge().getPredecessor().getFunctionName();

        // we need not the dependence relation of two edges in the main function, since main
        // function can only called once. and note that, if a function could only called once, then
        // it's no need to compute the dependence relation of it self.
        if (rowFun.equals(mainFunctionName) && colFun.equals(mainFunctionName)) {
          continue;
        }

        CondDepConstraints condDepConstraints =
            builder.buildDependenceConstraints(rowNode, colNode, useConditionalDep);

        if (condDepConstraints != null) {
          if (condDepConstraints.isUnCondDep()) {
            statistics.unCondDepNodePairNumber.inc();
          }
          statistics.depNodePairNumber.inc();
          resDepGraph.put(rowNode, colNode, condDepConstraints);
        }
      }
    }

    return resDepGraph;
  }

  private void export(ConditionalDepGraph pCDG) {
    if (exportDot != null) {
      try (Writer w = IO.openOutputFile(exportDot, Charset.defaultCharset())) {
        CondDepGraphExporter.generateDOT(w, pCDG);
      } catch (IOException e) {
        logger.logfUserException(Level.WARNING, e, "Could not write conditional graph to dot file");
      }
    }
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    pStatsCollection.add(getCondDepGraphBuildStatistics());
  }

  public Statistics getCondDepGraphBuildStatistics() {
    return new Statistics() {

      @Override
      public void printStatistics(
          PrintStream pOut, Result pResult, UnmodifiableReachedSet pReached) {
        if (statistics.depGraphBuildTimer.getUpdateCount() > 0) {
          put(pOut, 0, statistics.nodeBuildTimer);
          put(pOut, 0, statistics.depGraphBuildTimer);
          put(pOut, 1, statistics.gVarAccessNodeNumber);
          put(pOut, 1, statistics.depNodePairNumber);
          put(pOut, 1, statistics.unCondDepNodePairNumber);
        }
      }

      @Override
      public @Nullable String getName() {
        return ""; // empty name for nice output under CFACreator statistics.
      }
    };
  }
}
