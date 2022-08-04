package org.sosy_lab.cpachecker.cpa.dataaccess;

import java.util.ArrayList;
import java.util.List;

public class Interruptinformation {
    public Integer epPosition;
    public List<String> interOperation;
    public List<State> interState;

    private List<String> interLocation;


    public Interruptinformation() {

    }

    public Integer getepPosition() {
        return epPosition;
    }

    public void setepPosition(Integer epPosition) {
        this.epPosition = epPosition;
    }

    public List<String> getInterOperation() {
        return interOperation;
    }

    public void setInterOperation(String interOperation) {
        this.interOperation.add(interOperation);
    }

    public List<State> getInterState() {
        return interState;
    }

    public void setInter_state(State inter_state) {
        this.interState.add(inter_state);
    }

    public List<String> getInterLocation() {
        return interLocation;
    }

    public void setInterLocation(String interLocation) {
        this.interLocation.add(interLocation);
    }

    public int getIndexInterOperation(String Action) {
        for (int i=0;i<interOperation.size();i++) {
            if (interOperation.get(i) == Action) {
                return i;
            }
        }
        return -1;
    }
}
