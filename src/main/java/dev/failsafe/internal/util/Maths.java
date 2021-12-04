package dev.failsafe.internal.util;

/**
 * Misc math utilities.
 */
public final class Maths {
  private Maths() {
  }

  /**
   * Returns the sum of {@code a} and {@code b} else {@code Long.MAX_VALUE} if the sum would otherwise overflow.
   */
  public static long add(long a, long b) {
    long naiveSum = a + b;
    return (a ^ b) < 0L | (a ^ naiveSum) >= 0L ? naiveSum : 9223372036854775807L + (naiveSum >>> 63 ^ 1L);
  }

  /**
   * Returns the {@code input} rounded down to the nearest {@code interval}.
   */
  public static long roundDown(long input, long interval) {
    return (input / interval) * interval;
  }
}
