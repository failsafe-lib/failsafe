package net.jodah.recurrent;

/**
 * Retry statistics.
 * 
 * @author Jonathan Halterman
 */
public interface InvocationStats {
  /**
   * Gets the number of invocation attempts so far. Invocation attempts are recorded when {@code canRetry} is called or
   * when the invocation is completed successfully.
   */
  int getAttemptCount();

  /**
   * Returns the elapsed time in milliseconds.
   */
  long getElapsedMillis();

  /**
   * Returns the elapsed time in nanoseconds.
   */
  long getElapsedNanos();

  /**
   * Returns the start time in milliseconds.
   */
  long getStartMillis();

  /**
   * Returns the start time in nanoseconds.
   */
  long getStartNanos();
}
