package net.jodah.failsafe.internal;

import net.jodah.failsafe.CircuitBreaker;
import net.jodah.failsafe.CircuitBreaker.State;
import net.jodah.failsafe.internal.util.CircularBitSet;
import net.jodah.failsafe.util.Ratio;

public class HalfOpenState extends CircuitState {
  private final CircuitBreaker circuit;
  private CircularBitSet bitSet;

  public HalfOpenState(CircuitBreaker circuit) {
    this.circuit = circuit;
    setSuccessThreshold(circuit.getSuccessThreshold() != null ? circuit.getSuccessThreshold()
        : circuit.getFailureThreshold() != null ? circuit.getFailureThreshold() : ONE_OF_ONE);
  }

  @Override
  public boolean allowsExecution(CircuitBreakerStats stats) {
    return stats.getCurrentExecutions() < maxConcurrentExecutions();
  }

  @Override
  public State getState() {
    return State.HALF_OPEN;
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
    if (circuit.getSuccessThreshold() == null)
      bitSet = new CircularBitSet(threshold.denominator, bitSet);
  }

  @Override
  public void setSuccessThreshold(Ratio threshold) {
    bitSet = new CircularBitSet(threshold.denominator, bitSet);
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
    Ratio successRatio = circuit.getSuccessThreshold();
    Ratio failureRatio = circuit.getFailureThreshold();

    if (successRatio != null) {
      if (bitSet.occupiedBits() == successRatio.denominator
          || (successRatio.ratio == 1.0 && bitSet.positiveRatio() < 1.0))
        if (bitSet.positiveRatio() >= successRatio.ratio)
          circuit.close();
        else
          circuit.open();
    } else if (failureRatio != null) {
      if (bitSet.occupiedBits() == failureRatio.denominator
          || (failureRatio.ratio == 1.0 && bitSet.negativeRatio() < 1.0))
        if (bitSet.negativeRatio() >= failureRatio.ratio)
          circuit.open();
        else
          circuit.close();
    } else {
      if (bitSet.positiveRatio() == 1)
        circuit.close();
      else
        circuit.open();
    }
  }

  /**
   * Returns the max allowed concurrent executions.
   */
  int maxConcurrentExecutions() {
    if (circuit.getMaxConcurrency() != -1)
      return circuit.getMaxConcurrency();
    if (circuit.getSuccessThreshold() != null)
      return circuit.getSuccessThreshold().denominator;
    else if (circuit.getFailureThreshold() != null)
      return circuit.getFailureThreshold().denominator;
    else
      return 1;
  }
}