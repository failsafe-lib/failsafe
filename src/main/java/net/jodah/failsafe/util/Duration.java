package net.jodah.failsafe.util;

import java.util.concurrent.TimeUnit;

/**
 * Duration, consisting of length of a time unit.
 */
public class Duration {
  public static final Duration NONE = new Duration(0, TimeUnit.MILLISECONDS);

  private final long length;
  private final TimeUnit timeUnit;

  public Duration(long length, TimeUnit timeUnit) {
    this.length = length;
    this.timeUnit = timeUnit;
  }

  /**
   * Returns the Duration in nanoseconds.
   */
  public long toNanos() {
    return timeUnit.toNanos(length);
  }

  /**
   * Returns the Duration in milliseconds.
   */
  public long toMillis() {
    return timeUnit.toMillis(length);
  }

  /**
   * Returns the Duration in seconds.
   */
  public long toSeconds() {
    return timeUnit.toSeconds(length);
  }

  /**
   * Returns the Duration in minutes.
   */
  public long toMinutes() {
    return timeUnit.toMinutes(length);
  }

  /**
   * Returns the Duration in hours.
   */
  public long toHours() {
    return timeUnit.toHours(length);
  }

  /**
   * Returns the Duration in days.
   */
  public long toDays() {
    return timeUnit.toDays(length);
  }
}
