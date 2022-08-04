package org.sosy_lab.cpachecker.cpa.dataaccess;


import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.defaults.AbstractCPA;
import org.sosy_lab.cpachecker.core.defaults.AutomaticCPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.*;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.cpa.datarace2bdd.DataRaceTransferRelation;
import org.sosy_lab.cpachecker.cpa.thread.ThreadTransferRelation;

import java.util.Collection;

public class DataAccessCPA extends AbstractCPA implements ConfigurableProgramAnalysis,StatisticsProvider{

    public static RaceNum raceNum;
    private Statistics stats;
    private DataAccessTransferRelation transfer;

    public static CPAFactory factory() {
        return AutomaticCPAFactory.forType(DataAccessCPA.class);
    }

    public DataAccessCPA(Configuration pConfig) throws InvalidConfigurationException {
        super("sep", "sep", new DataAccessTransferRelation(pConfig, new DataAccessStatistics()));
        transfer = (DataAccessTransferRelation) super.getTransferRelation();
        stats = transfer.getStatistics();
        raceNum = new RaceNum();
    }

    @Override
    public AbstractState getInitialState(CFANode node, StateSpacePartition partition) throws InterruptedException {
        return DataAccessState.getInitialInstance();
    }

    @Override
    public void collectStatistics(Collection<Statistics> statsCollection) {
        statsCollection.add(stats);
    }
}
