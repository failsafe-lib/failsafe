package net.jodah.recurrent.util;

import java.util.concurrent.TimeUnit;

/**
 * Duration unit, consisting of length and time unit.
 */
public class Duration {
  public final long length;
  public final TimeUnit timeUnit;
  
  public static final Duration NONE = new Duration(0, TimeUnit.MILLISECONDS);

  public Duration(long length, TimeUnit timeUnit) {
    this.length = length;
    this.timeUnit = timeUnit;
  }
  
  public long toNanos() {
    return timeUnit.toNanos(length);
  }
}
