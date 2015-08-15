package net.jodah.recurrent;

import net.jodah.recurrent.internal.util.Assert;

/**
 * Tracks invocations and determines when an invocation can be performed for a {@link RetryPolicy}.
 * 
 * @author Jonathan Halterman
 */
public class Invocation {
  final RetryPolicy retryPolicy;
  private final long startTime;

  // Mutable state
  protected volatile Object lastResult;
  protected volatile Throwable lastFailure;
  protected volatile boolean completed;
  /** Number of attempts */
  volatile int attempts;
  /** Wait time in nanoseconds */
  volatile long waitTime;

  public Invocation(RetryPolicy retryPolicy) {
    this.retryPolicy = retryPolicy;
    waitTime = retryPolicy.getDelay().toNanos();
    startTime = System.nanoTime();
  }

  /**
   * Returns true if a retry can be performed for the {@code result}, else returns false and completes the invocation.
   * 
   * @throws IllegalStateException if the invocation is already complete
   */
  public boolean canRetryFor(Object result) {
    return canRetryFor(result, null);
  }

  /**
   * Returns true if a retry can be performed for the {@code result} or {@code failure}, else returns false and
   * completes the invocation.
   * 
   * @throws IllegalStateException if the invocation is already complete
   */
  public boolean canRetryFor(Object result, Throwable failure) {
    lastResult = result;
    lastFailure = failure;
    if (complete(result, failure, true))
      return false;
    incrementAttempts();
    return !(completed = isPolicyExceeded());
  }

  /**
   * Returns true if a retry can be performed for the {@code failure}, else returns false and completes the invocation.
   * 
   * @throws IllegalStateException if the invocation is already complete
   */
  public boolean canRetryOn(Throwable failure) {
    return canRetryFor(null, failure);
  }

  /**
   * Completes the invocation.
   * 
   * @throws IllegalStateException if the invocation is already complete
   */
  public void complete() {
    complete(null, null, false);
  }

  /**
   * Returns true if a retry can be performed for the {@code result} or {@code failure}, else returns false and records
   * and completes the invocation.
   *
   * @throws IllegalStateException if the invocation is already complete
   */
  public boolean complete(Object result) {
    return complete(result, null, true);
  }

  /**
   * Gets the number of invocation attempts so far. Invocation attempts are recorded when {@code canRetry} is called or
   * when the invocation is completed successfully.
   */
  public int getAttemptCount() {
    return attempts;
  }

  /**
   * Returns the last failure that was recorded.
   * 
   * @see #recordFailure(Throwable)
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
   * Returns the wait time in nanoseconds.
   */
  public long getWaitTime() {
    return waitTime;
  }

  /**
   * Returns whether the invocation is complete.
   * 
   * @see #complete()
   * @see #complete(Object)
   * @see #recordFailure(Throwable)
   */
  public boolean isComplete() {
    return completed;
  }

  /**
   * Records a failed invocation attempt and returns true if a retry can be performed for the {@code failure}, else
   * returns false and completes the invocation.
   * 
   * <p>
   * Alias of {@link #canRetryOn(Throwable)}
   * 
   * @throws IllegalStateException if the invocation is already complete
   */
  public boolean recordFailure(Throwable failure) {
    return canRetryFor(null, failure);
  }

  boolean complete(Object result, Throwable failure, boolean checkArgs) {
    Assert.state(!completed, "Invocation has already been completed");
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
      long elapsedNanos = System.nanoTime() - startTime;
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