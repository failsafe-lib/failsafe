package net.jodah.failsafe;

import net.jodah.failsafe.internal.util.Assert;

/**
 * Tracks executions and determines when an execution can be performed for a {@link RetryPolicy}.
 * 
 * @author Jonathan Halterman
 */
public class Execution extends AbstractExecution {
  /**
   * Creates a new Execution for the {@code circuitBreaker}.
   * 
   * @throws NullPointerException if {@code circuitBreaker} is null
   */
  public Execution(CircuitBreaker circuitBreaker) {
    super(null, Assert.notNull(circuitBreaker, "circuitBreaker"), null);
  }

  /**
   * Creates a new Execution for the {@code retryPolicy}.
   * 
   * @throws NullPointerException if {@code retryPolicy} is null
   */
  public Execution(RetryPolicy retryPolicy) {
    super(Assert.notNull(retryPolicy, "retryPolicy"), null, null);
  }

  /**
   * Creates a new Execution for the {@code retryPolicy} and {@code circuitBreaker}.
   * 
   * @throws NullPointerException if {@code retryPolicy} or {@code circuitBreaker} are null
   */
  public Execution(RetryPolicy retryPolicy, CircuitBreaker circuitBreaker) {
    super(Assert.notNull(retryPolicy, "retryPolicy"), Assert.notNull(circuitBreaker, "circuitBreaker"), null);
  }

  Execution(RetryPolicy retryPolicy, CircuitBreaker circuitBreaker, ListenerConfig<?, Object> listeners) {
    super(retryPolicy, circuitBreaker, listeners);
  }

  /**
   * Records an execution and returns true if a retry can be performed for the {@code result}, else returns false and
   * marks the execution as complete.
   * 
   * @throws IllegalStateException if the execution is already complete
   */
  public boolean canRetryFor(Object result) {
    return !complete(result, null, true);
  }

  /**
   * Records an execution and returns true if a retry can be performed for the {@code result} or {@code failure}, else
   * returns false and marks the execution as complete.
   * 
   * @throws IllegalStateException if the execution is already complete
   */
  public boolean canRetryFor(Object result, Throwable failure) {
    return !complete(result, failure, true);
  }

  /**
   * Records an execution and returns true if a retry can be performed for the {@code failure}, else returns false and
   * marks the execution as complete.
   * 
   * @throws NullPointerException if {@code failure} is null
   * @throws IllegalStateException if the execution is already complete
   */
  public boolean canRetryOn(Throwable failure) {
    Assert.notNull(failure, "failure");
    return !complete(null, failure, true);
  }

  /**
   * Records and completes the execution.
   * 
   * @throws IllegalStateException if the execution is already complete
   */
  public void complete() {
    complete(null, null, false);
  }

  /**
   * Records and attempts to complete the execution with the {@code result}. Returns true on success, else false if
   * completion failed and execution should be retried.
   *
   * @throws IllegalStateException if the execution is already complete
   */
  public boolean complete(Object result) {
    return complete(result, null, true);
  }

  /**
   * Records a failed execution and returns true if a retry can be performed for the {@code failure}, else returns false
   * and completes the execution.
   * 
   * <p>
   * Alias of {@link #canRetryOn(Throwable)}
   * 
   * @throws NullPointerException if {@code failure} is null
   * @throws IllegalStateException if the execution is already complete
   */
  public boolean recordFailure(Throwable failure) {
    return canRetryOn(failure);
  }
}