package net.jodah.failsafe.internal;

import net.jodah.failsafe.CircuitBreaker.State;
import net.jodah.failsafe.util.Ratio;

/**
 * The state of a circuit.
 * 
 * @author Jonathan Halterman
 */
public abstract class CircuitState {
  static final Ratio ONE_OF_ONE = new Ratio(1, 1);

  public abstract boolean allowsExecution(CircuitBreakerStats stats);

  public abstract State getState();

  public void recordFailure() {
  }

  public void recordSuccess() {
  }

  public void setFailureThreshold(Ratio threshold) {
  }

  public void setSuccessThreshold(Ratio threshold) {
  }
}