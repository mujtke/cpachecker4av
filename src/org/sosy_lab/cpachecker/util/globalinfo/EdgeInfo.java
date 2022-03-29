// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.util.globalinfo;

import com.google.common.base.Preconditions;
import java.util.Collection;
import java.util.logging.Level;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.util.dependence.conditional.ConditionalDepGraph;
import org.sosy_lab.cpachecker.util.dependence.conditional.ConditionalDepGraphBuilder;
import org.sosy_lab.cpachecker.util.dependencegraph.DepConstraintBuilder;

@Options(prefix = "utils.edgeinfo")
public class EdgeInfo implements StatisticsProvider {

  // We need the CFA to extract the information of edges.
  private final CFA cfa;
  private final Configuration config;
  private final ConditionalDepGraphBuilder builder;

  @Option(
    description = "build constrained dependency graph (only for partial-order reduction)",
    secure = true)
  private boolean buildDepGraph = false;

  // -------- Extracted Information --------
  // The dependent graph of edges in the CFA.
  private final ConditionalDepGraph condDepGraph;

  public EdgeInfo(
      final CFA pCfa,
      final Configuration pConfig,
      final LogManager pLogger,
      final ShutdownNotifier pShutdownNotifier)
      throws InvalidConfigurationException {
    cfa = Preconditions.checkNotNull(pCfa);
    config = Preconditions.checkNotNull(pConfig);

    pConfig.inject(this);

    if (buildDepGraph) {
      DepConstraintBuilder.setupEnvironment(cfa, config, pLogger, pShutdownNotifier);
      builder = new ConditionalDepGraphBuilder(cfa, config, pLogger);
      pLogger.log(Level.INFO, "Building Constrained Dependency Graph ...");
      condDepGraph = builder.build();
    } else {
      builder = null;
      condDepGraph = null;
    }
  }

  public ConditionalDepGraph getCondDepGraph() {
    return condDepGraph;
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    if (buildDepGraph) {
      pStatsCollection.add(builder.getCondDepGraphBuildStatistics());
    }
  }

}
