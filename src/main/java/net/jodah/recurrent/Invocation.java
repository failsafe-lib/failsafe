package net.jodah.recurrent;

import java.util.concurrent.TimeUnit;

import net.jodah.recurrent.internal.util.Assert;
import net.jodah.recurrent.util.Duration;

/**
 * A retryable invocation.
 * 
 * @author Jonathan Halterman
 * @param <T> result type
 */
public class Invocation {
  private RetryPolicy retryPolicy;
  private RecurrentFuture<Object> future;
  private long startTime;

  /** Count of retry attempts */
  volatile int retryCount;
  /** Wait time in nanoseconds */
  volatile long waitTime;
  /** Indicates whether a retry has been requested */
  volatile boolean retryRequested;

  @SuppressWarnings("unchecked")
  Invocation(RetryPolicy retryPolicy, RecurrentFuture<?> future) {
    this.retryPolicy = retryPolicy;
    this.future = (RecurrentFuture<Object>) future;
    waitTime = retryPolicy.getDelay().toNanos();
    startTime = System.nanoTime();
  }

  /**
   * Completes the invocation.
   */
  public void complete() {
    future.complete(null, null);
  }

  /**
   * Completes the invocation.
   */
  public void complete(Object result) {
    future.complete(result, null);
  }

  /**
   * Gets the number of retries that have been attempted so far.
   */
  public int getRetryCount() {
    return retryCount;
  }

  /**
   * Retries a failed invocation, returning true if the invocation's retry policy has not been exceeded, else false.
   */
  public boolean retry() {
    return retryInternal(null);
  }

  /**
   * Retries a failed invocation, returning true if the invocation's retry policy has not been exceeded, else false.
   * 
   * @throws NullPointerException if {@code failure} is null
   */
  public boolean retry(Throwable failure) {
    Assert.notNull(failure, "failure");
    return retryInternal(failure);
  }

  private boolean retryInternal(Throwable failure) {
    if (retryRequested)
      return true;

    // TODO validate failure against policy if failure != null
    recordFailedAttempt();
    if (!isPolicyExceeded()) {
      retryRequested = true;
      return true;
    }

    if (failure == null)
      failure = new RuntimeException("Retry invocations exceeded");
    future.complete(null, failure);
    return false;
  }

  /**
   * Returns the wait time.
   */
  Duration getWaitTime() {
    return new Duration(waitTime, TimeUnit.NANOSECONDS);
  }

  /**
   * Returns true if the max retries or max duration for the retry policy have been exceeded, else false.
   */
  boolean isPolicyExceeded() {
    boolean withinMaxRetries = retryPolicy.getMaxRetries() == -1 || retryCount <= retryPolicy.getMaxRetries();
    boolean withinMaxDuration = retryPolicy.getMaxDuration() == null
        || System.nanoTime() - startTime < retryPolicy.getMaxDuration().toNanos();
    return !withinMaxRetries || !withinMaxDuration;
  }

  /**
   * Records a failed attempt and adjusts the wait time.
   */
  void recordFailedAttempt() {
    retryCount++;
    adjustForBackoffs();
    adjustForMaxDuration();
  }

  /**
   * Adjusts the wait time for backoffs.
   */
  void adjustForBackoffs() {
    if (retryPolicy.getMaxDelay() != null)
      waitTime = (long) Math.min(waitTime * retryPolicy.getDelayMultiplier(), retryPolicy.getMaxDelay().toNanos());
  }

  /**
   * Adjusts the wait time for max duration.
   */
  void adjustForMaxDuration() {
    if (retryPolicy.getMaxDuration() != null) {
      long elapsedNanos = System.nanoTime() - startTime;
      long maxRemainingWaitTime = retryPolicy.getMaxDuration().toNanos() - elapsedNanos;
      waitTime = Math.min(waitTime, maxRemainingWaitTime < 0 ? 0 : maxRemainingWaitTime);
      if (waitTime < 0)
        waitTime = 0;
    }
  }
}