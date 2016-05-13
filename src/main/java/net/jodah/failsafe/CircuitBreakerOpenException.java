package net.jodah.failsafe;

/**
 * Thrown when an execution is attempted while a configured CircuitBreaker is open.
 * 
 * @author Jonathan Halterman
 */
public class CircuitBreakerOpenException extends RuntimeException {
  private static final long serialVersionUID = 1L;
}
