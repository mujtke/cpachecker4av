// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.observation;

import static com.google.common.collect.FluentIterable.from;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.util.Pair;

public class ObservationState implements AbstractState {

  // The observation function O: r_event_id -> w_event_id.
  // NOTICE: 1) each read/write event has its' unique identifier.
  // 2) the observation function is a surjection but not an injection function.
  private Map<Integer, Integer> observation;

  // Current write events: W: variable_name -> w_event_id.
  // NOTICE: if a variable is updated by certain statement, this map will be updated accordingly.
  private Map<SSAVariable, Integer> writes;

  public static ObservationState getInitialInstance() {
    return new ObservationState();
  }

  public ObservationState() {
    observation = new HashMap<>();
    writes = new HashMap<>();
  }

  public ObservationState(final ObservationState pOther) {
    observation = Maps.newHashMap(pOther.getObservation());
    writes = Maps.newHashMap(pOther.getWrites());
  }

  /**
   * Update the current write event set if the current edge 'writes' a global variable.
   *
   * @param pWVariable The write-access global variable.
   * @param pWEventId The write event id.
   * @apiNote control logic is managed by {@link ObservationTransferRelation}.
   */
  public void updateCurrentWrites(final SSAVariable pWVariable, final Integer pWEventId) {
    writes.put(pWVariable, pWEventId);
  }

  /**
   * Update the observation function if the read event accesses a previously written global variable
   * written before.
   *
   * @param pReadEventId The read event id.
   * @param pWriteEventId The write event id.
   * @apiNote These two event should access the same global variable. Meanwhile, if there is no
   *          write event in current write event set, then this read-write pair will not add into
   *          the observation function.
   */
  public void updateObservationFunction(final Integer pReadEventId, final Integer pWriteEventId) {
    if (writes.containsValue(pWriteEventId)) {
      observation.put(pReadEventId, pWriteEventId);
    }
  }

  public Map<Integer, Integer> getObservation() {
    return observation;
  }

  public Map<SSAVariable, Integer> getWrites() {
    return writes;
  }

  /**
   * Get the largest subscript SSA variable of pVar.
   *
   * @param pVar The write variable.
   * @return Return null if there are no variable equals to pVar.
   */
  public Pair<SSAVariable, Integer> getLatestSSAWriteVariable(final String pVar) {
    if ((pVar != null) && !pVar.isEmpty()) {
      ImmutableSet<SSAVariable> vars =
          from(writes.keySet()).filter(v -> v.isAnInstanceOf(pVar)).toSet();

      // There are no variable equals to pVar.
      if (vars.isEmpty()) {
        return null;
      }

      // At least one variable equals to pVar.
      SSAVariable latestWrite =
          Collections.max(
          vars,
          (SSAVariable v1, SSAVariable v2) -> v1.getSubscript().compareTo(v2.getSubscript()));
      return Pair.of(latestWrite, writes.get(latestWrite));
    }

    return null;
  }

  @Override
  public int hashCode() {
    // TODO Auto-generated method stub
    return Objects.hash(observation, writes);
  }

  @Override
  public boolean equals(Object pObj) {
    // TODO Auto-generated method stub
    if (this == pObj) {
      return true;
    }
    if (pObj == null || !(pObj instanceof ObservationState)) {
      return false;
    }

    ObservationState other = (ObservationState) pObj;
    return observation.equals(other.observation) && writes.equals(other.writes);
  }

  @Override
  public String toString() {
    String results = "{";
    for (Entry<Integer, Integer> e : observation.entrySet()) {
      results += "(" + e.getKey() + ", " + e.getValue() + ")";
    }
    results += "}";

    return results;
  }

}
