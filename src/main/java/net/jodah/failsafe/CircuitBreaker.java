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
package net.jodah.failsafe;

import net.jodah.failsafe.function.CheckedRunnable;
import net.jodah.failsafe.internal.*;
import net.jodah.failsafe.internal.util.Assert;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A circuit breaker temporarily blocks execution when a configured number of failures are exceeded.
 * <p>
 * Circuit breakers have three states: <i>closed</i>, <i>open</i>, and <i>half-open</i>. When a circuit breaker is in
 * the <i>closed</i> (initial) state, executions are allowed. If a {@link #withFailureThreshold(int) configurable
 * number} of failures occur, optionally over some {@link #withFailureThreshold(int, Duration) time period}, the circuit
 * breaker transitions to the <i>open</i> state. In the <i>open</i> state a circuit breaker will fail executions with
 * {@link CircuitBreakerOpenException}. After a {@link #withDelay(Duration) configurable delay}, the circuit breaker
 * will transition to a <i>half-open</i> state. In the
 * <i>half-open</i> state a {@link #withSuccessThreshold(int) configurable number} of trial executions will be allowed,
 * after which the circuit breaker will transition back to <i>closed</i> or <i>open</i> depending on how many were
 * successful.
 * </p>
 * <p>
 * A circuit breaker can be <i>count based</i> or <i>time based</i>. A <i>count based</i> circuit breaker will
 * transition between states when recent execution results exceed a threshold. A <i>time based</i> circuit breaker will
 * transition between states when recent execution results exceed a threshold within a time period. A minimum number of
 * executions must be performed in order for a state transition to occur.
 * </p>
 * <p>Time based circuit breakers use a sliding window to aggregate execution results. The window is divided into
 * {@code 10} time slices, each representing 1/10th of the {@link #getFailureThresholdingPeriod()
 * failureThresholdingPeriod}. As time progresses, statistics for old time slices are gradually discarded, which
 * smoothes the calculation of success and failure rates.</p>
 * <p>
 * Note: CircuitBreaker extends {@link DelayablePolicy}, {@link FailurePolicy}, and {@link PolicyListeners} which offer
 * additional configuration.
 * </p>
 *
 * @param <R> result type
 * @author Jonathan Halterman
 * @see CircuitBreakerOpenException
 */
@SuppressWarnings("WeakerAccess")
public class CircuitBreaker<R> extends DelayablePolicy<CircuitBreaker<R>, R> {
  /** Writes guarded by "this" */
  private final AtomicReference<CircuitState> state = new AtomicReference<>();
  private final AtomicInteger currentExecutions = new AtomicInteger();
  private Duration delay = Duration.ofMinutes(1);

  // Failure config
  private int failureThreshold = 1;
  private int failureRateThreshold;
  private int failureThresholdingCapacity = 1;
  private int failureExecutionThreshold;
  private Duration failureThresholdingPeriod;

  // Success config
  private int successThreshold;
  private int successThresholdingCapacity;

  // Listeners
  CheckedRunnable onOpen;
  CheckedRunnable onHalfOpen;
  CheckedRunnable onClose;

  /**
   * Creates a count based circuit breaker that opens after a {@link #withFailureThreshold(int) single failure}, closes
   * after a {@link #withSuccessThreshold(int) single success}, and has a 1 minute {@link #withDelay(Duration) delay} by
   * default.
   */
  public CircuitBreaker() {
    failureConditions = new ArrayList<>();
    state.set(new ClosedState(this, internals));
  }

  /**
   * The state of the circuit.
   */
  public enum State {
    /** The circuit is closed and fully functional, allowing executions to occur. */
    CLOSED,
    /** The circuit is opened and not allowing executions to occur. */
    OPEN,
    /** The circuit is temporarily allowing executions to occur. */
    HALF_OPEN
  }

  /**
   * Returns whether the circuit allows execution and triggers a state transition if a threshold has been exceeded.
   */
  public boolean allowsExecution() {
    return state.get().allowsExecution();
  }

  /**
   * Closes the circuit.
   */
  public void close() {
    transitionTo(State.CLOSED, onClose, null);
  }

  /**
   * Gets the state of the circuit.
   */
  public State getState() {
    return state.get().getState();
  }

  /**
   * Returns the delay before allowing another execution on the circuit. Defaults to 1 minute.
   *
   * @see #withDelay(Duration)
   * @see #getRemainingDelay()
   */
  public Duration getDelay() {
    return delay;
  }

  /**
   * Returns the number of executions recorded in the current state when the state is CLOSED or HALF_OPEN. When the
   * state is OPEN, returns the executions recorded during the previous CLOSED state.
   * <p>
   * For count based thresholding, the max number of executions is limited to the execution threshold. For time based
   * thresholds, the number of executions may vary within the thresholding period.
   * </p>
   */
  public int getExecutionCount() {
    return state.get().getStats().getExecutionCount();
  }

  /**
   * When in the OPEN state, returns the remaining delay until the circuit is half-opened and allows another execution,
   * else returns {@code Duration.ZERO}.
   */
  public Duration getRemainingDelay() {
    return state.get().getRemainingDelay();
  }

  /**
   * Returns the number of failures recorded in the current state when the state is CLOSED or HALF_OPEN. When the state
   * is OPEN, returns the failures recorded during the previous CLOSED state.
   * <p>
   * For count based thresholds, the max number of failures is based on the {@link #getFailureThreshold() failure
   * threshold}. For time based thresholds, the number of failures may vary within the {@link
   * #getFailureThresholdingPeriod() failure thresholding period}.
   * </p>
   */
  public long getFailureCount() {
    return state.get().getStats().getFailureCount();
  }

  /**
   * Returns the rolling capacity for storing execution results when performing failure thresholding in the CLOSED or
   * HALF_OPEN states. {@code 1} by default. Only the most recent executions that fit within this capacity contribute to
   * thresholding decisions.
   *
   * @see #withFailureThreshold(int)
   * @see #withFailureThreshold(int, int)
   */
  public int getFailureThresholdingCapacity() {
    return failureThresholdingCapacity;
  }

  /**
   * Used with time based thresholding. Returns the rolling time period during which failure thresholding is performed
   * when in the CLOSED state, else {@code null} if time based failure thresholding is not configured. Only the most
   * recent executions that occurred within this rolling time period contribute to thresholding decisions.
   *
   * @see #withFailureThreshold(int, Duration)
   * @see #withFailureThreshold(int, int, Duration)
   * @see #withFailureRateThreshold(int, int, Duration)
   */
  public Duration getFailureThresholdingPeriod() {
    return failureThresholdingPeriod;
  }

  /**
   * Used with time based thresholding. Returns the minimum number of executions that must be recorded in the CLOSED
   * state before the breaker can be opened. For {@link #withFailureRateThreshold(int, int, Duration) failure rate
   * thresholding} this also determines the minimum number of executions that must be recorded in the HALF_OPEN state.
   * Returns {@code 0} by default.
   *
   * @see #withFailureThreshold(int, int, Duration)
   * @see #withFailureRateThreshold(int, int, Duration)
   */
  public int getFailureExecutionThreshold() {
    return failureExecutionThreshold;
  }

  /**
   * The percentage rate of failed executions, from 0 to 100, in the current state when the state is CLOSED or
   * HALF_OPEN. When the state is OPEN, returns the rate recorded during the previous CLOSED state.
   * <p>
   * The rate is based on the configured {@link #getFailureThresholdingCapacity() failure thresholding capacity}.
   * </p>
   */
  public int getFailureRate() {
    return state.get().getStats().getFailureRate();
  }

  /**
   * Used with time based thresholding. Returns percentage rate of failures, from 1 to 100, that must occur when in a
   * CLOSED or HALF_OPEN state in order to open the circuit, else {@code 0} if failure rate thresholding is not
   * configured.
   *
   * @see #withFailureRateThreshold(int, int, Duration)
   */
  public int getFailureRateThreshold() {
    return failureRateThreshold;
  }

  /**
   * Gets the number of successive failures that must occur within the {@link #getFailureThresholdingCapacity() failure
   * thresholding capacity} when in a CLOSED or HALF_OPEN state in order to open the circuit. Returns {@code 1} by
   * default.
   *
   * @see #withFailureThreshold(int)
   * @see #withFailureThreshold(int, int)
   */
  public int getFailureThreshold() {
    return failureThreshold;
  }

  /**
   * Returns the number of successes recorded in the current state when the state is CLOSED or HALF_OPEN. When the state
   * is OPEN, returns the successes recorded during the previous CLOSED state.
   * <p>
   * The max number of successes is based on the {@link #getSuccessThreshold() success threshold}.
   * </p>
   */
  public int getSuccessCount() {
    return state.get().getStats().getSuccessCount();
  }

  /**
   * Returns the rolling capacity for storing execution results when performing success thresholding in the HALF_OPEN
   * state. Only the most recent executions that fit within this capacity contribute to thresholding decisions.
   *
   * @see #withSuccessThreshold(int)
   * @see #withSuccessThreshold(int, int)
   */
  public int getSuccessThresholdingCapacity() {
    return successThresholdingCapacity;
  }

  /**
   * The percentage rate of successful executions, from 0 to 100, in the current state when the state is CLOSED or
   * HALF_OPEN. When the state is OPEN, returns the rate recorded during the previous CLOSED state.
   * <p>
   * The rate is based on the configured {@link #getSuccessThresholdingCapacity() success thresholding capacity}.
   * </p>
   */
  public int getSuccessRate() {
    return state.get().getStats().getSuccessRate();
  }

  /**
   * Gets the number of successive successes that must occur within the {@link #getSuccessThresholdingCapacity() success
   * thresholding capacity} when in a HALF_OPEN state in order to open the circuit. Returns {@code 0} by default, in
   * which case the {@link #getFailureThreshold() failure threshold} is used instead.
   *
   * @see #withSuccessThreshold(int)
   * @see #withSuccessThreshold(int, int)
   */
  public int getSuccessThreshold() {
    return successThreshold;
  }

  /**
   * Half-opens the circuit.
   */
  public void halfOpen() {
    transitionTo(State.HALF_OPEN, onHalfOpen, null);
  }

  /**
   * Returns whether the circuit is closed.
   */
  public boolean isClosed() {
    return State.CLOSED.equals(getState());
  }

  /**
   * Returns whether the circuit is half open.
   */
  public boolean isHalfOpen() {
    return State.HALF_OPEN.equals(getState());
  }

  /**
   * Returns whether the circuit is open.
   */
  public boolean isOpen() {
    return State.OPEN.equals(getState());
  }

  /**
   * Calls the {@code runnable} when the circuit is closed.
   * <p>Note: Any exceptions that are thrown from within the {@code runnable} are ignored.</p>
   */
  public CircuitBreaker<R> onClose(CheckedRunnable runnable) {
    onClose = runnable;
    return this;
  }

  /**
   * Calls the {@code runnable} when the circuit is half-opened.
   * <p>Note: Any exceptions that are thrown within the {@code runnable} are ignored.</p>
   */
  public CircuitBreaker<R> onHalfOpen(CheckedRunnable runnable) {
    onHalfOpen = runnable;
    return this;
  }

  /**
   * Calls the {@code runnable} when the circuit is opened.
   * <p>Note: Any exceptions that are thrown within the {@code runnable} are ignored.</p>
   */
  public CircuitBreaker<R> onOpen(CheckedRunnable runnable) {
    onOpen = runnable;
    return this;
  }

  /**
   * Opens the circuit.
   */
  public void open() {
    transitionTo(State.OPEN, onOpen, null);
  }

  /**
   * Records an execution that is about to take place by incrementing the internal executions count. Required for
   * standalone CircuitBreaker usage.
   */
  public void preExecute() {
    currentExecutions.incrementAndGet();
  }

  /**
   * Records an execution failure.
   */
  public void recordFailure() {
    recordExecutionFailure(null);
  }

  /**
   * Records an execution {@code failure} as a success or failure based on the failure configuration as determined by
   * {@link #isFailure(R, Throwable)}.
   *
   * @see #isFailure(R, Throwable)
   */
  public void recordFailure(Throwable failure) {
    recordResult(null, failure);
  }

  /**
   * Records an execution {@code result} as a success or failure based on the failure configuration as determined by
   * {@link #isFailure(R, Throwable)}.
   *
   * @see #isFailure(R, Throwable)
   */
  public void recordResult(R result) {
    recordResult(result, null);
  }

  /**
   * Records an execution success.
   */
  public void recordSuccess() {
    try {
      state.get().recordSuccess();
    } finally {
      currentExecutions.decrementAndGet();
    }
  }

  @Override
  public String toString() {
    return getState().toString();
  }

  /**
   * Sets the {@code delay} to wait in OPEN state before transitioning to half-open.
   *
   * @throws NullPointerException if {@code delay} is null
   * @throws IllegalArgumentException if {@code delay} < 0
   */
  public CircuitBreaker<R> withDelay(Duration delay) {
    Assert.notNull(delay, "delay");
    Assert.isTrue(delay.toNanos() >= 0, "delay must be positive");
    this.delay = delay;
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
   * @see #getFailureThreshold()
   */
  public CircuitBreaker<R> withFailureThreshold(int failureThreshold) {
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
   * @see #getFailureThreshold()
   * @see #getFailureExecutionThreshold()
   */
  public synchronized CircuitBreaker<R> withFailureThreshold(int failureThreshold, int failureThresholdingCapacity) {
    Assert.isTrue(failureThreshold >= 1, "failureThreshold must be >= 1");
    Assert.isTrue(failureThresholdingCapacity >= 1, "failureThresholdingCapacity must be >= 1");
    Assert.isTrue(failureThresholdingCapacity >= failureThreshold,
      "failureThresholdingCapacity must be >= failureThreshold");
    this.failureThreshold = failureThreshold;
    this.failureThresholdingCapacity = failureThresholdingCapacity;
    state.get().handleConfigChange();
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
   * @see #getFailureThreshold()
   * @see #getFailureThresholdingPeriod()
   */
  public synchronized CircuitBreaker<R> withFailureThreshold(int failureThreshold, Duration failureThresholdingPeriod) {
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
   * @see #getFailureThreshold()
   * @see #getFailureExecutionThreshold()
   * @see #getFailureThresholdingPeriod()
   */
  public synchronized CircuitBreaker<R> withFailureThreshold(int failureThreshold, int failureExecutionThreshold,
    Duration failureThresholdingPeriod) {
    Assert.isTrue(failureThreshold >= 1, "failureThreshold must be >= 1");
    Assert.isTrue(failureExecutionThreshold >= failureThreshold,
      "failureExecutionThreshold must be >= failureThreshold");
    assertFailureExecutionThreshold(failureExecutionThreshold);
    assertFailureThresholdingPeriod(failureThresholdingPeriod);
    this.failureThreshold = failureThreshold;
    this.failureThresholdingCapacity = failureThreshold;
    this.failureExecutionThreshold = failureExecutionThreshold;
    this.failureThresholdingPeriod = failureThresholdingPeriod;
    state.get().handleConfigChange();
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
   * @see #getFailureRateThreshold()
   * @see #getFailureExecutionThreshold()
   * @see #getFailureThresholdingPeriod()
   */
  public synchronized CircuitBreaker<R> withFailureRateThreshold(int failureRateThreshold,
    int failureExecutionThreshold, Duration failureThresholdingPeriod) {
    Assert.isTrue(failureRateThreshold >= 1 && failureRateThreshold <= 100,
      "failureRateThreshold must be between 1 and 100");
    assertFailureExecutionThreshold(failureExecutionThreshold);
    assertFailureThresholdingPeriod(failureThresholdingPeriod);
    this.failureRateThreshold = failureRateThreshold;
    this.failureExecutionThreshold = failureExecutionThreshold;
    this.failureThresholdingPeriod = failureThresholdingPeriod;
    state.get().handleConfigChange();
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
   * @see #getSuccessThreshold()
   */
  public CircuitBreaker<R> withSuccessThreshold(int successThreshold) {
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
   * @see #getSuccessThreshold()
   * @see #getSuccessThresholdingCapacity()
   */
  public synchronized CircuitBreaker<R> withSuccessThreshold(int successThreshold, int successThresholdingCapacity) {
    Assert.isTrue(successThreshold >= 1, "successThreshold must be >= 1");
    Assert.isTrue(successThresholdingCapacity >= 1, "successThresholdingCapacity must be >= 1");
    Assert.isTrue(successThresholdingCapacity >= successThreshold,
      "successThresholdingCapacity must be >= successThreshold");
    this.successThreshold = successThreshold;
    this.successThresholdingCapacity = successThresholdingCapacity;
    state.get().handleConfigChange();
    return this;
  }

  void recordResult(R result, Throwable failure) {
    try {
      if (isFailure(result, failure))
        state.get().recordFailure(null);
      else
        state.get().recordSuccess();
    } finally {
      currentExecutions.decrementAndGet();
    }
  }

  /**
   * Transitions to the {@code newState} if not already in that state and calls any associated event listener.
   */
  private void transitionTo(State newState, CheckedRunnable listener, ExecutionContext context) {
    boolean transitioned = false;
    synchronized (this) {
      if (!getState().equals(newState)) {
        switch (newState) {
          case CLOSED:
            state.set(new ClosedState(this, internals));
            break;
          case OPEN:
            Duration computedDelay = computeDelay(context);
            state.set(new OpenState(this, state.get(), computedDelay != null ? computedDelay : delay));
            break;
          case HALF_OPEN:
            state.set(new HalfOpenState(this, internals));
            break;
        }
        transitioned = true;
      }
    }

    if (transitioned && listener != null) {
      try {
        listener.run();
      } catch (Throwable ignore) {
      }
    }
  }

  /**
   * Records an execution failure.
   */
  void recordExecutionFailure(ExecutionContext context) {
    try {
      state.get().recordFailure(context);
    } finally {
      currentExecutions.decrementAndGet();
    }
  }

  // Internal delegate implementation
  final CircuitBreakerInternals internals = new CircuitBreakerInternals() {
    @Override
    public int getCurrentExecutions() {
      return currentExecutions.get();
    }

    @Override
    public void open(ExecutionContext context) {
      transitionTo(State.OPEN, onOpen, context);
    }
  };

  @Override
  public PolicyExecutor toExecutor(AbstractExecution execution) {
    return new CircuitBreakerExecutor(this, execution);
  }
}
