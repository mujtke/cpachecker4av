package org.sosy_lab.cpachecker.cpa.dataaccess;

import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.defaults.SingleEdgeTransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.dependence.conditional.ConditionalDepGraph;
import org.sosy_lab.cpachecker.util.dependence.conditional.EdgeVtx;
import org.sosy_lab.cpachecker.util.dependence.conditional.Var;
import org.sosy_lab.cpachecker.util.globalinfo.EdgeInfo;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.cpachecker.util.statistics.ThreadSafeTimerContainer;

import java.util.*;

@Options(prefix = "cpa.dataaccess")
public class DataAccessTransferRelation extends SingleEdgeTransferRelation {

    private ConditionalDepGraph conDepGraph;  // 用于获取边上信息
    private Configuration config;
    private Statistics stats;
    private ThreadSafeTimerContainer.TimerWrapper transferTimer;

    private EdgeInfo edgeInfo;
    boolean reachMainFunc;

    @Option(secure = true, name = "MAIN_LINE", description = "The main function begin line")
    private int MAIN_LINE;

    public DataAccessTransferRelation(Configuration pConfig, Statistics pStatistics) throws InvalidConfigurationException {
        pConfig.inject(this);
        conDepGraph = GlobalInfo.getInstance().getEdgeInfo().getCondDepGraph();
        config = pConfig;
        stats = pStatistics;
        transferTimer = ((DataAccessStatistics) stats).time.getNewTimer();
        edgeInfo = GlobalInfo.getInstance().getEdgeInfo();
        reachMainFunc = false;
    }



    @Override
    public Collection<DataAccessState> getAbstractSuccessorsForEdge(AbstractState pstate, Precision precision, CFAEdge pCfaEdge) throws CPATransferException, InterruptedException {
        /**
         * @param state 父节点的信息
         * @param precision 精度，这用不到
         * @param cfaEdge 边上的信6息
         */
        // count time for transfer relation
        transferTimer.start();
        try {
            // 将父节点中的 Dataaccess 取出
            DataAccessState lastDataAccess = (DataAccessState) pstate;

            // 若没进入函数主题部分，则不进行冲突性检测

            EdgeVtx edgeVtx = (EdgeVtx) conDepGraph.getDGNode(pCfaEdge.hashCode());

            if(!reachMainFunc){
                reachMainFunc = edgeInfo.getCFA().getMainFunction().getFileLocation().equals(pCfaEdge.getFileLocation());
            }

            if (!reachMainFunc || edgeVtx == null) {
                lastDataAccess.setStr("No share variable.\n\n");
                return Collections.singleton(lastDataAccess);
            }

            DataAccessState dataAccess = new DataAccessState(lastDataAccess.getDataAccess(), lastDataAccess.getDataRace(), lastDataAccess.getpathFunc(), lastDataAccess.getPathNum());

            // 获取边上的共享节点的信息
            String pathNum = pCfaEdge.getFileLocation().toString();
            dataAccess.setPathNum(pathNum);

            // 得到读写信息
            Set<Var> gRVars = edgeVtx.getgReadVars(), gWVars = edgeVtx.getgWriteVars();

            // 得到边所在的函数名
            String task = edgeVtx.getBlockStartEdge().getPredecessor().getFunctionName();
            dataAccess.setPath(task);

            // 先判断读   因为对于任何一条语句， 无论怎样都是先读后写
            if (!gRVars.isEmpty()) {
                for (Var var : gRVars) {
                    int location = var.getExp().getFileLocation().getEndingLineNumber();
                    State ec = new State(var.getName(), task, location, "R");

                    // 进行数据冲突检测
                    dataAccess.DataRace(ec);
                }
            }

            // 后判断写
            if (!gWVars.isEmpty()) {
                for (Var var : gWVars) {

                    int location = var.getExp().getFileLocation().getEndingLineNumber();
                    State ec = new State(var.getName(), task, location, "W");

                    // 进行数据冲突检测
                    dataAccess.DataRace(ec);
                }
            }

            return Collections.singleton(dataAccess);

        }finally {
            // anyway, stop the timer when transfer process finished.
            transferTimer.stop();
        }
    }


    public Statistics getStatistics() {
        return stats;
    }
}

