package net.jodah.recurrent;

import java.util.concurrent.TimeUnit;

import net.jodah.recurrent.internal.util.Assert;

/**
 * Tracks executions and determines when an execution can be performed for a {@link RetryPolicy}.
 * 
 * @author Jonathan Halterman
 */
public class Execution extends ExecutionStats {
  final RetryPolicy retryPolicy;

  // Mutable state
  protected volatile Object lastResult;
  protected volatile Throwable lastFailure;
  protected volatile boolean completed;
  /** Wait time in nanoseconds */
  volatile long waitTime;

  /**
   * Creates a new Execution for the {@code retryPolicy}.
   * 
   * @throws NullPointerException if {@code retryPolicy} is null
   */
  public Execution(RetryPolicy retryPolicy) {
    super(System.nanoTime());
    this.retryPolicy = Assert.notNull(retryPolicy, "retryPolicy");
    waitTime = retryPolicy.getDelay().toNanos();
  }

  /**
   * Returns true if a retry can be performed for the {@code result}, else returns false and completes the execution.
   * 
   * @throws IllegalStateException if the execution is already complete
   */
  public boolean canRetryFor(Object result) {
    return canRetryFor(result, null);
  }

  /**
   * Returns true if a retry can be performed for the {@code result} or {@code failure}, else returns false and
   * completes the execution.
   * 
   * @throws IllegalStateException if the execution is already complete
   */
  public boolean canRetryFor(Object result, Throwable failure) {
    if (complete(result, failure, true))
      return false;
    return canRetryForInternal(result, failure);
  }

  /**
   * Returns true if a retry can be performed for the {@code failure}, else returns false and completes the execution.
   * 
   * @throws NullPointerException if {@code failure} is null
   * @throws IllegalStateException if the execution is already complete
   */
  public boolean canRetryOn(Throwable failure) {
    Assert.notNull(failure, "failure");
    return canRetryFor(null, failure);
  }

  /**
   * Completes the execution.
   * 
   * @throws IllegalStateException if the execution is already complete
   */
  public void complete() {
    complete(null, null, false);
  }

  /**
   * Attempts to complete the execution with the {@code result}. Returns true on success, else false if completion
   * failed and should be retried.
   *
   * @throws IllegalStateException if the execution is already complete
   */
  public boolean complete(Object result) {
    return complete(result, null, true);
  }

  /**
   * Returns the last failure that was recorded.
   * 
   * @see #fail(Throwable)
   */
  @SuppressWarnings("unchecked")
  public <T extends Throwable> T getLastFailure() {
    return (T) lastFailure;
  }

  /**
   * Returns the last result that was recorded.
   * 
   * @see #complete()
   * @see #complete(Object)
   */
  @SuppressWarnings("unchecked")
  public <T> T getLastResult() {
    return (T) lastResult;
  }

  /**
   * Returns the wait time in milliseconds.
   */
  public long getWaitMillis() {
    return TimeUnit.NANOSECONDS.toMillis(waitTime);
  }

  /**
   * Returns the wait time in nanoseconds.
   */
  public long getWaitNanos() {
    return waitTime;
  }

  /**
   * Returns whether the execution is complete.
   * 
   * @see #complete()
   * @see #complete(Object)
   * @see #fail(Throwable)
   */
  public boolean isComplete() {
    return completed;
  }

  /**
   * Fails the execution attempt and returns true if a retry can be performed for the {@code failure}, else returns
   * false and completes the execution.
   * 
   * <p>
   * Alias of {@link #canRetryOn(Throwable)}
   * 
   * @throws IllegalStateException if the execution is already complete
   */
  public boolean fail(Throwable failure) {
    return canRetryFor(null, failure);
  }

  /**
   * Increments attempt counts and returns whether the policy has been exceeded.
   */
  boolean canRetryForInternal(Object result, Throwable failure) {
    lastResult = result;
    lastFailure = failure;
    incrementAttempts();
    return !(completed = isPolicyExceeded());
  }

  boolean complete(Object result, Throwable failure, boolean checkArgs) {
    Assert.state(!completed, "Execution has already been completed");
    lastResult = result;
    lastFailure = failure;
    if (checkArgs && retryPolicy.allowsRetriesFor(result, failure))
      return false;
    incrementAttempts();
    return completed = true;
  }

  /**
   * Adjusts the wait time for backoffs.
   */
  private void adjustForBackoffs() {
    if (retryPolicy.getMaxDelay() != null)
      waitTime = (long) Math.min(waitTime * retryPolicy.getDelayMultiplier(), retryPolicy.getMaxDelay().toNanos());
  }

  /**
   * Adjusts the wait time for max duration.
   */
  private void adjustForMaxDuration() {
    if (retryPolicy.getMaxDuration() != null) {
      long elapsedNanos = getElapsedNanos();
      long maxRemainingWaitTime = retryPolicy.getMaxDuration().toNanos() - elapsedNanos;
      waitTime = Math.min(waitTime, maxRemainingWaitTime < 0 ? 0 : maxRemainingWaitTime);
      if (waitTime < 0)
        waitTime = 0;
    }
  }

  private void incrementAttempts() {
    attempts++;
    adjustForBackoffs();
    adjustForMaxDuration();
  }

  /**
   * Returns true if the max retries or max duration for the retry policy have been exceeded, else false.
   */
  private boolean isPolicyExceeded() {
    boolean withinMaxRetries = retryPolicy.getMaxRetries() == -1 || attempts <= retryPolicy.getMaxRetries();
    boolean withinMaxDuration = retryPolicy.getMaxDuration() == null
        || System.nanoTime() - startTime < retryPolicy.getMaxDuration().toNanos();
    return !withinMaxRetries || !withinMaxDuration;
  }
}