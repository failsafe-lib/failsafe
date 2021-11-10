package net.jodah.failsafe.event;

import net.jodah.failsafe.CircuitBreaker.State;

/**
 * Indicates a circuit breaker's state changed.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public class CircuitBreakerStateChangedEvent {
  private final State previousState;

  public CircuitBreakerStateChangedEvent(State previousState) {
    this.previousState = previousState;
  }

  /**
   * Returns the previous state of the circuit breaker.
   */
  public State getPreviousState() {
    return previousState;
  }
}
