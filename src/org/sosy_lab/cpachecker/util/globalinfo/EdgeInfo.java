// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.util.globalinfo;

import com.google.common.base.Preconditions;
import java.util.logging.Level;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.util.dependence.conditional.ConditionalDepGraph;
import org.sosy_lab.cpachecker.util.dependence.conditional.ConditionalDepGraphBuilder;

public class EdgeInfo {

  // We need the CFA to extract the information of edges.
  private final CFA cfa;
  private final Configuration config;
  private final ConditionalDepGraphBuilder builder;

  // -------- Extracted Information --------
  // The dependent graph of edges in the CFA.
  private final ConditionalDepGraph condDepGraph;

  public EdgeInfo(final CFA pCfa, final Configuration pConfig, final LogManager pLogger)
      throws InvalidConfigurationException {
    cfa = Preconditions.checkNotNull(pCfa);
    config = Preconditions.checkNotNull(pConfig);

    builder = new ConditionalDepGraphBuilder(cfa, config, pLogger);
    pLogger.log(Level.INFO, "Building Conditional Dependency Graph ...");
    condDepGraph = builder.build();
  }

  public ConditionalDepGraph getCondDepGraph() {
    return condDepGraph;
  }

}
