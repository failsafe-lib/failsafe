package net.jodah.failsafe.metrics;

import net.jodah.failsafe.CircuitBreaker;

public interface MetricsCollector {

  void markFailure(CircuitBreaker.State state);

  void markSuccess(CircuitBreaker.State state);

  void markFailure(CircuitBreaker.State state, Throwable failure);

  void markSuccess(CircuitBreaker.State state, Object result);

  void markTransition(CircuitBreaker.State state, CircuitBreaker.State newState);
}
