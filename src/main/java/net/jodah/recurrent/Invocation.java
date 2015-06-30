package net.jodah.recurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import net.jodah.recurrent.util.Duration;

/**
 * A retryable invocation.
 * 
 * @author Jonathan Halterman
 * @param <T> result type
 */
class Invocation {
  private RetryPolicy retryPolicy;
  private Callable<?> callable;
  private ScheduledExecutorService executor;
  private long startTime;

  /** count of failed attempts */
  int attemptCount;
  /** wait time in nanoseconds */
  long waitTime;

  Invocation(Callable<?> callable, RetryPolicy retryPolicy, ScheduledExecutorService executor) {
    this.callable = callable;
    this.retryPolicy = retryPolicy;
    this.executor = executor;

    waitTime = retryPolicy.getDelay().toNanos();
    startTime = System.nanoTime();
  }

  /**
   * Retries a failed invocation, returning true if the invocation's retry policy has not been exceeded, else false.
   */
  public boolean retry() {
    recordFailedAttempt();
    if (!isPolicyExceeded()) {
      executor.schedule(callable, waitTime, TimeUnit.NANOSECONDS);
      return true;
    }
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
    boolean withinMaxRetries = retryPolicy.getMaxRetries() == -1 || attemptCount <= retryPolicy.getMaxRetries();
    boolean withinMaxDuration = retryPolicy.getMaxDuration() == null
        || System.nanoTime() - startTime < retryPolicy.getMaxDuration().toNanos();
    return !withinMaxRetries || !withinMaxDuration;
  }

  /**
   * Records a failed attempt and adjusts the wait time.
   */
  void recordFailedAttempt() {
    attemptCount++;

    // Adjust wait time for backoff
    if (retryPolicy.getMaxDelay() != null)
      waitTime = (long) Math.min(waitTime * retryPolicy.getDelayMultiplier(), retryPolicy.getMaxDelay().toNanos());

    // Adjust wait time for max duration
    if (retryPolicy.getMaxDuration() != null) {
      long elapsedNanos = System.nanoTime() - startTime;
      long maxRemainingWaitTime = retryPolicy.getMaxDuration().toNanos() - elapsedNanos;
      waitTime = Math.min(waitTime, maxRemainingWaitTime < 0 ? 0 : maxRemainingWaitTime);
    }
  }
}