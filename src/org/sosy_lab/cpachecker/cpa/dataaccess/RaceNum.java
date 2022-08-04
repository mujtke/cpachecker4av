package org.sosy_lab.cpachecker.cpa.dataaccess;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class RaceNum {
    private int raceNum = 0;
    private int raceRWR = 0;
    private int raceWWR = 0;
    private int raceRWW = 0;
    private int raceWRW = 0;

    private Set<String> raceSet;
    private Set<String> raceRWRSet;
    private Set<String> raceWWRSet;
    private Set<String> raceRWWSet;
    private Set<String> raceWRWSet;


    public RaceNum() {
        raceNum = 0;
        raceWRW = 0;
        raceWWR = 0;
        raceRWR = 0;
        raceRWW = 0;

        raceSet = new HashSet<String>();
        raceRWRSet = new HashSet<String>();
        raceWWRSet = new HashSet<String>();
        raceRWWSet = new HashSet<String>();
        raceWRWSet = new HashSet<String>();
    }

    public int getRaceNum() {
        return raceNum;
    }

    public void setRaceNum(int raceNum) {
        this.raceNum = raceNum;
    }

    public void addraceNum() {
        raceNum += 1;
    }

    public void addraceWRW() {
        raceWRW += 1;
    }

    public void addraceWWR() {
        raceWWR += 1;
    }

    public void addraceRWR() {
        raceRWR += 1;
    }

    public void addraceRWW() {
        raceRWW += 1;
    }


    public void setRace(String race) {
        if (raceSet.contains(race)) {
            return;
        }
        raceSet.add(race);
        raceNum += 1;
    }

    public void setraceRWRSet(String race) {
        if (raceRWRSet.contains(race)) {
            return;
        }
        raceRWRSet.add(race);
        raceRWR += 1;
    }

    public void setraceWWRSet(String race) {
        if (raceWWRSet.contains(race)) {
            return;
        }
        raceWWRSet.add(race);
        raceWWR += 1;
    }

    public void setraceRWWSet(String race) {
        if (raceRWWSet.contains(race)) {
            return;
        }
        raceRWWSet.add(race);
        raceRWW += 1;
    }

    public void setraceWRWSet(String race) {
        if (raceWRWSet.contains(race)) {
            return;
        }
        raceWRWSet.add(race);
        raceWRW += 1;
    }


    @Override
    public String toString() {
        return
                "\n\033[31m" +
                        "=========================== Conflicts checked =============================" +
                        "\nThe total number of conflicts is" + raceNum +
                        "\nRWR conflicts have " + raceRWR +
                        "\nWRW conflicts have " + raceWRW +
                        "\nRWW conflicts have " + raceRWW +
                        "\nWWR conflicts have " + raceWWR +
                        "\n\033[0m" +
                        "=========================== 详细状态如下 =============================" +
                        "\n raceRWRSet:\n" + raceRWRSet +
                        "\n raceWRWSet:\n" + raceWRWSet +
                        "\n raceRWWSet:\n" + raceRWWSet +
                        "\n raceWWRSet:\n" + raceWWRSet;

    }

    @Override
    public int hashCode() {
        return Objects.hash();
    }
}
