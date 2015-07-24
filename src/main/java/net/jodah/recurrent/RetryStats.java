package net.jodah.recurrent;

/**
 * Statistics for the usage of a RetryPolicy.
 * 
 * @author Jonathan Halterman
 */
public class RetryStats {
  final RetryPolicy retryPolicy;
  private final long startTime;

  // Mutable state
  /** Count of retry attempts */
  volatile int retryCount;
  /** Wait time in nanoseconds */
  volatile long waitTime;

  public RetryStats(RetryPolicy retryPolicy) {
    this.retryPolicy = retryPolicy;
    waitTime = retryPolicy.getDelay().toNanos();
    startTime = System.nanoTime();
  }

  boolean canRetry() {
    return retryPolicy.allowsRetries() && canRetryInternal();
  }

  /**
   * Records a failed attempt, adjusts the wait time and returns whether a retry can be performed for the
   * {@code failure}.
   */
  public boolean canRetryOn(Throwable failure) {
    return retryPolicy.allowsRetriesFor(null, failure) && canRetryInternal();
  }

  /**
   * Records a failed attempt, adjusts the wait time and returns whether a retry can be performed for the {@code result}
   * .
   */
  public boolean canRetryWhen(Object result) {
    return retryPolicy.allowsRetriesFor(result, null) && canRetryInternal();
  }

  /**
   * Records a failed attempt, adjusts the wait time and returns whether a retry can be performed for the {@code result}
   * and {@code failure}.
   */
  public boolean canRetryWhen(Object result, Throwable failure) {
    return retryPolicy.allowsRetriesFor(result, failure) && canRetryInternal();
  }

  private boolean canRetryInternal() {
    retryCount++;
    adjustForBackoffs();
    adjustForMaxDuration();
    return !isPolicyExceeded();
  }

  /**
   * Gets the number of retries that have been attempted so far.
   */
  public int getRetryCount() {
    return retryCount;
  }

  /**
   * Returns the wait time in nanoseconds.
   */
  public long getWaitTime() {
    return waitTime;
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

  /**
   * Returns true if the max retries or max duration for the retry policy have been exceeded, else false.
   */
  boolean isPolicyExceeded() {
    boolean withinMaxRetries = retryPolicy.getMaxRetries() == -1 || retryCount <= retryPolicy.getMaxRetries();
    boolean withinMaxDuration = retryPolicy.getMaxDuration() == null
        || System.nanoTime() - startTime < retryPolicy.getMaxDuration().toNanos();
    return !withinMaxRetries || !withinMaxDuration;
  }
}