package net.jodah.recurrent;

import java.util.concurrent.TimeUnit;

/**
 * Execution statistics.
 * 
 * @author Jonathan Halterman
 */
public class ExecutionStats {
  final long startTime;
  /** Number of attempts */
  volatile int attempts;

  ExecutionStats(long startTime) {
    this.startTime = startTime;
  }

  ExecutionStats(ExecutionStats stats) {
    this.startTime = stats.startTime;
    this.attempts = stats.attempts;
  }

  /**
   * Gets the number of execution attempts so far. Execution attempts are recorded when {@code canRetry} is called or
   * when the execution is completed successfully.
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

  ExecutionStats copy() {
    return new ExecutionStats(this);
  }
}
