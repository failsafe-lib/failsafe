package net.jodah.failsafe.internal;

import net.jodah.failsafe.CircuitBreaker;
import net.jodah.failsafe.CircuitBreaker.State;
import net.jodah.failsafe.internal.util.CircularBitSet;
import net.jodah.failsafe.util.Ratio;

public class ClosedState extends CircuitState {
  private final CircuitBreaker circuit;
  private CircularBitSet bitSet;

  public ClosedState(CircuitBreaker circuit) {
    this.circuit = circuit;
    setFailureThreshold(circuit.getFailureThreshold() != null ? circuit.getFailureThreshold() : ONE_OF_ONE);
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
    bitSet = new CircularBitSet(threshold.denominator, bitSet);
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
    Ratio failureRatio = circuit.getFailureThreshold();

    // Handle failure threshold ratio
    if (failureRatio != null && bitSet.occupiedBits() >= failureRatio.denominator
        && bitSet.negativeRatio() >= failureRatio.ratio)
      circuit.open();

    // Handle no thresholds configured
    if (failureRatio == null && bitSet.negativeRatio() == 1)
      circuit.open();
  }
}