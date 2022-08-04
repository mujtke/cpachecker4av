// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.datarace;

import static com.google.common.collect.FluentIterable.from;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.counterexample.CounterexampleInfo;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Refiner;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGBasedRefiner;
import org.sosy_lab.cpachecker.cpa.arg.ARGCPA;
import org.sosy_lab.cpachecker.cpa.arg.ARGReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.ARGUtils;
import org.sosy_lab.cpachecker.cpa.arg.path.ARGPath;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateAbstractionRefinementStrategy;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateCPA;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateCPARefinerFactory;
import org.sosy_lab.cpachecker.cpa.predicate.RefinementStrategy;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.CPAs;
import org.sosy_lab.cpachecker.util.slicing.Slice;
import org.sosy_lab.cpachecker.util.slicing.Slicer;
import org.sosy_lab.cpachecker.util.slicing.SlicerFactory;

public class DataRaceAbstractionsRefiner implements Refiner {

  private final ARGBasedRefiner refiner;
  private final ARGCPA argCpa;
  private final CFA cfa;

  private Slicer slicer;

  @SuppressWarnings("resource")
  public DataRaceAbstractionsRefiner(
      ConfigurableProgramAnalysis pCpa,
      LogManager pLogger,
      CFA pCfa,
      Slicer pSlicer)
      throws InvalidConfigurationException {
    PredicateCPA predicateCpa = CPAs.retrieveCPA(pCpa, PredicateCPA.class);
    argCpa = CPAs.retrieveCPA(pCpa, ARGCPA.class);

    if (predicateCpa == null) {
      throw new InvalidConfigurationException(
          DataRaceAbstractionsRefiner.class.getSimpleName() + " needs a PredicateCPA");
    }

    RefinementStrategy strategy =
        new PredicateAbstractionRefinementStrategy(
            predicateCpa.getConfiguration(),
            pLogger,
            predicateCpa.getPredicateManager(),
            predicateCpa.getSolver());

    PredicateCPARefinerFactory factory = new PredicateCPARefinerFactory(pCpa);
    refiner = factory.create(strategy);

    cfa = pCfa;
    slicer = pSlicer;
  }

  @SuppressWarnings("resource")
  public static Refiner create(
      ConfigurableProgramAnalysis pCpa,
      LogManager pLogger,
      ShutdownNotifier pShutdownNotifier)
      throws InvalidConfigurationException {
    DataRaceCPA drCPA = CPAs.retrieveCPA(pCpa, DataRaceCPA.class);

    SlicerFactory slicerFactory = new SlicerFactory();
    Slicer tmpSlicer = null;
    try {
      tmpSlicer =
          slicerFactory
              .create(pLogger, pShutdownNotifier, drCPA.getConfiguration(), drCPA.getCFA());
    } catch (InterruptedException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    
    return new DataRaceAbstractionsRefiner(pCpa, pLogger, drCPA.getCFA(), tmpSlicer);
  }

  @Override
  public boolean performRefinement(ReachedSet pReached) throws CPAException, InterruptedException {

    // filter out the target states.
    ImmutableList<AbstractState> tgtStates =
        from(pReached)
            .filter(s -> AbstractStates.extractStateByType(s, DataRaceState.class).isDataRace())
            .toList();

    Precision defPrecision = pReached.getPrecision(pReached.getFirstState());
    for (AbstractState s : tgtStates) {
      if (!pReached.getWaitlist().contains(s)) {
        pReached.add(s, defPrecision);
      }
    }

    boolean isAllRealCex = true;
    for (AbstractState tgtState : tgtStates) {
      ARGPath errPath = ARGUtils.getShortestPathTo((ARGState) tgtState);
      ARGReachedSet reached = new ARGReachedSet(pReached, argCpa);
      ARGPath sliceResult = genSlicedPath(errPath);

      CounterexampleInfo counterexample = refiner.performRefinementForPath(reached, errPath);

      if (counterexample.isSpurious()) {
        isAllRealCex = false;
      }
    }

    if (isAllRealCex) {
      return false;
    } else {
      return true;
    }

  }

  private ARGPath genSlicedPath(ARGPath pPath) {
    ImmutableList<CFAEdge> asuEdges =
        from(pPath.getFullPath()).filter(e -> e instanceof AssumeEdge).toList();

    try {
      Slice sliceResult = slicer.getSlice(cfa, asuEdges);
      ImmutableSet<CFAEdge> relEdges = sliceResult.getRelevantEdges();
      System.out.println(relEdges);
    } catch (InterruptedException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }
    
    return null;
  }

}
