package net.jodah.recurrent;

import java.util.concurrent.TimeUnit;

/**
 * Invocation statistics.
 * 
 * @author Jonathan Halterman
 */
public class InvocationStats {
  final long startTime;
  /** Number of attempts */
  volatile int attempts;

  InvocationStats(long startTime) {
    this.startTime = startTime;
  }

  InvocationStats(InvocationStats stats) {
    this.startTime = stats.startTime;
    this.attempts = stats.attempts;
  }

  /**
   * Gets the number of invocation attempts so far. Invocation attempts are recorded when {@code canRetry} is called or
   * when the invocation is completed successfully.
   */
  public int getAttemptCount() {
    return attempts;
  }

  /**
   * Returns the elapsed time in milliseconds.
   */
  public long getElapsedMillis() {
    return TimeUnit.NANOSECONDS.toMillis(getElapsedNanos());
  }

  /**
   * Returns the elapsed time in nanoseconds.
   */
  public long getElapsedNanos() {
    return System.nanoTime() - startTime;
  }

  /**
   * Returns the start time in milliseconds.
   */
  public long getStartMillis() {
    return TimeUnit.NANOSECONDS.toMillis(startTime);
  }

  /**
   * Returns the start time in nanoseconds.
   */
  public long getStartNanos() {
    return startTime;
  }

  InvocationStats copy() {
    return new InvocationStats(this);
  }
}
