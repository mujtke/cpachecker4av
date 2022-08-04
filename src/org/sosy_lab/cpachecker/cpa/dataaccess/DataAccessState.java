package org.sosy_lab.cpachecker.cpa.dataaccess;


import org.sosy_lab.cpachecker.core.interfaces.AbstractState;

import java.util.*;
import java.util.ArrayList;
import java.util.List;

//
//public class DataAccessState implements AbstractState, Graphable {
public class DataAccessState implements AbstractState {
    private List<DataState> dataAccess;   // 数据访问集

    private Map<String, List<List<State>>> dataRace;   // 用于存放当前路径所产生的数据冲突对

    private List<String> pathFunc;     //  用于存放经历的函数

    private List<String> pathNum;       // 用于存放经历的行数

    private String str;   // 用于对当前状态产生说明性语句.

    private boolean isRace = false;   // 判断是否产生了数据冲突，用于更新数据集

    String[][] patternList = {{"R", "W", "R"}, {"W", "W", "R"}, {"R", "W", "W"}, {"W", "R", "W"}};    // 冲突模式集
    // 构造方法

    public static DataAccessState getInitialInstance() {
        return new DataAccessState();
    }

    public DataAccessState() {
        dataAccess = new ArrayList<DataState>();
        dataRace = new HashMap<String, List<List<State>>>();
        pathFunc = new ArrayList<String>();
        pathNum = new ArrayList<String>();
    }

    //
    public DataAccessState(List<DataState> dataAccess, Map<String, List<List<State>>> dataRace, List<String> pathFunc, List<String> pathNum) {
        this.dataAccess = newData(dataAccess);
        this.dataRace = new HashMap<>(dataRace);
        this.pathFunc = new ArrayList<>(pathFunc);
        this.pathNum = new ArrayList<>(pathNum);
    }

    public List<DataState> newData(List<DataState> dataAccess) {
        List<DataState> res = new ArrayList<DataState>();
        for (DataState tmp : dataAccess) {
//            res.add(new DataState(tmp.getN(), tmp.getAs(), tmp.getInterOperation(), tmp.getInterState()));
            res.add(new DataState(tmp.getN(), tmp.getAs(), tmp.getInterState()));
        }
        return res;
    }

    // dataAccess的方法

    public List<DataState> getDataAccess() {
        return dataAccess;
    }

    public void setDataAccess(State e) {
        if (dataAccess.isEmpty()) {
            dataAccess.add(productDataState(e));
            return;
        }

        boolean notIsExists = true;
        for (DataState data : dataAccess) {
            if (data.getN() == e.getName()) {
                notIsExists = false;
                data.updataActionList(e);
            }
        }

        if (notIsExists) {
            dataAccess.add(productDataState(e));
            return;
        }
    }

    public DataState productDataState(State e) {
        if (e.getTask().contains("isr")) {
            return new DataState(e.getName(), e, "isr");
        } else {
            return new DataState(e.getName(), e);
        }
    }


    public void add(DataState e) {
        dataAccess.add(e);
    }

    public int Is_exist(String Name) {
        /**
         * 判断当前 Name 是否在数据访问集中, 在返回序号，不在返回-1
         */

        for (int i = 0; i <= dataAccess.size(); i++) {
            if (dataAccess.get(i).getN() == Name) return i;
        }
        return -1;
    }

    public int actionListPosition(String Name) {
        /**
         * 判断当前 Name 是否在数据访问集中, 在返回序号，不在返回-1
         */

        for (int i = 0; i < dataAccess.size(); i++) {
            if (dataAccess.get(i).getN().equals(Name)) return i;
        }

        return -1;
    }

    // dataRace 的方法
    public Map<String, List<List<State>>> getDataRace() {
        return dataRace;
    }


    public boolean isInDataRace(String Name) {
        return dataRace.containsKey(Name);
    }


    public void setDataRace(String Name, List<State> race) {
        if (dataRace.containsKey(Name)) {
            dataRace.get(Name).add(race);
        } else {
            List<List<State>> array = new ArrayList<>();
            array.add(race);
            dataRace.put(Name, array);
        }

    }

    // path 的方法
    public List<String> getpathFunc() {
        return pathFunc;
    }

    public void setPath(String road) {

        if (pathFunc.isEmpty()) {
            pathFunc.add(road);
        } else if (pathFunc.get(pathFunc.size() - 1) == road) {
        } else if (pathFunc.contains(road)) {
            for (int i = 0; i < pathFunc.size(); i++) {
                if (pathFunc.get(i) == road) {
                    break;
                }
            }
        } else {
            pathFunc.add(road);
        }
    }

    public List<String> getPathNum() {
        return pathNum;
    }

    public void setPathNum(String pathNum) {
        this.pathNum.add(pathNum);
    }

    public void poppath(int idx) {
        for (int i = idx + 1; i < pathFunc.size(); i++) {
            pathFunc.remove(i);
        }
    }

    public List<String> involvedPaths(String road) {
        List<String> re = new ArrayList<String>();
        for (int i = pathFunc.size() - 1; i >= 0; i--) {
            if (pathFunc.get(i) == road) {
                break;
            }
            re.add(pathFunc.get(i));
        }
        return re;
    }

    public void disposePath(State a, String mainFunction) {
        if (a.getTask() != mainFunction) return;

        int index = isExistPath(a.getTask());
        if (index != -1) {
            poppath(index);
        }
    }

    public int isExistPath(String Task) {
        for (int i = pathFunc.size() - 2; i >= 0; i--) {
            if (pathFunc.get(i) == Task) {
                return i;
            }
        }

        return -1;
    }

    public String getStr() {
        return str;
    }

    public void setStr(String str) {
        this.str = str;
    }


    public boolean isRace() {
        return isRace;
    }

    public void setRace(boolean race) {
        isRace = race;
    }


    /**
     * 冲突检测
     *
     * @param a 当前状态
     * @return 返回一个数据冲突对 (as,am,ae)，或返回一个 null
     */
    public void DataRace(State a) {

        int index = this.actionListPosition(a.getName());

        if (index == -1) {
            setDataAccess(a);
            str = "Add this state: " + a;
            return;
        }

        DataState actionList = dataAccess.get(index);

        // 判断是否为中断函数
        if (a.getTask().contains("isr")) {
            isrDataRace(actionList, a);
        } else {
            mainDataRace(actionList, a);
        }
    }

    public void mainDataRace(DataState actionList, State a) {
        // 判断 ep 是否为空或interstate内是否有数
        if (actionList.getAs().isEmpty()) {
            actionList.getEmpty(a);
            str = "For share_var " + a.getName() + ", not enough elements in DataAccess";
            return;
        }else if(actionList.getInterState().isEmpty()){
            actionList.setAs(a);
            str = "For share_var " + a.getName() + ", not enough elements in DataAccess";
            return;
        }

        for (Map.Entry entry : actionList.getInterState().entrySet()) {
            List<State> isrStateList = (List<State>) entry.getValue();

            for (State am : isrStateList) {
                realDataRace(actionList.getAs(), am, a);
            }
        }

        actionList.getEmpty(a);
    }

    void isrDataRace(DataState actionList, State a) {
        Map<String, List<State>> interState = actionList.getInterState();

        if (!interState.containsKey(a.getTask()) || (a.getTask() == pathFunc.get(pathFunc.size() - 1))) {
            actionList.setInterState(a);
        }

        State as = new State();
        boolean getAs = false;
        int index = pathFunc.indexOf(a.getTask());
        if (interState.containsKey(a.getTask())) {
            as = interState.get(a.getTask()).get(interState.get(a.getTask()).size() - 1);
            getAs = true;
        } else {
            for (int i = index - 1; i >= 0; i--) {
                if (interState.containsKey(a.getTask())) {
                    as = interState.get(a.getTask()).get(interState.get(a.getTask()).size() - 1);
                    getAs = true;
                }
            }
        }

        if(getAs){
            for (int i = index + 1; i < pathFunc.size(); i++) {
                if (!pathFunc.get(i).contains("isr")) {
                    continue;
                }

                List<State> highIsrStateList = interState.get(pathFunc.get(i));
                if (highIsrStateList != null) {
                    for (State am : highIsrStateList) {
                        realDataRace(as, am, a);
                    }
                }
            }
        }


        actionList.setInterState(a);
    }

    public void realDataRace(State as, State am, State ae) {
        String[] pattern = {as.getAction(), am.getAction(), ae.getAction()};
        // 冲突检测，是否有 patternList 中的 pattern
        for (int j = 0; j < 4; j++) {
            if (Arrays.equals(patternList[j], pattern)) {
                str = "\n   For the three access states {" + as.toString() + "," + am.toString() + "," + ae.toString() + "} of the variable " + as.getName() + " a data conflict occurs";
                isRace = true;

                List<State> race = new ArrayList<State>();
                race.add(as);
                race.add(am);
                race.add(ae);

                setDataRace(as.getName(), race);

                String str = getRaceString(as.getName(), race);

                if (j == 0) {
                    DataAccessCPA.raceNum.setraceRWRSet(str);
                } else if (j == 1) {
                    DataAccessCPA.raceNum.setraceWWRSet(str);
                } else if (j == 2) {
                    DataAccessCPA.raceNum.setraceRWWSet(str);
                } else if (j == 3) {
                    DataAccessCPA.raceNum.setraceWRWSet(str);
                }
                DataAccessCPA.raceNum.setRace(str);
            }
        }
    }


    public void toprint() {
        if (!isRace) {
            System.out.println(str);

//            System.out.println("\nNow the DataAccess is :");
//            System.out.println(getString());
            System.out.println("\n\n");
            return;
        }
        isRace = false;
        System.out.println("\n\033[31m" + str);
        System.out.println("\nNow the DataAccess is :");
        System.out.println(getString());
        System.out.println("\n\n\033[0m");
    }


    public String getRaceString(String Name, List<State> race) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n" + Name + ":\n");
        for (State a : race) {
            sb.append("\n       " + a);
        }
        return sb.toString();
    }

    public String getString() {
        return "DataAccess=" + dataAccess.toString() + ", \nDataRace=" + dataRace.toString() + ", \npathFunc = " + pathFunc.toString() + ", \npathNum = " + pathNum.toString() + "\n\n";
    }

    @Override
    public String toString() {
        return "DataAccessState{" + "\ndataAccess=" + dataAccess + ", \ndataRace=" + dataRace + ", \npathFunc=" + pathFunc + ", \npathNum=" + pathNum + ", \nstr='" + str + ", \nisRace=" + isRace + '}';
    }

//    @Override
//    public String toDOTLabel() {
//        StringBuilder sb = new StringBuilder();
//        sb.append("\n--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------\n");
//        sb.append(str).append("\n").append(getString());
//        sb.append(pathFunc).append("\n").append(pathNum);
//        return sb.toString();
//    }
//
//    @Override
//    public boolean shouldBeHighlighted() {
//        if (str.toString().contains("three access states")) {
//            return true;
//        }
//        return false;
//    }

}


