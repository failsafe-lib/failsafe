package net.jodah.failsafe.internal;

import net.jodah.failsafe.CircuitBreaker;
import net.jodah.failsafe.CircuitBreaker.State;
import net.jodah.failsafe.internal.util.CircularBitSet;
import net.jodah.failsafe.internal.util.Ratio;

public class HalfOpenState implements CircuitState {
  private final CircuitBreaker circuit;
  private final Integer successThresh;
  private final Integer failureThresh;
  private final Ratio successRatio;
  private final Ratio failureRatio;
  private final int maxConcurrentExecutions;

  private volatile int executions;
  private volatile int successiveSuccesses;
  private volatile int successiveFailures;
  private CircularBitSet bitSet;

  public HalfOpenState(CircuitBreaker circuit) {
    this.circuit = circuit;
    this.successThresh = circuit.getSuccessThreshold();
    this.failureThresh = circuit.getFailureThreshold();
    this.successRatio = circuit.getSuccessThresholdRatio();
    this.failureRatio = circuit.getFailureThresholdRatio();
    maxConcurrentExecutions = maxConcurrentExecutions();

    if (successRatio != null)
      bitSet = new CircularBitSet(successRatio.denominator);
    if (failureRatio != null)
      bitSet = new CircularBitSet(failureRatio.denominator);
  }

  @Override
  public boolean allowsExecution(CircuitBreakerStats stats) {
    return stats.getCurrentExecutions() < maxConcurrentExecutions;
  }

  @Override
  public State getState() {
    return State.HALF_OPEN;
  }

  @Override
  public synchronized void recordFailure() {
    executions++;
    successiveFailures++;
    successiveSuccesses = 0;
    if (bitSet != null)
      bitSet.setNext(false);
    checkThreshold();
  }

  @Override
  public synchronized void recordSuccess() {
    executions++;
    successiveSuccesses++;
    successiveFailures = 0;
    if (bitSet != null)
      bitSet.setNext(true);
    checkThreshold();
  }

  /**
   * Returns the max allowed concurrent executions.
   */
  int maxConcurrentExecutions() {
    if (successRatio != null)
      return successRatio.denominator;
    else if (successThresh != null)
      return successThresh;
    else if (failureRatio != null)
      return failureRatio.denominator;
    else if (failureThresh != null)
      return failureThresh;
    else
      return 1;
  }

  /**
   * Checks to determine if a threshold has been met and the circuit should be opened or closed.
   * 
   * <p>
   * When a success or failure ratio is configured, the circuit is opened or closed after the expected number of
   * executions based on whether the ratio was exceeded.
   * <p>
   * If a success threshold is configured, the circuit is closed if the expected number of executions are successful
   * else it's opened if a single execution fails.
   * <p>
   * If a failure threshold is configured, the circuit is opened if the expected number of executions fails else it's
   * closed if a single execution succeeds.
   */
  synchronized void checkThreshold() {
    // Handle success threshold ratio
    if (successRatio != null && executions == successRatio.denominator) {
      if (bitSet.positiveRatio() >= successRatio.ratio)
        circuit.close();
      else
        circuit.open();
    }

    // Handle success threshold
    if (successThresh != null) {
      if (successiveSuccesses == successThresh)
        circuit.close();
      else if (failureThresh == null && failureRatio == null && successiveFailures == 1)
        circuit.open();
    }

    // Handle failure threshold ratio
    if (failureRatio != null && executions == failureRatio.denominator) {
      if (bitSet.negativeRatio() >= failureRatio.ratio)
        circuit.open();
      else
        circuit.close();
    }

    // Handle failure threshold
    if (failureThresh != null) {
      if (successiveFailures == failureThresh)
        circuit.open();
      else if (successThresh == null && successRatio == null && successiveSuccesses == 1)
        circuit.close();
    }

    // Handle no thresholds configured
    if (successThresh == null && failureThresh == null && successRatio == null && failureRatio == null) {
      if (successiveSuccesses == 1)
        circuit.close();
      else
        circuit.open();
    }
  }
}