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
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import de.uni_freiburg.informatik.ultimate.smtinterpol.util.ArrayQueue;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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
import org.sosy_lab.cpachecker.cfa.ast.AExpression;
import org.sosy_lab.cpachecker.cfa.ast.AFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.AFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.AFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.AIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.AStatement;
import org.sosy_lab.cpachecker.cfa.ast.AUnaryExpression;
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
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.dependence.DGNode;
import org.sosy_lab.cpachecker.util.dependencegraph.DepConstraintBuilder;

/** Factory for creating a {@link ConditionalDepGraph} from a {@link CFA}. */
@Options(prefix = "depgraph.cond")
public class ConditionalDepGraphBuilder {

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
    name = "addNodeForGVars",
    description = "This option add node for global variable initialization edges (it is mainly used for )")
  private boolean addNodeForGlobalVariableInit = false;

  @Option(
    secure = true,
      description =
          "File to export dependence graph to. If `null`, dependence"
              + " graph will not be exported as dot.")
  @FileOption(FileOption.Type.OUTPUT_FILE)
  private Path exportDot = Paths.get("CondDependenceGraph.dot");

  @Option(
    secure = true,
    name = "export",
    description = "Export this dependency graph into a file in DOT format.")
  private boolean exportToDot = false;

  @Option(
    secure = true,
    description = "Remove isolated nodes in the conditional dependency graph, these nodes have no effect with other transitions.")
  private boolean removeIsolatedNodes = false;

  @Option(
    secure = true,
    description = "Use solver to compute more precise independence constraints (time-consuming), otherwise we only simply the constraints.")
  private boolean useSolverToCompute = false;

  @Option(
    secure = true,
    description = "Whether build node and constraints for navie thread function (these functions are never used in real ARG exploration). Notice that, if this option and buildForClonedFunctions are disabled, then an empty graph will be generated.")
  private boolean buildForNoneCloneThread = false;

  private static final String specialSelfBlockFunction = "__VERIFIER_atomic_";
  private static final String noneDetFunction = "__VERIFIER_nondet_";
  private static final String cloneFunction = "__cloned_function__";
  // This set is used to collect the function names of all created threads.
  // It's unnecessary to create edges for inner transitions pairs of a thread.
  private List<String> threadFunctions = new ArrayList<>();

  private BiMap<Integer, EdgeVtx> nodes;
  private Table<EdgeVtx, EdgeVtx, CondDepConstraints> depGraph;
  private Map<String, EdgeVtx> selfBlockFunVarCache;

  public ConditionalDepGraphBuilder(
      final CFA pCfa, final Configuration pConfig, final LogManager pLogger)
      throws InvalidConfigurationException {
    pConfig.inject(this);

    DepConstraintBuilder.setUseSolverToCompute(this.useSolverToCompute);
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
    postProcess();
    statistics.depGraphBuildTimer.stop();

    // build and export this graph.
    ConditionalDepGraph depG =
        new ConditionalDepGraph(nodes, depGraph, buildForClonedFunctions, useConditionalDep);
    if (exportToDot) {
      this.export(depG);
    }

    return depG;
  }

  /**
   * This function builds all the nodes of the dependence graph.
   *
   * @return The set of DGNode, where each DNode corresponds to an edge.
   * @implNote For self-block functions, all the read/write variables in these functions construct a
   *     single DGNode.
   */
  private BiMap<Integer, EdgeVtx> buildDependenceGraphNodes() {
    HashBiMap<Integer, EdgeVtx> tmpDGNode = HashBiMap.create();
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
      HashBiMap<Integer, EdgeVtx> pDGNodes) {
    String funcName = pFuncEntry.getFunctionName();
    if (!buildForClonedFunctions && funcName.contains(cloneFunction)) {
      // we do not process the cloned function for sake of the size of dependence graph.
      return;
    }

    // exploration data structure.
    Queue<CFANode> waitlist = new ArrayQueue<>();

    boolean extractStart = addNodeForGlobalVariableInit ? true : false;
    // BFS strategy.
    waitlist.add(pFuncEntry);
    while (!waitlist.isEmpty()) {
      CFANode node = waitlist.remove();

      for (int i = 0; i < node.getNumLeavingEdges(); ++i) {
        CFAEdge edge = node.getLeavingEdge(i);
        String edgeFuncName = getEdgeFunctionName(edge);

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

        // process thread creation edge.
        processEdgeIfThreadCreation(edge);

        if (isBlockStartPoint(edgeFuncName)) {
          // handle block.
          EdgeVtx blockDepNode =
              handleBlockNode(node, edge, edgeFuncName, pExtractor, waitlist, pVisitedNodes);
          if (blockDepNode != null) {
            pDGNodes.put(edge.hashCode(), blockDepNode);
            statistics.blockNumber.inc();
            statistics.blockSize.setNextValue(blockDepNode.getBlockEdgeNumber());
          }
        } else {
          // handle none block.
          EdgeVtx noneBlockDepNode =
              handleNoBlockNode(node, edge, edgeFuncName, pExtractor, waitlist, pVisitedNodes);
          if (noneBlockDepNode != null) {
            pDGNodes.put(edge.hashCode(), noneBlockDepNode);
            //            System.out.println(noneBlockDepNode);
            statistics.blockNumber.inc();
            statistics.blockSize.setNextValue(noneBlockDepNode.getBlockEdgeNumber());
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
          } else if (stmt instanceof AFunctionCallAssignmentStatement) {
            String funcName =
                ((AFunctionCallAssignmentStatement) stmt).getFunctionCallExpression()
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
      if(pEdgeFunName != null) {
        // process self-block function call.
        if(selfBlockFunVarCache.containsKey(pEdgeFunName)) {
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
                      List.of(pEdge),
                      selfBlockParamDGNode.getgReadVars(),
                      selfBlockParamDGNode.getgWriteVars(),
                      selfBlockParamDGNode.isSimpleEdgeVtx(),
                      selfBlockParamDGNode.isContainNonDetVar(),
                      selfBlockParamDGNode.getBlockEdgeNumber())
                  : null;
          selfBlockContentDGNode =
              selfBlockContentDGNode != null
                  ? new EdgeVtx(
                      pEdge,
                      List.of(pEdge),
                      selfBlockContentDGNode.getgReadVars(),
                      selfBlockContentDGNode.getgWriteVars(),
                      selfBlockContentDGNode.isSimpleEdgeVtx(),
                      selfBlockContentDGNode.isContainNonDetVar(),
                      selfBlockContentDGNode.getBlockEdgeNumber())
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
        } else if (pEdgeFunName.startsWith(noneDetFunction)) {
          // process non-determined function call.
          EdgeVtx nonDetDGNode = (EdgeVtx) pExtractor.extractESVAInfo(pEdge);

          CFANode nonDetEdgeSucNode = pEdge.getSuccessor();
          pWaitlist.add(nonDetEdgeSucNode);
          pVisitedNodes.add(pEdgePreNode);
          return nonDetDGNode;
        } else {
          // process no-block inner function call (leaving summary edge)
          FunctionSummaryEdge leavingSummaryEdge = pEdgePreNode.getLeavingSummaryEdge();
          if (leavingSummaryEdge != null) {
            // some function call have no leaving summary edge.
            CFANode summaryEdgeSucNode = leavingSummaryEdge.getSuccessor();
            pWaitlist.add(summaryEdgeSucNode);
            pVisitedNodes.add(summaryEdgeSucNode);
          }
        }
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
    List<CFAEdge> blockEdges = new ArrayList<>();
    blockEdges.add(pBlockStartEdge);
    EdgeVtx resDepNode = (EdgeVtx) pExtractor.extractESVAInfo(pBlockStartEdge);
    int innerProcEdgeNumber = 0, processedEdgeNumber = 0;

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
            resDepNode = (resDepNode == null) ? resDepNode : resDepNode.mergeGlobalRWVarsOnly(tmpDepNode);
          }

          blockEdges.add(nextEdge);
          visitedEdges.add(nextEdge);
          pWaitlist.add(nextEdgeSucNode);
          pVisitedNodes.add(nextEdgeSucNode);
          ++processedEdgeNumber;
          continue;
        } else {
          // process block inner function call (leaving summary edge)
          FunctionSummaryEdge leavingSummaryEdge = curNode.getLeavingSummaryEdge();
          if (leavingSummaryEdge != null) {
            // some function call have no leaving summary edge.
            EdgeVtx tmpDepNode = (EdgeVtx) pExtractor.extractESVAInfo(leavingSummaryEdge);
            if (tmpDepNode != null) {
              resDepNode =
                  (resDepNode == null) ? resDepNode : resDepNode.mergeGlobalRWVarsOnly(tmpDepNode);
            }

            CFANode summarySucNode = leavingSummaryEdge.getSuccessor();
            blockStack.push(Pair.of(summarySucNode, 0));
            visitedEdges.add(leavingSummaryEdge);
            pVisitedNodes.add(summarySucNode);
            ++processedEdgeNumber;
          }
        }
      }

      // process block internals.
      EdgeVtx tmpDepNode = (EdgeVtx) pExtractor.extractESVAInfo(nextEdge);
      ++innerProcEdgeNumber;
      if (tmpDepNode != null) {
        resDepNode =
            (resDepNode == null) ? resDepNode : resDepNode.mergeGlobalRWVarsOnly(tmpDepNode);
      }
      blockEdges.add(nextEdge);
      blockStack.push(Pair.of(nextEdgeSucNode, 0));
      visitedEdges.add(nextEdge);
      pVisitedNodes.add(nextEdgeSucNode);
      ++processedEdgeNumber;
    }

    // mark that this block is not a simple block.
    if (innerProcEdgeNumber == 2) {
      resDepNode = (resDepNode == null) ? null :
          new EdgeVtx(
              resDepNode.getBlockStartEdge(),
              blockEdges,
              resDepNode.getgReadVars(),
              resDepNode.getgWriteVars(),
              true,
              false,
              processedEdgeNumber);
    } else {
      resDepNode = (resDepNode == null) ? null :
          new EdgeVtx(
              resDepNode.getBlockStartEdge(),
              blockEdges,
              resDepNode.getgReadVars(),
              resDepNode.getgWriteVars(),
              false,
              false,
              processedEdgeNumber);
    }

    // empty block, we should not put it into the dependence graph.
    if (resDepNode == null
        || (resDepNode.getgReadVars().isEmpty() && resDepNode.getgWriteVars().isEmpty())) {
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
      CFAEdge initEdge = pFunEntry.getEnteringEdge(0);
      EdgeVtx resDGNode =
          new EdgeVtx(initEdge, List.of(initEdge), Set.of(), Set.of(), false, false, 0);
      int processedEdgeNumber = 0;

      waitlist.add(pFunEntry);
      while (!waitlist.isEmpty()) {
        CFANode node = waitlist.remove();

        for (int i = 0; i < node.getNumLeavingEdges(); ++i) {
          CFAEdge edge = node.getLeavingEdge(i);
          EdgeVtx tmpDGNode = (EdgeVtx) pExtractor.extractESVAInfo(edge);
          ++processedEdgeNumber;

          if (tmpDGNode != null) {
            resDGNode = resDGNode.mergeGlobalRWVarsOnly(tmpDGNode);
          }

          // process thread creation edge.
          processEdgeIfThreadCreation(edge);

          CFANode edgeSucNode = edge.getSuccessor();
          // skip the visited and function exit nodes.
          if (!pVisitedNodes.contains(edgeSucNode) && !(edgeSucNode instanceof FunctionExitNode)) {
            waitlist.add(edgeSucNode);
          }
        }

        // special process to summary edge.
        CFAEdge leavingSumEdge = node.getLeavingSummaryEdge();
        if (leavingSumEdge != null) {
          // NOTICE: the leaving summary edge must be independent with any other edges.
          // hence, we only add its' successor node to the waitlist.
          CFANode leavingSumEdgeSuc = leavingSumEdge.getSuccessor();
          if (!pVisitedNodes.contains(leavingSumEdgeSuc)
              && !(leavingSumEdgeSuc instanceof FunctionExitNode)) {
            waitlist.add(leavingSumEdgeSuc);
          }
        }

      }

      // for empty self block function (i.e., this self block do not access global variables), we
      // need not preserve it.
      resDGNode.setBlockEdgeNumber(processedEdgeNumber);
      if (resDGNode.getgReadVars().isEmpty() && resDGNode.getgWriteVars().isEmpty()) {
        resDGNode = null;
      }
      selfBlockFunVarCache.put(funcName, resDGNode);
      return resDGNode;
    }
  }

  private void processEdgeIfThreadCreation(final CFAEdge pEdge) {
    if (pEdge instanceof AStatementEdge) {
      AStatement statement = ((AStatementEdge) pEdge).getStatement();
      if (statement instanceof AFunctionCall) {
        AExpression functionNameExp =
            ((AFunctionCall) statement).getFunctionCallExpression().getFunctionNameExpression();
        if (functionNameExp instanceof AIdExpression) {
          String funcName = ((AIdExpression) functionNameExp).getName();
          if (funcName.contains("pthread_create")) {
            // get the name of created thread.
            AExpression createdThreadExp = ((AFunctionCall) statement).getFunctionCallExpression()
            .getParameterExpressions()
            .get(2);
            
            threadFunctions.add(
                createdThreadExp instanceof AUnaryExpression
                    ? ((AUnaryExpression) createdThreadExp).getOperand().toASTString()
                    : createdThreadExp.toASTString());
          }
        }
      }
    }
  }

  private Table<EdgeVtx, EdgeVtx, CondDepConstraints> buildDependenceGraph(
      BiMap<Integer, EdgeVtx> pDGNodes) {
    HashBasedTable<EdgeVtx, EdgeVtx, CondDepConstraints> resDepGraph = HashBasedTable.create();
    DepConstraintBuilder builder = DepConstraintBuilder.getInstance();
    List<EdgeVtx> dgNodes = new ArrayList<>(pDGNodes.values());

    // actually, we only need to compute the upper triangular matrix of the dependence graph.
    for (int i = 0; i < dgNodes.size(); ++i) {
      for (int j = i; j < dgNodes.size(); ++j) {
        EdgeVtx rowNode = dgNodes.get(i), colNode = dgNodes.get(j);
        String rowFun = rowNode.getBlockStartEdge().getPredecessor().getFunctionName(),
            colFun = colNode.getBlockStartEdge().getPredecessor().getFunctionName();

        // we need not the dependence relation of two edges in the main function, since main
        // function can only called once. and note that, if a function could only called once, then
        // it's no need to compute the dependence relation of it self.
        if (rowFun.equals(mainFunctionName) && colFun.equals(mainFunctionName)) {
          continue;
        }

        // we need not to create constraints for a pair of transitions that belongs to the same
        // thread.
        if (rowFun.equals(colFun)) {
          String[] splitStr = rowFun.split(cloneFunction);
          if (splitStr != null && splitStr.length > 0 && threadFunctions.contains(splitStr[0])) {
            continue;
          }
        }

        // the naive thread functions are never used in real ARG exploration.
        if (!buildForNoneCloneThread
            && (threadFunctions.contains(rowFun) || threadFunctions.contains(colFun))) {
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

  private void postProcess() {
    // remove isolated nodes in node list.
    if (removeIsolatedNodes) {
      Set<EdgeVtx> dgNodes = new HashSet<>(nodes.values()),
          rowColNodes = Sets.union(depGraph.rowKeySet(), depGraph.columnKeySet());
      Set<EdgeVtx> toRemove = Sets.difference(dgNodes, rowColNodes);
      //
      if (!toRemove.isEmpty()) {
        BiMap<EdgeVtx, Integer> invNodes = nodes.inverse();
        toRemove.forEach(i -> invNodes.remove(i));
        nodes = invNodes.inverse();
      }
    }
  }

  @SuppressWarnings("unused")
  private void export(ConditionalDepGraph pCDG) {
    if (exportDot != null) {
      try (Writer w = IO.openOutputFile(exportDot, Charset.defaultCharset())) {
        CondDepGraphExporter.generateDOT(w, pCDG);
      } catch (IOException e) {
        logger.logfUserException(Level.WARNING, e, "Could not write conditional graph to dot file");
      }
    }
  }

  public Statistics getCondDepGraphBuildStatistics() {
    return new Statistics() {

      @Override
      public void printStatistics(
          PrintStream pOut, Result pResult, UnmodifiableReachedSet pReached) {
        if (statistics.depGraphBuildTimer.getUpdateCount() > 0) {
          pOut.println("\nConstrained Dependency Graph statistics");
          pOut.println("---------------------------------------");
          put(pOut, 0, statistics.nodeBuildTimer);
          put(pOut, 0, statistics.depGraphBuildTimer);
          put(pOut, 1, statistics.gVarAccessNodeNumber);
          put(pOut, 1, statistics.depNodePairNumber);
          put(pOut, 1, statistics.unCondDepNodePairNumber);
          put(pOut, 1, statistics.blockNumber);
          put(pOut, 1, statistics.blockSize);
        }
      }

      @Override
      public @Nullable String getName() {
        return ""; // empty name for nice output under CFACreator statistics.
      }
    };
  }
}
