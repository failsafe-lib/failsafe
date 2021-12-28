/*
 * Copyright 2011-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
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
