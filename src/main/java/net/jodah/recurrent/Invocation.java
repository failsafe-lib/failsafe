package net.jodah.recurrent;

import java.util.concurrent.TimeUnit;

import net.jodah.recurrent.internal.util.Assert;
import net.jodah.recurrent.util.Duration;

/**
 * A retryable invocation.
 * 
 * @author Jonathan Halterman
 */
public class Invocation {
  final RetryPolicy retryPolicy;
  private final long startTime;

  // Internal mutable state
  /** Count of retry attempts */
  volatile int retryCount;
  /** Wait time in nanoseconds */
  volatile long waitTime;

  // User state
  volatile boolean retryRequested;
  volatile boolean completionRequested;
  volatile Object result;
  volatile Throwable failure;

  Invocation(RetryPolicy retryPolicy) {
    this.retryPolicy = retryPolicy;
    waitTime = retryPolicy.getDelay().toNanos();
    startTime = System.nanoTime();
  }

  /**
   * Completes the invocation, allowing any futures waiting on the invocation to complete.
   * 
   * @throws IllegalStateException if complete or retry has already been called
   */
  public void complete() {
    this.complete(null);
  }

  /**
   * Completes the invocation with the given {@code failure}, allowing any futures waiting on the invocation to
   * complete.
   * 
   * @throws IllegalStateException if complete or retry has already been called
   */
  public void completeExceptionally(Throwable failure) {
    Assert.state(!completionRequested, "Complete has already been called");
    Assert.state(!retryRequested, "Retry has already been called");
    completionRequested = true;
    this.failure = failure;
  }

  /**
   * Completes the invocation with the {@code result}, allowing any futures waiting on the invocation to complete.
   * 
   * @throws IllegalStateException if complete or retry has already been called
   */
  public void complete(Object result) {
    Assert.state(!completionRequested, "Complete has already been called");
    Assert.state(!retryRequested, "Retry has already been called");
    completionRequested = true;
    this.result = result;
  }

  /**
   * Gets the number of retries that have been attempted so far.
   */
  public int getRetryCount() {
    return retryCount;
  }

  /**
   * Retries a failed invocation, returning true if the invocation's retry policy has not been exceeded, else false.
   * 
   * @throws IllegalStateException if retry or complete has already been called
   */
  public boolean retry() {
    return retryInternal(null);
  }

  /**
   * Retries a failed invocation, returning true if the invocation's retry policy has not been exceeded, else false.
   * 
   * @throws NullPointerException if {@code failure} is null
   * @throws IllegalStateException if retry or complete has already been called
   */
  public boolean retry(Throwable failure) {
    Assert.notNull(failure, "failure");
    return retryInternal(failure);
  }

  private boolean retryInternal(Throwable failure) {
    Assert.state(!retryRequested, "Retry has already been called");
    Assert.state(!completionRequested, "Retry has already been called");

    recordFailedAttempt();
    if ((failure == null || retryPolicy.allowsRetriesFor(failure)) && !isPolicyExceeded()) {
      retryRequested = true;
      return true;
    }

    if (failure == null)
      failure = new RuntimeException("Retry invocations exceeded");
    completeExceptionally(failure);
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

  void resetUserState() {
    retryRequested = false;
    completionRequested = false;
    result = null;
    failure = null;
  }
}