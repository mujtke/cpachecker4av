/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2020  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.sosy_lab.cpachecker.cpa.influence;

import com.google.common.collect.ImmutableSet;
import java.util.HashSet;
import java.util.Set;

public class GlobalVarReadWritePair {

  //
  private Set<String> gReadVars;
  private Set<String> gWriteVars;

  public GlobalVarReadWritePair() {
    gReadVars = new HashSet<>();
    gWriteVars = new HashSet<>();
  }

  public GlobalVarReadWritePair(final Set<String> gRVars, final Set<String> gWVars) {
    gReadVars = ImmutableSet.copyOf(gRVars);
    gWriteVars = ImmutableSet.copyOf(gWVars);
  }

  public static GlobalVarReadWritePair empty() {
    return new GlobalVarReadWritePair();
  }

  public Set<String> getgReadVars() {
    return gReadVars;
  }

  public Set<String> getgWriteVars() {
    return gWriteVars;
  }

  @Override
  public String toString() {
    return "(" + gReadVars + ", " + gWriteVars + ")";
  }

}