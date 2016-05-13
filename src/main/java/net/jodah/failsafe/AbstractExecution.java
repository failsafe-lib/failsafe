package net.jodah.failsafe;

import java.util.concurrent.TimeUnit;

import net.jodah.failsafe.internal.util.Assert;
import net.jodah.failsafe.util.Duration;

abstract class AbstractExecution extends ExecutionContext {
  final RetryPolicy retryPolicy;
  final CircuitBreaker circuitBreaker;

  // Mutable state
  long attemptStartTime;
  volatile Object lastResult;
  volatile Throwable lastFailure;
  volatile boolean completed;
  volatile boolean success;
  volatile long waitNanos;

  /**
   * Creates a new Execution for the {@code retryPolicy} and {@code circuitBreaker}.
   * 
   * @throws NullPointerException if {@code retryPolicy} is null
   */
  public AbstractExecution(RetryPolicy retryPolicy, CircuitBreaker circuitBreaker) {
    super(new Duration(System.nanoTime(), TimeUnit.NANOSECONDS));
    this.retryPolicy = Assert.notNull(retryPolicy, "retryPolicy");
    this.circuitBreaker = circuitBreaker;
    waitNanos = retryPolicy.getDelay().toNanos();
  }

  /**
   * Returns the last failure that was recorded.
   */
  @SuppressWarnings("unchecked")
  public <T extends Throwable> T getLastFailure() {
    return (T) lastFailure;
  }

  /**
   * Returns the last result that was recorded.
   */
  @SuppressWarnings("unchecked")
  public <T> T getLastResult() {
    return (T) lastResult;
  }

  /**
   * Returns the time to wait before the next execution attempt.
   */
  public Duration getWaitTime() {
    return new Duration(waitNanos, TimeUnit.NANOSECONDS);
  }

  /**
   * Returns whether the execution is complete.
   */
  public boolean isComplete() {
    return completed;
  }

  void before() {
    if (circuitBreaker != null)
      circuitBreaker.before();
    attemptStartTime = System.nanoTime();
  }

  /**
   * Records and attempts to complete the execution, returning true if complete else false.
   * 
   * @throws IllegalStateException if the execution is already complete
   */
  boolean complete(Object result, Throwable failure, boolean checkArgs) {
    Assert.state(!completed, "Execution has already been completed");
    executions++;
    lastResult = result;
    lastFailure = failure;
    long elapsedNanos = getElapsedTime().toNanos();

    // Record the execution with the circuit breaker
    if (circuitBreaker != null) {
      Duration timeout = circuitBreaker.getTimeout();
      boolean timeoutExceeded = timeout != null && elapsedNanos >= timeout.toNanos();
      if (circuitBreaker.isFailure(result, failure) || timeoutExceeded)
        circuitBreaker.recordFailure();
      else
        circuitBreaker.recordSuccess();
    }

    // Adjust the wait time for max duration
    if (retryPolicy.getMaxDuration() != null) {
      long maxRemainingWaitTime = retryPolicy.getMaxDuration().toNanos() - elapsedNanos;
      waitNanos = Math.min(waitNanos, maxRemainingWaitTime < 0 ? 0 : maxRemainingWaitTime);
      if (waitNanos < 0)
        waitNanos = 0;
    }

    // Adjust the wait time for backoffs
    if (retryPolicy.getMaxDelay() != null)
      waitNanos = (long) Math.min(waitNanos * retryPolicy.getDelayMultiplier(), retryPolicy.getMaxDelay().toNanos());

    boolean maxRetriesExceeded = retryPolicy.getMaxRetries() != -1 && executions > retryPolicy.getMaxRetries();
    boolean maxDurationExceeded = retryPolicy.getMaxDuration() != null
        && elapsedNanos > retryPolicy.getMaxDuration().toNanos();
    boolean shouldAbort = retryPolicy.canAbortFor(result, failure);
    boolean shouldRetry = checkArgs && retryPolicy.canRetryFor(result, failure);

    completed = maxRetriesExceeded || maxDurationExceeded || !shouldRetry || shouldAbort;
    success = completed && !shouldRetry && !shouldAbort && failure == null;
    return completed;
  }
}
