package net.jodah.failsafe.internal;

import net.jodah.failsafe.CircuitBreaker;
import net.jodah.failsafe.CircuitBreaker.State;
import net.jodah.failsafe.util.Duration;

public class OpenState implements CircuitState {
  private final CircuitBreaker circuit;
  private final Duration delay;
  private final long startTime = System.nanoTime();

  public OpenState(CircuitBreaker circuit) {
    this.circuit = circuit;
    this.delay = circuit.getDelay();
  }

  @Override
  public boolean allowsExecution(CircuitBreakerStats stats) {
    if (System.nanoTime() - startTime >= delay.toNanos()) {
      circuit.halfOpen();
      return true;
    }

    return false;
  }

  @Override
  public State getState() {
    return State.OPEN;
  }

  @Override
  public void recordFailure() {
    throw new IllegalStateException("Cannot record result for open circuit");
  }

  @Override
  public void recordSuccess() {
    throw new IllegalStateException("Cannot record result for open circuit");
  }
}