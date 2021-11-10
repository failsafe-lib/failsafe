package net.jodah.failsafe;

import net.jodah.failsafe.event.CircuitBreakerStateChangedEvent;
import net.jodah.failsafe.event.EventListener;

import java.time.Duration;

/**
 * Configuration for a {@link CircuitBreaker}.
 * <p>
 * This class is threadsafe.
 * </p>
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public class CircuitBreakerConfig<R> extends DelayablePolicyConfig<R> {
  // Failure config
  int failureThreshold;
  int failureRateThreshold;
  int failureThresholdingCapacity;
  int failureExecutionThreshold;
  Duration failureThresholdingPeriod;

  // Success config
  int successThreshold;
  int successThresholdingCapacity;

  // Listeners
  volatile EventListener<CircuitBreakerStateChangedEvent> openListener;
  volatile EventListener<CircuitBreakerStateChangedEvent> halfOpenListener;
  volatile EventListener<CircuitBreakerStateChangedEvent> closeListener;

  CircuitBreakerConfig() {
  }

  CircuitBreakerConfig(CircuitBreakerConfig<R> config) {
    super(config);
    failureThreshold = config.failureThreshold;
    failureRateThreshold = config.failureRateThreshold;
    failureThresholdingCapacity = config.failureThresholdingCapacity;
    failureExecutionThreshold = config.failureExecutionThreshold;
    failureThresholdingPeriod = config.failureThresholdingPeriod;
    successThreshold = config.successThreshold;
    successThresholdingCapacity = config.successThresholdingCapacity;
    openListener = config.openListener;
    halfOpenListener = config.halfOpenListener;
    closeListener = config.closeListener;
  }

  /**
   * Returns the delay before allowing another execution on the circuit. Defaults to 1 minute.
   *
   * @see CircuitBreakerBuilder#withDelay(Duration)
   * @see CircuitBreaker#getRemainingDelay()
   */
  public Duration getDelay() {
    return delay;
  }

  /**
   * Gets the number of successive failures that must occur within the {@link #getFailureThresholdingCapacity() failure
   * thresholding capacity} when in a CLOSED or HALF_OPEN state in order to open the circuit. Returns {@code 1} by
   * default.
   *
   * @see CircuitBreakerBuilder#withFailureThreshold(int)
   * @see CircuitBreakerBuilder#withFailureThreshold(int, int)
   */
  public int getFailureThreshold() {
    return failureThreshold;
  }

  /**
   * Returns the rolling capacity for storing execution results when performing failure thresholding in the CLOSED or
   * HALF_OPEN states. {@code 1} by default. Only the most recent executions that fit within this capacity contribute to
   * thresholding decisions.
   *
   * @see CircuitBreakerBuilder#withFailureThreshold(int)
   * @see CircuitBreakerBuilder#withFailureThreshold(int, int)
   */
  public int getFailureThresholdingCapacity() {
    return failureThresholdingCapacity;
  }

  /**
   * Used with time based thresholding. Returns percentage rate of failures, from 1 to 100, that must occur when in a
   * CLOSED or HALF_OPEN state in order to open the circuit, else {@code 0} if failure rate thresholding is not
   * configured.
   *
   * @see CircuitBreakerBuilder#withFailureRateThreshold(int, int, Duration)
   */
  public int getFailureRateThreshold() {
    return failureRateThreshold;
  }

  /**
   * Used with time based thresholding. Returns the rolling time period during which failure thresholding is performed
   * when in the CLOSED state, else {@code null} if time based failure thresholding is not configured. Only the most
   * recent executions that occurred within this rolling time period contribute to thresholding decisions.
   *
   * @see CircuitBreakerBuilder#withFailureThreshold(int, Duration)
   * @see CircuitBreakerBuilder#withFailureThreshold(int, int, Duration)
   * @see CircuitBreakerBuilder#withFailureRateThreshold(int, int, Duration)
   */
  public Duration getFailureThresholdingPeriod() {
    return failureThresholdingPeriod;
  }

  /**
   * Used with time based thresholding. Returns the minimum number of executions that must be recorded in the CLOSED
   * state before the breaker can be opened. For {@link CircuitBreakerBuilder#withFailureRateThreshold(int, int,
   * Duration) failure rate thresholding} this also determines the minimum number of executions that must be recorded in
   * the HALF_OPEN state. Returns {@code 0} by default.
   *
   * @see CircuitBreakerBuilder#withFailureThreshold(int, int, Duration)
   * @see CircuitBreakerBuilder#withFailureRateThreshold(int, int, Duration)
   */
  public int getFailureExecutionThreshold() {
    return failureExecutionThreshold;
  }

  /**
   * Gets the number of successive successes that must occur within the {@link #getSuccessThresholdingCapacity() success
   * thresholding capacity} when in a HALF_OPEN state in order to open the circuit. Returns {@code 0} by default, in
   * which case the {@link #getFailureThreshold() failure threshold} is used instead.
   *
   * @see CircuitBreakerBuilder#withSuccessThreshold(int)
   * @see CircuitBreakerBuilder#withSuccessThreshold(int, int)
   */
  public int getSuccessThreshold() {
    return successThreshold;
  }

  /**
   * Returns the rolling capacity for storing execution results when performing success thresholding in the HALF_OPEN
   * state. Only the most recent executions that fit within this capacity contribute to thresholding decisions.
   *
   * @see CircuitBreakerBuilder#withSuccessThreshold(int)
   * @see CircuitBreakerBuilder#withSuccessThreshold(int, int)
   */
  public int getSuccessThresholdingCapacity() {
    return successThresholdingCapacity;
  }

  /**
   * Returns the open event listener.
   *
   * @see CircuitBreakerListeners#onOpen(EventListener)
   */
  public EventListener<CircuitBreakerStateChangedEvent> getOpenListener() {
    return openListener;
  }

  /**
   * Returns the half-open event listener.
   *
   * @see CircuitBreakerListeners#onHalfOpen(EventListener)
   */
  public EventListener<CircuitBreakerStateChangedEvent> getHalfOpenListener() {
    return halfOpenListener;
  }

  /**
   * Returns the close event listener.
   *
   * @see CircuitBreakerListeners#onClose(EventListener)
   */
  public EventListener<CircuitBreakerStateChangedEvent> getCloseListener() {
    return closeListener;
  }
}
