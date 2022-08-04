package org.sosy_lab.cpachecker.cpa.dataaccess;

import java.util.*;

public class DataState {
    /**
     * 数据访问集中存放的类型及相关方法
     */
    private String N;
    private State as;
    private Map<String, List<State>> interState;


    public DataState(String Name) {
        /* 初始化 DataAccess[var]=[] */
        N = Name;
        as = new State();
        this.interState = new LinkedHashMap<String, List<State>>();
    }

    public DataState(String Name, State as) {
        N = Name;
        this.as = as;
        this.interState = new LinkedHashMap<String, List<State>>();
    }

    public DataState(String Name, State as, String flag) {
        if (flag == "isr") {
            N = Name;
            this.as = new State();
            this.interState = new LinkedHashMap<String, List<State>>();
            List action = new ArrayList<State>();
            action.add(as);
            interState.put(as.getTask(), action);
        }
    }

    public DataState(String n, State as, Map<String, List<State>> interState) {
        N = n;
        this.as = as;
        this.interState = new LinkedHashMap<>(interState);
    }

    public String getN() {
        return N;
    }

    public void setN(String n) {
        N = n;
    }

    public State getAs() {
        return as;
    }

    public void setAs(State as) {
        this.as = as;
    }

    public Map<String, List<State>> getInterState() {
        return interState;
    }

    public void setInterState(State a) {
        if (!interState.containsKey(a.getTask())) {  // 没有此中断函数
            List<State> action = new ArrayList<State>();
            action.add(a);
            interState.put(a.getTask(), action);
        } else {  // 拥有此中断函数
            interState.get(a.getTask()).add(a);
        }
    }

    public void updataActionList(State a) {
        if (a.getTask().contains("isr")) {
            this.setInterState(a);
        } else {
            as = a;
        }
    }

    public void getEmpty(State a) {
        as = a;
        interState = new LinkedHashMap<String, List<State>>();
    }

    @Override
    public String toString() {
        StringBuffer str = new StringBuffer();
        str.append("\n对于 " + N + " 来说:" + "\n                 在非中断函数的最新访问状态是" + as + "\n                 在中断函数的访问状态集是" + interState);
        return String.valueOf(str);
    }

}
