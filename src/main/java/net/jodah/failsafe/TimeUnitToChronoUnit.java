package net.jodah.failsafe;

import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

public class TimeUnitToChronoUnit {
  /**
   * Converts this {@code TimeUnit} to the equivalent {@code ChronoUnit}.
   *
   * @return the converted equivalent ChronoUnit
   * @since 9
   */
  /* Backwards compatability for 1.x -> 2.x migration */
  @Deprecated
  public static ChronoUnit toChronoUnit(TimeUnit timeUnit) {
    switch (timeUnit) {
      case NANOSECONDS:  return ChronoUnit.NANOS;
      case MICROSECONDS: return ChronoUnit.MICROS;
      case MILLISECONDS: return ChronoUnit.MILLIS;
      case SECONDS:      return ChronoUnit.SECONDS;
      case MINUTES:      return ChronoUnit.MINUTES;
      case HOURS:        return ChronoUnit.HOURS;
      case DAYS:         return ChronoUnit.DAYS;
      default: throw new AssertionError();
    }
  }
}
