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
package org.sosy_lab.cpachecker.core.algorithm.ccex;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.core.algorithm.Algorithm;
import org.sosy_lab.cpachecker.core.algorithm.ParallelAlgorithm.ReachedSetUpdateListener;
import org.sosy_lab.cpachecker.core.algorithm.ParallelAlgorithm.ReachedSetUpdater;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.exceptions.CPAEnabledAnalysisPropertyViolationException;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.globalinfo.CFAInfo;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;

/**
 * This algorithm use SMT solver to check the satisfiability of a path.
 *
 * @implNote This algorithm always combine with the BDD-based analysis algorithm which could not
 *           support many features of C language, thus may report false-negative result.
 */
@Options(prefix = "ccex")
public class CCEXAlgorithm implements Algorithm, ReachedSetUpdater {

  private final List<ReachedSetUpdateListener> reachedSetUpdateListeners =
      new CopyOnWriteArrayList<>();

  private final LogManager logger;
  private final ShutdownNotifier shutdownNotifier;

  private final Algorithm algorithm;
  private final CounterexampleChecker checker;

  public CCEXAlgorithm(
      Algorithm pAlgorithm,
      LogManager pLogger,
      Configuration pConfig,
      ShutdownNotifier pShutdownNotifier)
      throws InvalidConfigurationException {
    pConfig.inject(this);

    logger = pLogger;
    shutdownNotifier = pShutdownNotifier;
    algorithm = pAlgorithm;

    Optional<CFAInfo> cfaInfo = GlobalInfo.getInstance().getCFAInfo();
    if (cfaInfo.isPresent()) {
      CFA pCfa = cfaInfo.get().getCFA();
      checker = new CounterexampleChecker(pCfa, pConfig, pLogger, pShutdownNotifier);
    } else {
      throw new InvalidConfigurationException("could not get cfa information!");
    }
  }

  @Override
  public void register(ReachedSetUpdateListener pReachedSetUpdateListener) {
    if (algorithm instanceof ReachedSetUpdater) {
      ((ReachedSetUpdater) algorithm).register(pReachedSetUpdateListener);
    }
    reachedSetUpdateListeners.add(pReachedSetUpdateListener);
  }

  @Override
  public void unregister(ReachedSetUpdateListener pReachedSetUpdateListener) {
    if (algorithm instanceof ReachedSetUpdater) {
      ((ReachedSetUpdater) algorithm).unregister(pReachedSetUpdateListener);
    }
    reachedSetUpdateListeners.remove(pReachedSetUpdateListener);
  }

  private void notifyReachedSetUpdateListeners(ReachedSet pReachedSet) {
    for (ReachedSetUpdateListener rsul : reachedSetUpdateListeners) {
      rsul.updated(pReachedSet);
    }
  }

  @Override
  public AlgorithmStatus run(ReachedSet pReachedSet)
      throws CPAException, InterruptedException, CPAEnabledAnalysisPropertyViolationException {
    AlgorithmStatus status = AlgorithmStatus.SOUND_AND_PRECISE;

    // run algorithm
    status = status.update(algorithm.run(pReachedSet));
    notifyReachedSetUpdateListeners(pReachedSet);

    // if a target state is reached and the path construct from the root state to this state is
    // actually infeasible, we conclude that the verification result is incomplete.
    if (pReachedSet.hasViolatedProperties() && checker.infeasible(pReachedSet)) {
      status = status.withPrecise(false);
    }

    return status;
  }

}
