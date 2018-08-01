package net.jodah.failsafe.metrics;

import net.jodah.failsafe.CircuitBreaker;

public class MetricsCollectorGracefulDecorator implements MetricsCollector {

  private final MetricsCollector decorated;

  public MetricsCollectorGracefulDecorator(MetricsCollector decorated) {
    this.decorated = decorated;
  }

  @Override
  public void markFailure(CircuitBreaker.State state) {
    try {
      decorated.markFailure(state);
    } catch(Exception e) {
      /* failed to mark failure - if logging is ever added - add it here */
    }
  }

  @Override
  public void markSuccess(CircuitBreaker.State state) {
    try {
      decorated.markSuccess(state);
    } catch(Exception e) {
      /* failed to mark success - if logging is ever added - add it here */
    }
  }

  @Override
  public void markFailure(CircuitBreaker.State state, Throwable failure) {
    try {
      decorated.markFailure(state, failure);
    } catch(Exception e) {
      /* failed to mark failure - if logging is ever added - add it here */
    }
  }

  @Override
  public void markSuccess(CircuitBreaker.State state, Object result) {
    try {
      decorated.markSuccess(state, result);
    } catch(Exception e) {
      /* failed to mark success - if logging is ever added - add it here */
    }
  }

  @Override
  public void markTransition(
      CircuitBreaker.State previousState, CircuitBreaker.State newState
  ) {
    try {
      decorated.markTransition(previousState, newState);
    } catch(Exception e) {
      /* failed to mark transition- if logging is ever added - add it here */
    }
  }
}
