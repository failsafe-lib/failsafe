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

import java.time.Duration;

/**
 * A circuit breaker temporarily blocks execution when a configured number of failures are exceeded.
 * <p>
 * Circuit breakers have three states: <i>closed</i>, <i>open</i>, and <i>half-open</i>. When a circuit breaker is in
 * the <i>closed</i> (initial) state, executions are allowed. If a {@link CircuitBreakerBuilder#withFailureThreshold(int)
 * configurable number} of failures occur, optionally over some {@link CircuitBreakerBuilder#withFailureThreshold(int,
 * Duration) time period}, the circuit breaker transitions to the <i>open</i> state. In the <i>open</i> state a circuit
 * breaker will fail executions with {@link CircuitBreakerOpenException}. After a {@link
 * CircuitBreakerBuilder#withDelay(Duration) configurable delay}, the circuit breaker will transition to a
 * <i>half-open</i> state. In the
 * <i>half-open</i> state a {@link CircuitBreakerBuilder#withSuccessThreshold(int) configurable number} of trial
 * executions will be allowed, after which the circuit breaker will transition back to <i>closed</i> or <i>open</i>
 * depending on how many were successful.
 * </p>
 * <p>
 * A circuit breaker can be <i>count based</i> or <i>time based</i>:
 * <ul>
 *   <li><i>Count based</i> circuit breakers will transition between states when recent execution results exceed a threshold.</li>
 *   <li><i>Time based</i> circuit breakers will transition between states when recent execution results exceed a threshold
 *   within a time period.</li>
 * </ul>
 * </p>
 * <p>A minimum number of executions must be performed in order for a state transition to occur. Time based circuit
 * breakers use a sliding window to aggregate execution results. The window is divided into {@code 10} time slices,
 * each representing 1/10th of the {@link CircuitBreakerConfig#getFailureThresholdingPeriod() failureThresholdingPeriod}.
 * As time progresses, statistics for old time slices are gradually discarded, which smoothes the calculation of
 * success and failure rates.</p>
 * <p>
 * This class is threadsafe.
 * </p>
 *
 * @param <R> result type
 * @author Jonathan Halterman
 * @see CircuitBreakerConfig
 * @see CircuitBreakerBuilder
 * @see CircuitBreakerOpenException
 */
public interface CircuitBreaker<R> extends Policy<R> {
  /**
   * Creates a CircuitBreakerBuilder that by default will build a count based circuit breaker that opens after a {@link
   * CircuitBreakerBuilder#withFailureThreshold(int) single failure}, closes after a {@link
   * CircuitBreakerBuilder#withSuccessThreshold(int) single success}, and has a 1 minute {@link
   * CircuitBreakerBuilder#withDelay(Duration) delay}, unless configured otherwise.
   *
   * @see #ofDefaults()
   */
  static <R> CircuitBreakerBuilder<R> builder() {
    return new CircuitBreakerBuilder<>();
  }

  /**
   * Creates a new CircuitBreakerBuilder that will be based on the {@code config}.
   */
  static <R> CircuitBreakerBuilder<R> builder(CircuitBreakerConfig<R> config) {
    return new CircuitBreakerBuilder<>(config);
  }

  /**
   * Creates a count based CircuitBreaker that opens after one {@link CircuitBreakerBuilder#withFailureThreshold(int)
   * failure}, half-opens after a one minute {@link CircuitBreakerBuilder#withDelay(Duration) delay}, and closes after one
   * {@link CircuitBreakerBuilder#withSuccessThreshold(int) success}. To configure additional options on a
   * CircuitBreaker, use {@link #builder()} instead.
   *
   * @see #builder()
   */
  static <R> CircuitBreaker<R> ofDefaults() {
    return CircuitBreaker.<R>builder().build();
  }

  /**
   * The state of the circuit.
   */
  enum State {
    /** The circuit is closed and fully functional, allowing executions to occur. */
    CLOSED,
    /** The circuit is opened and not allowing executions to occur. */
    OPEN,
    /** The circuit is temporarily allowing executions to occur. */
    HALF_OPEN
  }

  /**
   * Returns the {@link CircuitBreakerConfig} that the CircuitBreaker was built with.
   */
  @Override
  CircuitBreakerConfig<R> getConfig();

  /**
   * Attempts to acquire a permit for the circuit breaker and throws {@link CircuitBreakerOpenException} if a permit
   * could not be acquired. Permission will be automatically released when a result or failure is recorded.
   *
   * @throws CircuitBreakerOpenException if the circuit breaker is in a half-open state and no permits remain according
   * to the configured success or failure thresholding capacity.
   * @see #tryAcquirePermit()
   * @see #recordResult(Object)
   * @see #recordException(Throwable)
   * @see #recordSuccess()
   * @see #recordFailure()
   */
  default void acquirePermit() {
    if (!tryAcquirePermit())
      throw new CircuitBreakerOpenException(this);
  }

  /**
   * Tries to acquire a permit to use the circuit breaker and returns whether a permit was acquired. Permission will be
   * automatically released when a result or failure is recorded.
   *
   * @see #recordResult(Object)
   * @see #recordException(Throwable)
   * @see #recordSuccess()
   * @see #recordFailure()
   */
  boolean tryAcquirePermit();

  /**
   * Opens the circuit.
   */
  void open();

  /**
   * Closes the circuit.
   */
  void close();

  /**
   * Half-opens the circuit.
   */
  void halfOpen();

  /**
   * Gets the state of the circuit.
   */
  State getState();

  /**
   * Returns the number of executions recorded in the current state when the state is CLOSED or HALF_OPEN. When the
   * state is OPEN, returns the executions recorded during the previous CLOSED state.
   * <p>
   * For count based thresholding, the max number of executions is limited to the execution threshold. For time based
   * thresholds, the number of executions may vary within the thresholding period.
   * </p>
   */
  int getExecutionCount();

  /**
   * When in the OPEN state, returns the remaining delay until the circuit is half-opened and allows another execution,
   * else returns {@code Duration.ZERO}.
   */
  Duration getRemainingDelay();

  /**
   * Returns the number of failures recorded in the current state when the state is CLOSED or HALF_OPEN. When the state
   * is OPEN, returns the failures recorded during the previous CLOSED state.
   * <p>
   * For count based thresholds, the max number of failures is based on the {@link
   * CircuitBreakerConfig#getFailureThreshold() failure threshold}. For time based thresholds, the number of failures
   * may vary within the {@link CircuitBreakerConfig#getFailureThresholdingPeriod() failure thresholding period}.
   * </p>
   */
  long getFailureCount();

  /**
   * The percentage rate of failed executions, from 0 to 100, in the current state when the state is CLOSED or
   * HALF_OPEN. When the state is OPEN, returns the rate recorded during the previous CLOSED state.
   * <p>
   * The rate is based on the configured {@link CircuitBreakerConfig#getFailureThresholdingCapacity() failure
   * thresholding capacity}.
   * </p>
   */
  int getFailureRate();

  /**
   * Returns the number of successes recorded in the current state when the state is CLOSED or HALF_OPEN. When the state
   * is OPEN, returns the successes recorded during the previous CLOSED state.
   * <p>
   * The max number of successes is based on the {@link CircuitBreakerConfig#getSuccessThreshold() success threshold}.
   * </p>
   */
  int getSuccessCount();

  /**
   * The percentage rate of successful executions, from 0 to 100, in the current state when the state is CLOSED or
   * HALF_OPEN. When the state is OPEN, returns the rate recorded during the previous CLOSED state.
   * <p>
   * The rate is based on the configured {@link CircuitBreakerConfig#getSuccessThresholdingCapacity() success
   * thresholding capacity}.
   * </p>
   */
  int getSuccessRate();

  /**
   * Returns whether the circuit is closed.
   */
  boolean isClosed();

  /**
   * Returns whether the circuit is half open.
   */
  boolean isHalfOpen();

  /**
   * Returns whether the circuit is open.
   */
  boolean isOpen();

  /**
   * Records an execution failure.
   */
  void recordFailure();

  /**
   * Records an {@code exception} as a success or failure based on the exception configuration.
   */
  void recordException(Throwable exception);

  /**
   * Records an execution {@code result} as a success or failure based on the failure configuration.
   */
  void recordResult(R result);

  /**
   * Records an execution success.
   */
  void recordSuccess();
}
