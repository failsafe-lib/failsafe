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
import net.jodah.failsafe.internal.util.CircularBitSet;
import net.jodah.failsafe.util.Ratio;

public class HalfOpenState extends CircuitState {
  private final CircuitBreaker breaker;
  private final CircuitBreakerInternals internals;

  public HalfOpenState(CircuitBreaker breaker, CircuitBreakerInternals internals) {
    this.breaker = breaker;
    this.internals = internals;
    setSuccessThreshold(breaker.getSuccessThreshold() != null ?
        breaker.getSuccessThreshold() :
        breaker.getFailureThreshold() != null ? breaker.getFailureThreshold() : ONE_OF_ONE);
  }

  @Override
  public boolean allowsExecution() {
    return internals.getCurrentExecutions() < maxConcurrentExecutions();
  }

  @Override
  public State getInternals() {
    return State.HALF_OPEN;
  }

  @Override
  public synchronized void recordFailure(ExecutionContext context) {
    bitSet.setNext(false);
    checkThreshold();
  }

  @Override
  public synchronized void recordSuccess() {
    bitSet.setNext(true);
    checkThreshold();
  }

  @Override
  public void setFailureThreshold(Ratio threshold) {
    if (breaker.getSuccessThreshold() == null)
      bitSet = new CircularBitSet(threshold.getDenominator(), bitSet);
  }

  @Override
  public void setSuccessThreshold(Ratio threshold) {
    bitSet = new CircularBitSet(threshold.getDenominator(), bitSet);
  }

  /**
   * Checks to determine if a threshold has been met and the circuit should be opened or closed.
   *
   * <p>
   * If a success ratio is configured, the circuit is opened or closed after the expected number of executions based on
   * whether the ratio was exceeded.
   * <p>
   * Else if a failure ratio is configured, the circuit is opened or closed after the expected number of executions
   * based on whether the ratio was not exceeded.
   * <p>
   * Else when no thresholds are configured, the circuit opens or closes on a single failure or success.
   */
  synchronized void checkThreshold() {
    Ratio successRatio = breaker.getSuccessThreshold();
    Ratio failureRatio = breaker.getFailureThreshold();

    if (successRatio != null) {
      if (bitSet.occupiedBits() == successRatio.getDenominator() || (successRatio.getValue() == 1.0
          && bitSet.positiveRatioValue() < 1.0))
        if (bitSet.positiveRatioValue() >= successRatio.getValue())
          breaker.close();
        else
          breaker.open();
    } else if (failureRatio != null) {
      if (bitSet.occupiedBits() == failureRatio.getDenominator() || (failureRatio.getValue() == 1.0
          && bitSet.negativeRatioValue() < 1.0))
        if (bitSet.negativeRatioValue() >= failureRatio.getValue())
          breaker.open();
        else
          breaker.close();
    } else {
      if (bitSet.positiveRatioValue() == 1)
        breaker.close();
      else
        breaker.open();
    }
  }

  /**
   * Returns the max allowed concurrent executions.
   */
  int maxConcurrentExecutions() {
    if (breaker.getSuccessThreshold() != null)
      return breaker.getSuccessThreshold().getDenominator();
    else if (breaker.getFailureThreshold() != null)
      return breaker.getFailureThreshold().getDenominator();
    else
      return 1;
  }
}