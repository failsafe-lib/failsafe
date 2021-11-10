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
package dev.failsafe;

import dev.failsafe.event.CircuitBreakerStateChangedEvent;
import dev.failsafe.event.EventListener;
import dev.failsafe.internal.CircuitBreakerImpl;
import dev.failsafe.internal.util.Assert;

import java.time.Duration;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Builds {@link CircuitBreaker} instances.
 * <ul>
 *   <li>By default, any exception is considered a failure and will be handled by the policy. You can override this by
 *   specifying your own {@code handle} conditions. The default exception handling condition will only be overridden by
 *   another condition that handles failure exceptions such as {@link #handle(Class)} or {@link #handleIf(BiPredicate)}.
 *   Specifying a condition that only handles results, such as {@link #handleResult(Object)} or
 *   {@link #handleResultIf(Predicate)} will not replace the default exception handling condition.</li>
 *   <li>If multiple {@code handle} conditions are specified, any condition that matches an execution result or failure
 *   will trigger policy handling.</li>
 * </ul>
 * <p>
 * Note:
 * <ul>
 *   <li>This class extends {@link DelayablePolicyBuilder} and {@link FailurePolicyBuilder} which offer additional configuration.</li>
 *   <li>This class is <i>not</i> threadsafe.</li>
 * </ul>
 * </p>
 *
 * @param <R> result type
 * @author Jonathan Halterman
 * @see CircuitBreaker
 * @see CircuitBreakerConfig
 * @see CircuitBreakerOpenException
 */
public class CircuitBreakerBuilder<R>
  extends DelayablePolicyBuilder<CircuitBreakerBuilder<R>, CircuitBreakerConfig<R>, R>
  implements PolicyListeners<CircuitBreakerBuilder<R>, R> {

  CircuitBreakerBuilder() {
    super(new CircuitBreakerConfig<>());
    config.delay = Duration.ofMinutes(1);
    config.failureThreshold = 1;
    config.failureThresholdingCapacity = 1;
  }

  /**
   * Builds a new {@link CircuitBreaker} using the builder's configuration.
   */
  public CircuitBreaker<R> build() {
    return new CircuitBreakerImpl<>(new CircuitBreakerConfig<>(config));
  }

  /**
   * Calls the {@code listener} when the circuit is closed.
   * <p>Note: Any exceptions that are thrown from within the {@code listener} are ignored.</p>
   *
   * @throws NullPointerException if {@code listener} is null
   */
  public CircuitBreakerBuilder<R> onClose(EventListener<CircuitBreakerStateChangedEvent> listener) {
    config.closeListener = Assert.notNull(listener, "runnable");
    return this;
  }

  /**
   * Calls the {@code listener} when the circuit is half-opened.
   * <p>Note: Any exceptions that are thrown within the {@code listener} are ignored.</p>
   *
   * @throws NullPointerException if {@code listener} is null
   */
  public CircuitBreakerBuilder<R> onHalfOpen(EventListener<CircuitBreakerStateChangedEvent> listener) {
    config.halfOpenListener = Assert.notNull(listener, "runnable");
    return this;
  }

  /**
   * Calls the {@code listener} when the circuit is opened.
   * <p>Note: Any exceptions that are thrown within the {@code listener} are ignored.</p>
   *
   * @throws NullPointerException if {@code listener} is null
   */
  public CircuitBreakerBuilder<R> onOpen(EventListener<CircuitBreakerStateChangedEvent> listener) {
    config.openListener = Assert.notNull(listener, "listener");
    return this;
  }

  /**
   * Sets the {@code delay} to wait in OPEN state before transitioning to half-open.
   *
   * @throws NullPointerException if {@code delay} is null
   * @throws IllegalArgumentException if {@code delay} < 0
   */
  public CircuitBreakerBuilder<R> withDelay(Duration delay) {
    Assert.notNull(delay, "delay");
    Assert.isTrue(delay.toNanos() >= 0, "delay must be positive");
    config.delay = delay;
    return this;
  }

  /**
   * Configures count based failure thresholding by setting the number of consecutive failures that must occur when in a
   * CLOSED state in order to open the circuit.
   * <p>
   * If a {@link #withSuccessThreshold(int) success threshold} is not configured, the {@code failureThreshold} will also
   * be used when the circuit breaker is in a HALF_OPEN state to determine whether to transition back to OPEN or
   * CLOSED.
   * </p>
   *
   * @param failureThreshold The number of consecutive failures that must occur in order to open the circuit
   * @throws IllegalArgumentException if {@code failureThreshold} < 1
   * @see CircuitBreakerConfig#getFailureThreshold()
   */
  public CircuitBreakerBuilder<R> withFailureThreshold(int failureThreshold) {
    return withFailureThreshold(failureThreshold, failureThreshold);
  }

  /**
   * Configures count based failure thresholding by setting the ratio of successive failures to executions that must
   * occur when in a CLOSED state in order to open the circuit. For example: 5, 10 would open the circuit if 5 out of
   * the last 10 executions result in a failure.
   * <p>
   * If a {@link #withSuccessThreshold(int) success threshold} is not configured, the {@code failureThreshold} and
   * {@code failureThresholdingCapacity} will also be used when the circuit breaker is in a HALF_OPEN state to determine
   * whether to transition back to OPEN or CLOSED.
   * </p>
   *
   * @param failureThreshold The number of failures that must occur in order to open the circuit
   * @param failureThresholdingCapacity The capacity for storing execution results when performing failure thresholding
   * @throws IllegalArgumentException if {@code failureThreshold} < 1, {@code failureThresholdingCapacity} < 1, or
   * {@code failureThreshold} > {@code failureThresholdingCapacity}
   * @see CircuitBreakerConfig#getFailureThreshold()
   * @see CircuitBreakerConfig#getFailureExecutionThreshold()
   */
  public CircuitBreakerBuilder<R> withFailureThreshold(int failureThreshold, int failureThresholdingCapacity) {
    Assert.isTrue(failureThreshold >= 1, "failureThreshold must be >= 1");
    Assert.isTrue(failureThresholdingCapacity >= 1, "failureThresholdingCapacity must be >= 1");
    Assert.isTrue(failureThresholdingCapacity >= failureThreshold,
      "failureThresholdingCapacity must be >= failureThreshold");
    config.failureThreshold = failureThreshold;
    config.failureThresholdingCapacity = failureThresholdingCapacity;
    return this;
  }

  /**
   * Configures time based failure thresholding by setting the number of failures that must occur within the {@code
   * failureThresholdingPeriod} when in a CLOSED state in order to open the circuit.
   * <p>
   * If a {@link #withSuccessThreshold(int) success threshold} is not configured, the {@code failureThreshold} will also
   * be used when the circuit breaker is in a HALF_OPEN state to determine whether to transition back to OPEN or
   * CLOSED.
   * </p>
   *
   * @param failureThreshold The number of failures that must occur within the {@code failureThresholdingPeriod} in
   * order to open the circuit
   * @param failureThresholdingPeriod The period during which failures are compared to the {@code failureThreshold}
   * @throws NullPointerException if {@code failureThresholdingPeriod} is null
   * @throws IllegalArgumentException if {@code failureThreshold} < 1 or {@code failureThresholdingPeriod} < 10 ms
   * @see CircuitBreakerConfig#getFailureThreshold()
   * @see CircuitBreakerConfig#getFailureThresholdingPeriod()
   */
  public CircuitBreakerBuilder<R> withFailureThreshold(int failureThreshold, Duration failureThresholdingPeriod) {
    return withFailureThreshold(failureThreshold, failureThreshold, failureThresholdingPeriod);
  }

  /**
   * Configures time based failure thresholding by setting the number of failures that must occur within the {@code
   * failureThresholdingPeriod} when in a CLOSED state in order to open the circuit. The number of executions must also
   * exceed the {@code failureExecutionThreshold} within the {@code failureThresholdingPeriod} when in the CLOSED state
   * before the circuit can be opened.
   * <p>
   * If a {@link #withSuccessThreshold(int) success threshold} is not configured, the {@code failureThreshold} will also
   * be used when the circuit breaker is in a HALF_OPEN state to determine whether to transition back to OPEN or
   * CLOSED.
   * </p>
   *
   * @param failureThreshold The number of failures that must occur within the {@code failureThresholdingPeriod} in
   * order to open the circuit
   * @param failureExecutionThreshold The minimum number of executions that must occur within the {@code
   * failureThresholdingPeriod} when in the CLOSED state before the circuit can be opened
   * @param failureThresholdingPeriod The period during which failures are compared to the {@code failureThreshold}
   * @throws NullPointerException if {@code failureThresholdingPeriod} is null
   * @throws IllegalArgumentException if {@code failureThreshold} < 1, {@code failureExecutionThreshold} < 1, {@code
   * failureThreshold} > {@code failureExecutionThreshold}, or {@code failureThresholdingPeriod} < 10 ms
   * @see CircuitBreakerConfig#getFailureThreshold()
   * @see CircuitBreakerConfig#getFailureExecutionThreshold()
   * @see CircuitBreakerConfig#getFailureThresholdingPeriod()
   */
  public CircuitBreakerBuilder<R> withFailureThreshold(int failureThreshold, int failureExecutionThreshold,
    Duration failureThresholdingPeriod) {
    Assert.isTrue(failureThreshold >= 1, "failureThreshold must be >= 1");
    Assert.isTrue(failureExecutionThreshold >= failureThreshold,
      "failureExecutionThreshold must be >= failureThreshold");
    assertFailureExecutionThreshold(failureExecutionThreshold);
    assertFailureThresholdingPeriod(failureThresholdingPeriod);
    config.failureThreshold = failureThreshold;
    config.failureThresholdingCapacity = failureThreshold;
    config.failureExecutionThreshold = failureExecutionThreshold;
    config.failureThresholdingPeriod = failureThresholdingPeriod;
    return this;
  }

  /**
   * Configures time based failure rate thresholding by setting the percentage rate of failures, from 1 to 100, that
   * must occur within the rolling {@code failureThresholdingPeriod} when in a CLOSED state in order to open the
   * circuit. The number of executions must also exceed the {@code failureExecutionThreshold} within the {@code
   * failureThresholdingPeriod} before the circuit can be opened.
   * <p>
   * If a {@link #withSuccessThreshold(int) success threshold} is not configured, the {@code failureExecutionThreshold}
   * will also be used when the circuit breaker is in a HALF_OPEN state to determine whether to transition back to open
   * or closed.
   * </p>
   *
   * @param failureRateThreshold The percentage rate of failures, from 1 to 100, that must occur in order to open the
   * circuit
   * @param failureExecutionThreshold The minimum number of executions that must occur within the {@code
   * failureThresholdingPeriod} when in the CLOSED state before the circuit can be opened, or in the HALF_OPEN state
   * before it can be re-opened or closed
   * @param failureThresholdingPeriod The period during which failures are compared to the {@code failureThreshold}
   * @throws NullPointerException if {@code failureThresholdingPeriod} is null
   * @throws IllegalArgumentException if {@code failureRateThreshold} < 1 or > 100, {@code failureExecutionThreshold} <
   * 1, or {@code failureThresholdingPeriod} < 10 ms
   * @see CircuitBreakerConfig#getFailureRateThreshold()
   * @see CircuitBreakerConfig#getFailureExecutionThreshold()
   * @see CircuitBreakerConfig#getFailureThresholdingPeriod()
   */
  public CircuitBreakerBuilder<R> withFailureRateThreshold(int failureRateThreshold, int failureExecutionThreshold,
    Duration failureThresholdingPeriod) {
    Assert.isTrue(failureRateThreshold >= 1 && failureRateThreshold <= 100,
      "failureRateThreshold must be between 1 and 100");
    assertFailureExecutionThreshold(failureExecutionThreshold);
    assertFailureThresholdingPeriod(failureThresholdingPeriod);
    config.failureRateThreshold = failureRateThreshold;
    config.failureExecutionThreshold = failureExecutionThreshold;
    config.failureThresholdingPeriod = failureThresholdingPeriod;
    return this;
  }

  private void assertFailureExecutionThreshold(int failureExecutionThreshold) {
    Assert.isTrue(failureExecutionThreshold >= 1, "failureExecutionThreshold must be >= 1");
  }

  private void assertFailureThresholdingPeriod(Duration failureThresholdingPeriod) {
    Assert.notNull(failureThresholdingPeriod, "failureThresholdingPeriod");
    Assert.isTrue(failureThresholdingPeriod.toMillis() >= 10, "failureThresholdingPeriod must be >= 10 ms");
  }

  /**
   * Configures count based success thresholding by setting the number of consecutive successful executions that must
   * occur when in a HALF_OPEN state in order to close the circuit, else the circuit is re-opened when a failure
   * occurs.
   *
   * @param successThreshold The number of consecutive successful executions that must occur in order to open the
   * circuit
   * @throws IllegalArgumentException if {@code successThreshold} < 1
   * @see CircuitBreakerConfig#getSuccessThreshold()
   */
  public CircuitBreakerBuilder<R> withSuccessThreshold(int successThreshold) {
    return withSuccessThreshold(successThreshold, successThreshold);
  }

  /**
   * Configures count based success thresholding by setting the ratio of successive successful executions that must
   * occur when in a HALF_OPEN state in order to close the circuit. For example: 5, 10 would close the circuit if 5 out
   * of the last 10 executions were successful.
   *
   * @param successThreshold The number of successful executions that must occur in order to open the circuit
   * @param successThresholdingCapacity The capacity for storing execution results when performing success thresholding
   * @throws IllegalArgumentException if {@code successThreshold} < 1, {@code successThresholdingCapacity} < 1, or
   * {@code successThreshold} > {@code successThresholdingCapacity}
   * @see CircuitBreakerConfig#getSuccessThreshold()
   * @see CircuitBreakerConfig#getSuccessThresholdingCapacity()
   */
  public CircuitBreakerBuilder<R> withSuccessThreshold(int successThreshold, int successThresholdingCapacity) {
    Assert.isTrue(successThreshold >= 1, "successThreshold must be >= 1");
    Assert.isTrue(successThresholdingCapacity >= 1, "successThresholdingCapacity must be >= 1");
    Assert.isTrue(successThresholdingCapacity >= successThreshold,
      "successThresholdingCapacity must be >= successThreshold");
    config.successThreshold = successThreshold;
    config.successThresholdingCapacity = successThresholdingCapacity;
    return this;
  }
}
