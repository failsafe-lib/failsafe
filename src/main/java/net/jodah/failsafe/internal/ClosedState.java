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

import net.jodah.failsafe.CircuitBreaker;
import net.jodah.failsafe.CircuitBreaker.State;
import net.jodah.failsafe.ExecutionContext;

public class ClosedState<R> extends CircuitState<R> {
  public ClosedState(CircuitBreakerImpl<R> breaker) {
    super(breaker, CircuitStats.create(breaker, capacityFor(breaker), true, null));
  }

  @Override
  public boolean allowsExecution() {
    return true;
  }

  @Override
  public State getState() {
    return State.CLOSED;
  }

  @Override
  public synchronized void handleConfigChange() {
    stats = CircuitStats.create(breaker, capacityFor(breaker), true, stats);
  }

  /**
   * Checks to see if the executions and failure thresholds have been exceeded, opening the circuit if so.
   */
  @Override
  synchronized void checkThreshold(ExecutionContext<R> context) {
    // Execution threshold will only be set for time based thresholding
    if (stats.getExecutionCount() >= config.getFailureExecutionThreshold()) {
      double failureRateThreshold = config.getFailureRateThreshold();
      if ((failureRateThreshold != 0 && stats.getFailureRate() >= failureRateThreshold) || (failureRateThreshold == 0
        && stats.getFailureCount() >= config.getFailureThreshold()))
        breaker.open(context);
    }
  }

  /**
   * Returns the capacity of the breaker in the closed state.
   */
  private static int capacityFor(CircuitBreaker<?> breaker) {
    if (breaker.getConfig().getFailureExecutionThreshold() != 0)
      return breaker.getConfig().getFailureExecutionThreshold();
    else
      return breaker.getConfig().getFailureThresholdingCapacity();
  }
}