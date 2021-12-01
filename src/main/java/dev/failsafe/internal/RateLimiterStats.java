/*
 * Copyright 2021 the original author or authors.
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
package dev.failsafe.internal;

import java.time.Duration;

abstract class RateLimiterStats {
  final Stopwatch stopwatch;

  RateLimiterStats(Stopwatch stopwatch) {
    this.stopwatch = stopwatch;
  }

  static class Stopwatch {
    private long startTime = System.nanoTime();

    long elapsedNanos() {
      return System.nanoTime() - startTime;
    }

    void reset() {
      startTime = System.nanoTime();
    }
  }

  /**
   * Eagerly acquires permits and returns the time in nanos that must be waited in order to use the permits, else
   * returns {@code -1} if the wait time would exceed the {@code timeout}.
   *
   * @param permits the number of requested permits
   * @param timeout the max time to wait for the requested permits, else {@code null} to wait indefinitely
   */
  abstract long acquirePermits(long permits, Duration timeout);

  /**
   * Returns whether the {@code waitNanos} would exceed the {@code timeout}, else {@code false} if {@code timeout} is
   * null.
   */
  boolean exceedsTimeout(long waitNanos, Duration timeout) {
    return timeout != null && waitNanos > timeout.toNanos();
  }

  /**
   * Returns the elapsed time since the rate limiter began.
   */
  Duration getElapsed() {
    return Duration.ofNanos(stopwatch.elapsedNanos());
  }

  /**
   * Resets the rate limiter's internal stats.
   */
  abstract void reset();
}