package net.jodah.failsafe.internal;

/**
 * Private CircuitBreaker stats APIs.
 * 
 * @author Jonathan Halterman
 */
public interface CircuitBreakerStats {
  /**
   * Returns the current number of executions occurring on the circuit breaker. Executions are started when a
   * {@code Failsafe} call begins and ended when a result is recorded.
   */
  public int getCurrentExecutions();
}
