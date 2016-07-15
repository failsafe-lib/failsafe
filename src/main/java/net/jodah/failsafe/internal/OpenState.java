package net.jodah.failsafe.internal;

import net.jodah.failsafe.CircuitBreaker;
import net.jodah.failsafe.CircuitBreaker.State;

public class OpenState extends CircuitState {
  private final CircuitBreaker circuit;
  private final long startTime = System.nanoTime();

  public OpenState(CircuitBreaker circuit) {
    this.circuit = circuit;
  }

  @Override
  public boolean allowsExecution(CircuitBreakerStats stats) {
    if (System.nanoTime() - startTime >= circuit.getDelay().toNanos()) {
      circuit.halfOpen();
      return true;
    }

    return false;
  }

  @Override
  public State getState() {
    return State.OPEN;
  }
}