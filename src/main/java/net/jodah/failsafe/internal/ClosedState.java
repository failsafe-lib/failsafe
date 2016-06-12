package net.jodah.failsafe.internal;

import net.jodah.failsafe.CircuitBreaker;
import net.jodah.failsafe.CircuitBreaker.State;
import net.jodah.failsafe.internal.util.CircularBitSet;
import net.jodah.failsafe.util.Ratio;

public class ClosedState implements CircuitState {
  private final CircuitBreaker circuit;
  private final Integer failureThresh;
  private final Ratio failureRatio;

  private volatile int executions;
  private volatile int successiveFailures;
  private CircularBitSet bitSet;

  public ClosedState(CircuitBreaker circuit) {
    this.circuit = circuit;
    this.failureThresh = circuit.getFailureThreshold();
    this.failureRatio = circuit.getFailureThresholdRatio();
    if (failureRatio != null)
      bitSet = new CircularBitSet(failureRatio.denominator);
  }

  @Override
  public boolean allowsExecution(CircuitBreakerStats stats) {
    return true;
  }

  @Override
  public State getState() {
    return State.CLOSED;
  }

  @Override
  public synchronized void recordFailure() {
    executions++;
    successiveFailures++;
    if (bitSet != null)
      bitSet.setNext(false);
    checkThreshold();
  }

  @Override
  public synchronized void recordSuccess() {
    executions++;
    successiveFailures = 0;
    if (bitSet != null)
      bitSet.setNext(true);
    checkThreshold();
  }

  /**
   * Checks to determine if a threshold has been met and the circuit should be opened or closed.
   * 
   * <p>
   * When a failure ratio is configured, the circuit is opened after the expected number of executions based on whether
   * the ratio was exceeded.
   * <p>
   * If a failure threshold is configured, the circuit is opened if the expected number of executions fails else it's
   * closed if a single execution succeeds.
   */
  synchronized void checkThreshold() {
    // Handle failure threshold ratio
    if (failureRatio != null && executions >= failureRatio.denominator && bitSet.negativeRatio() >= failureRatio.ratio)
      circuit.open();

    // Handle failure threshold
    if (failureThresh != null && successiveFailures == failureThresh)
      circuit.open();

    // Handle no thresholds configured
    if (failureThresh == null && failureRatio == null && successiveFailures == 1)
      circuit.open();
  }
}