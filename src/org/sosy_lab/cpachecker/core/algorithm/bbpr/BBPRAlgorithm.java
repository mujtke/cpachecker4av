// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.core.algorithm.bbpr;

import com.google.common.base.Functions;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.core.algorithm.Algorithm;
import org.sosy_lab.cpachecker.core.defaults.MergeSepOperator;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.MergeOperator;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult.Action;
import org.sosy_lab.cpachecker.core.interfaces.StopOperator;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGCPA;
import org.sosy_lab.cpachecker.cpa.arg.ARGMergeJoinCPAEnabledAnalysis;
import org.sosy_lab.cpachecker.cpa.bdd.BDDCPA;
import org.sosy_lab.cpachecker.exceptions.CPAEnabledAnalysisPropertyViolationException;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.Pair;

@Options(prefix = "bbpr")
public class BBPRAlgorithm implements Algorithm {

  private final LogManager logger;
  private final ShutdownNotifier shutdownNotifier;

  private final ConfigurableProgramAnalysis cpa;

  private final TransferRelation transferRelation;
  private final MergeOperator mergeOperator;
  private final StopOperator stopOperator;
  private final PrecisionAdjustment precisionAdjustment;

  private final AlgorithmStatus status;

  public BBPRAlgorithm(
      Configuration pConfig,
      LogManager pLogger,
      ShutdownNotifier pShutdownNotifier,
      ConfigurableProgramAnalysis pCpa)
      throws InvalidConfigurationException {
    pConfig.inject(this);

    this.logger = pLogger;
    this.shutdownNotifier = pShutdownNotifier;

    this.cpa = pCpa;
    if (!checkBDDCPA(this.cpa)) {
      throw new InvalidConfigurationException("The given cpa does not contain BDDCPA!");
    }

    this.transferRelation = cpa.getTransferRelation();
    this.mergeOperator = cpa.getMergeOperator();
    this.stopOperator = cpa.getStopOperator();
    this.precisionAdjustment = cpa.getPrecisionAdjustment();

    this.status = AlgorithmStatus.SOUND_AND_PRECISE;
  }

  @Override
  public AlgorithmStatus run(final ReachedSet pReachedSet)
      throws CPAException, InterruptedException, CPAEnabledAnalysisPropertyViolationException {
    try {
      return run0(pReachedSet);
    } finally {
      logger.log(Level.FINER, "Finished exploring the state space");
    }
  }

  private AlgorithmStatus run0(final ReachedSet pReachedSet)
      throws CPAException, InterruptedException {
    // explore all the states until no state can be generated.
    while (pReachedSet.hasWaitingState()) {
      shutdownNotifier.shutdownIfNecessary();

      // obtain the state to be explored and its corresponding precision.
      final AbstractState state = pReachedSet.popFromWaitlist();
      final Precision precision = pReachedSet.getPrecision(state);

      //
      logger.log(Level.FINER, "Retrieved state from waitlist");
      try {
        if(handleState(state, precision, pReachedSet)) {
          // Prec operator requested break
          return status;
        }
      } catch(Exception e) {
        // re-add the old state to the waitlist, there might be unhandled successors left
        // that otherwise would be forgotten (which would be unsound)
        pReachedSet.reAddToWaitlist(state);
        throw e;
      }
    }

    return status;
  }

  /**
   * Handle one state from the waitlist, i.e., produce successors etc.
   *
   * @param state The abstract state that was taken out of the waitlist.
   * @param precision The precision for this abstract state.
   * @param reachedSet The reached set.
   * @return true if analysis should terminate, false if analysis should continue with next state.
   */
  private boolean handleState(
      final AbstractState state,
      final Precision precision,
      final ReachedSet reachedSet)
      throws InterruptedException, CPAException {
    logger.log(Level.ALL, "Current state is", state, "with precision", precision);

    // generate all the successors of current state.
    Collection<? extends AbstractState> successors =
        transferRelation.getAbstractSuccessors(state, precision);

    // adjust each state according to its precision.
    for (Iterator<? extends AbstractState> it = successors.iterator(); it.hasNext();) {
      AbstractState successor = it.next();
      shutdownNotifier.shutdownIfNecessary();
      logger.log(Level.FINER, "Considering successor of current state");
      logger.log(Level.ALL, "Successor of", state, "\nis", successor);

      // perform the precision adjustment at the current successor.
      PrecisionAdjustmentResult precAdjustmentResult =
          precisionAdjustment
              .prec(successor, precision, reachedSet, Functions.identity(), successor)
              .orElseThrow();

      // get the adjusted abstract state and precision.
      successor = precAdjustmentResult.abstractState();
      Precision successorPrecision = precAdjustmentResult.precision();
      Action action = precAdjustmentResult.action();

      // handle BREAK signal.
      if (action == Action.BREAK) {
        // check whether current successor have been covered.
        boolean stop =
            stopOperator.stop(successor, reachedSet.getReached(successor), successorPrecision);

        if (AbstractStates.isTargetState(successor) && stop) {
          // don't signal BREAK for covered states
          // no need to call merge and stop either, so just ignore this state
          // and handle next successor
          logger.log(Level.FINER, "Break was signalled but ignored because the state is covered.");
          continue;
        } else {
          logger.log(Level.FINER, "Break signalled, BBPRAlgorithm will stop.");

          // add the new state.
          reachedSet.add(successor, successorPrecision);

          if (it.hasNext()) {
            // re-add the old state to the waitlist, there are unhandled successors left
            // that otherwise would be forgotten.
            reachedSet.reAddToWaitlist(state);
          }
        }
      }
      assert action == Action.CONTINUE : "Enum Action has unhandled values!";

      // handle CONTINUE signal.
      Collection<AbstractState> reached = reachedSet.getReached(successor);

      // An optimization, we don't bother merging if we know that the
      // merge operator won't do anything (i.e., it is merge-sep).
      if (mergeOperator != MergeSepOperator.getInstance() && !reached.isEmpty()) {
        List<AbstractState> toRemove = new ArrayList<>();
        List<Pair<AbstractState, Precision>> toAdd = new ArrayList<>();

        try {
          logger
              .log(Level.FINER, "Considering", reached.size(), "states from reached set for merge");

          for (AbstractState reachedState : reached) {
            shutdownNotifier.shutdownIfNecessary();
            AbstractState mergedState =
                mergeOperator.merge(successor, reachedState, successorPrecision);

            if (!mergedState.equals(reachedState)) {
              logger.log(Level.FINER, "Successor was merged with state from reached set");
              logger
                  .log(Level.ALL, "Merged", successor, "\nand", reachedState, "\n-->", mergedState);

              toRemove.add(reachedState);
              toAdd.add(Pair.of(mergedState, successorPrecision));
            }
          }
        } finally {
          // If we terminate, we should still update the reachedSet if necessary
          // because ARGCPA doesn't like states in toRemove to be in the reachedSet.
          reachedSet.removeAll(toRemove);
          reachedSet.addAll(toAdd);
        }

        if (mergeOperator instanceof ARGMergeJoinCPAEnabledAnalysis) {
          ((ARGMergeJoinCPAEnabledAnalysis) mergeOperator).cleanUp(reachedSet);
        }
      }

      // check whether current state have been covered.
      boolean stop = stopOperator.stop(successor, reached, successorPrecision);

      if (stop) {
        logger.log(Level.FINER, "Successor is covered or unreachable, not adding to waitlist");
      } else {
        logger.log(Level.FINER, "No need to stop, adding successor ot waitlist");
        reachedSet.add(successor, successorPrecision);
      }
    }

    return false;
  }

  /**
   * Check whether the CPA is or contains BDDCPA.
   *
   * @param cpa The CPA.
   * @return true if it contains BDDCPA.
   */
  private boolean checkBDDCPA(final ConfigurableProgramAnalysis cpa) {
    if(cpa instanceof BDDCPA) {
      return true;
    }
    if (cpa instanceof ARGCPA) {
      BDDCPA bddCPA = ((ARGCPA) cpa).retrieveWrappedCpa(BDDCPA.class);
      if (bddCPA != null) {
        return true;
      }
    }
    return false;
  }

}
