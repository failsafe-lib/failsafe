/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package net.jodah.failsafe.internal;

import net.jodah.failsafe.CircuitBreaker.State;
import net.jodah.failsafe.ExecutionContext;
import net.jodah.failsafe.ExecutionResult;
import net.jodah.failsafe.internal.util.CircularBitSet;
import net.jodah.failsafe.util.Ratio;

/**
 * The state of a circuit.
 *
 * @author Jonathan Halterman
 */
public abstract class CircuitState {
  static final Ratio ONE_OF_ONE = new Ratio(1, 1);

  protected CircularBitSet bitSet;

  public abstract boolean allowsExecution();

  public abstract State getInternals();

  public int getFailureCount() {
    return bitSet.negatives();
  }

  public Ratio getFailureRatio() {
    return bitSet.negativeRatio();
  }

  public int getSuccessCount() {
    return bitSet.positives();
  }

  public Ratio getSuccessRatio() {
    return bitSet.positiveRatio();
  }

  public void recordFailure(ExecutionResult result, ExecutionContext context) {
  }

  public void recordSuccess() {
  }

  public void setFailureThreshold(Ratio threshold) {
  }

  public void setSuccessThreshold(Ratio threshold) {
  }
}