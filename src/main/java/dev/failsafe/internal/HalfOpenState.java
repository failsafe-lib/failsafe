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
package dev.failsafe.internal;

import dev.failsafe.CircuitBreaker;
import dev.failsafe.CircuitBreaker.State;
import dev.failsafe.ExecutionContext;

import java.util.concurrent.atomic.AtomicInteger;

public class HalfOpenState<R> extends CircuitState<R> {
  protected final AtomicInteger permittedExecutions = new AtomicInteger();

  public HalfOpenState(CircuitBreakerImpl<R> breaker) {
    super(breaker, CircuitStats.create(breaker, capacityFor(breaker), false, null));
    permittedExecutions.set(capacityFor(breaker));
  }

  @Override
  public boolean tryAcquirePermit() {
    return permittedExecutions.getAndUpdate(permits -> permits == 0 ? 0 : permits - 1) > 0;
  }

  @Override
  public void releasePermit() {
    permittedExecutions.incrementAndGet();
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
  synchronized void checkThreshold(ExecutionContext<R> context) {
    boolean successesExceeded;
    boolean failuresExceeded;

    int successThreshold = config.getSuccessThreshold();
    if (successThreshold != 0) {
      int successThresholdingCapacity = config.getSuccessThresholdingCapacity();
      successesExceeded = stats.getSuccessCount() >= successThreshold;
      failuresExceeded = stats.getFailureCount() > successThresholdingCapacity - successThreshold;
    } else {
      int failureRateThreshold = config.getFailureRateThreshold();
      if (failureRateThreshold != 0) {
        boolean executionThresholdExceeded = stats.getExecutionCount() >= config.getFailureExecutionThreshold();
        failuresExceeded = executionThresholdExceeded && stats.getFailureRate() >= failureRateThreshold;
        successesExceeded = executionThresholdExceeded && stats.getSuccessRate() > 100 - failureRateThreshold;
      } else {
        int failureThresholdingCapacity = config.getFailureThresholdingCapacity();
        int failureThreshold = config.getFailureThreshold();
        failuresExceeded = stats.getFailureCount() >= failureThreshold;
        successesExceeded = stats.getSuccessCount() > failureThresholdingCapacity - failureThreshold;
      }
    }

    if (successesExceeded)
      breaker.close();
    else if (failuresExceeded)
      breaker.open(context);
  }

  /**
   * Returns the capacity of the breaker in the half-open state.
   */
  private static int capacityFor(CircuitBreaker<?> breaker) {
    int capacity = breaker.getConfig().getSuccessThresholdingCapacity();
    if (capacity == 0)
      capacity = breaker.getConfig().getFailureExecutionThreshold();
    if (capacity == 0)
      capacity = breaker.getConfig().getFailureThresholdingCapacity();
    return capacity;
  }
}