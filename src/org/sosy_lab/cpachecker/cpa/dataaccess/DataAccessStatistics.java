package org.sosy_lab.cpachecker.cpa.dataaccess;

import org.sosy_lab.cpachecker.core.CPAcheckerResult;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.util.statistics.*;

import java.io.PrintStream;


public class DataAccessStatistics implements Statistics {

    public final ThreadSafeTimerContainer time = new ThreadSafeTimerContainer("Time for DataAccessTransferRelation");

    @Override

    public void printStatistics(PrintStream out, CPAcheckerResult.Result result, UnmodifiableReachedSet reached) {

        System.out.println("\u001b[32mtime for TransferRelation:\u001b[0m      \u001b[33m" + time + "\u001b[0m");
    }

    @Override
    public String getName() {
        return "DataAccessCPA";
    }
}





