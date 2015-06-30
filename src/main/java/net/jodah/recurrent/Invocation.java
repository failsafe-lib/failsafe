package net.jodah.recurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import net.jodah.recurrent.util.Duration;

/**
 * A retryable invocation.
 * 
 * @author Jonathan Halterman
 * @param <T> invocation result type
 */
public class Invocation {
  private RetryPolicy retryPolicy;
  private Callable<?> callable;
  private Scheduler scheduler;
  private long startTime;

  /** count of failed attempts */
  int attemptCount;
  /** wait time in nanoseconds */
  long waitTime;

  Invocation(Callable<?> callable, RetryPolicy retryPolicy, Scheduler scheduler) {
    initialize(callable, retryPolicy, scheduler);
  }

  void initialize(Callable<?> callable, RetryPolicy retryPolicy, Scheduler scheduler) {
    this.callable = callable;
    this.retryPolicy = retryPolicy;
    this.scheduler = scheduler;

    waitTime = retryPolicy.getDelay().toNanos();
    startTime = System.nanoTime();
  }

  /**
   * Retries a failed invocation, returning true if the invocation's retry policy has not been exceeded, else false.
   */
  public boolean retry() {
    recordFailedAttempt();
    if (!isPolicyExceeded()) {
      scheduler.schedule(callable, waitTime, TimeUnit.NANOSECONDS);
      return true;
    }
    return false;
  }

  /**
   * Records a failed attempt, incrementing the attempt count and time.
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

  /**
   * Returns the wait time.
   */
  Duration getWaitTime() {
    return new Duration(waitTime, TimeUnit.NANOSECONDS);
  }

  /**
   * Returns true if the max retries or max duration for the retry policy have been exceeded, else false.
   */
  public boolean isPolicyExceeded() {
    boolean withinMaxRetries = retryPolicy.getMaxRetries() == -1 || attemptCount <= retryPolicy.getMaxRetries();
    boolean withinMaxDuration = retryPolicy.getMaxDuration() == null
        || System.nanoTime() - startTime < retryPolicy.getMaxDuration().toNanos();
    return !withinMaxRetries || !withinMaxDuration;
  }
}