package net.jodah.failsafe.internal;

import net.jodah.failsafe.CircuitBreaker.State;

/**
 * The state of a circuit.
 * 
 * @author Jonathan Halterman
 */
public interface CircuitState {
  boolean allowsExecution(CircuitBreakerStats stats);

  State getState();

  void recordFailure();

  void recordSuccess();
}