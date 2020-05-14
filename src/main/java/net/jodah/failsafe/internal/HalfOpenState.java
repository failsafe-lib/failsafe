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

public class HalfOpenState extends CircuitState {
  private final CircuitBreakerInternals internals;

  public HalfOpenState(CircuitBreaker breaker, CircuitBreakerInternals internals) {
    super(breaker, CircuitStats.create(breaker, capacityFor(breaker), false, null));
    this.internals = internals;
  }

  /**
   * Ensures that the current executions are less than the thresholding capacity.
   */
  @Override
  public boolean allowsExecution() {
    return internals.getCurrentExecutions() < capacityFor(breaker);
  }

  @Override
  public State getState() {
    return State.HALF_OPEN;
  }

  @Override
  public synchronized void handleConfigChange() {
    stats = CircuitStats.create(breaker, capacityFor(breaker), false, stats);
  }

  /**
   * Checks to determine if a threshold has been met and the circuit should be opened or closed.
   *
   * <p>
   * If a success threshold is configured, the circuit is opened or closed based on whether the ratio was exceeded.
   * <p>
   * Else the circuit is opened or closed based on whether the failure threshold was exceeded.
   */
  @Override
  synchronized void checkThreshold(ExecutionContext context) {
    boolean successesExceeded;
    boolean failuresExceeded;

    int successThreshold = breaker.getSuccessThreshold();
    if (successThreshold != 0) {
      int successThresholdingCapacity = breaker.getSuccessThresholdingCapacity();
      successesExceeded = stats.getSuccessCount() >= successThreshold;
      failuresExceeded = stats.getFailureCount() > successThresholdingCapacity - successThreshold;
    } else {
      int failureRateThreshold = breaker.getFailureRateThreshold();
      if (failureRateThreshold != 0) {
        boolean executionThresholdExceeded = stats.getExecutionCount() >= breaker.getFailureExecutionThreshold();
        failuresExceeded = executionThresholdExceeded && stats.getFailureRate() >= failureRateThreshold;
        successesExceeded = executionThresholdExceeded && stats.getSuccessRate() > 100 - failureRateThreshold;
      } else {
        int failureThresholdingCapacity = breaker.getFailureThresholdingCapacity();
        int failureThreshold = breaker.getFailureThreshold();
        failuresExceeded = stats.getFailureCount() >= failureThreshold;
        successesExceeded = stats.getSuccessCount() > failureThresholdingCapacity - failureThreshold;
      }
    }

    if (successesExceeded)
      breaker.close();
    else if (failuresExceeded)
      internals.open(context);
  }

  /**
   * Returns the capacity of the breaker in the half-open state.
   */
  private static int capacityFor(CircuitBreaker<?> breaker) {
    int capacity = breaker.getSuccessThresholdingCapacity();
    if (capacity == 0)
      capacity = breaker.getFailureExecutionThreshold();
    if (capacity == 0)
      capacity = breaker.getFailureThresholdingCapacity();
    return capacity;
  }
}