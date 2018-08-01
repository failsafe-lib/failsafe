package net.jodah.failsafe.metrics;

import net.jodah.failsafe.CircuitBreaker;

public class NoOpMetricsCollector implements MetricsCollector {

  @Override
  public void markFailure(CircuitBreaker.State state) { }

  @Override
  public void markSuccess(CircuitBreaker.State state) { }

  @Override
  public void markFailure(CircuitBreaker.State state, Throwable failure) { }

  @Override
  public void markSuccess(CircuitBreaker.State state, Object result) { }

  @Override
  public void markTransition(CircuitBreaker.State state, CircuitBreaker.State newState) { }
}
