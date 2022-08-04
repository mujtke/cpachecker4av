package org.sosy_lab.cpachecker.cpa.dataaccess;

import org.checkerframework.checker.units.qual.A;

public class State {
    /**
     * 每一个节点应包含的状态
     */
    private String Name;
    private String Task;
    private int Loaction;
    private String Action;

    public State() {
        Name = "";
        Task = "";
        Loaction = -1;
        Action = "";
    }

    public State(String name, String task, int loaction, String action) {
        Name = name;
        Task = task;
        Loaction = loaction;
        Action = action;
    }

    public String getName() {
        return Name;
    }

    public void setName(String name) {
        Name = name;
    }

    public String getTask() {
        return Task;
    }

    public void setTask(String task) {
        Task = task;
    }

    public int getLoaction() {
        return Loaction;
    }

    public void setLoaction(int loaction) {
        Loaction = loaction;
    }

    public String getAction() {
        return Action;
    }

    public void setAction(String action) {
        Action = action;
    }

    @Override
    public String toString() {
        return "(" + Name + "," + Task + ", '" + Loaction + ", '" + Action + ')';
    }

    public boolean isEmpty() {
        if ((Name == "") && (Task == "" ) && (Loaction == -1) && (Action == "")){
            return true;
        }else{
            return false;
        }
    }
}
