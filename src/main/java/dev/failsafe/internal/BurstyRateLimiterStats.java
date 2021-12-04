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

import dev.failsafe.RateLimiterConfig;

import java.time.Duration;

/**
 * A rate limiter implementation that allows bursts of executions, up to the max permits per period. This implementation
 * tracks the current period and available permits, which can go into a deficit. A deficit of available permits will
 * cause wait times for callers that can be several periods long, depending on the size of the deficit and the number of
 * requested permits.
 */
class BurstyRateLimiterStats extends RateLimiterStats {
  /* The permits per period */
  final long periodPermits;
  /* The nanos per period */
  private final long periodNanos;

  /* Available permits. Can be negative during a deficit. */
  private long availablePermits;
  private long currentPeriod;

  BurstyRateLimiterStats(RateLimiterConfig<?> config, Stopwatch stopwatch) {
    super(stopwatch);
    periodPermits = config.getMaxPermits();
    periodNanos = config.getPeriod().toNanos();
    availablePermits = periodPermits;
  }

  @Override
  public synchronized long acquirePermits(long requestedPermits, Duration maxWaitTime) {
    long currentNanos = stopwatch.elapsedNanos();
    long newCurrentPeriod = currentNanos / periodNanos;

    // Update current period and available permits
    if (currentPeriod < newCurrentPeriod) {
      long elapsedPeriods = newCurrentPeriod - currentPeriod;
      long elapsedPermits = elapsedPeriods * periodPermits;
      currentPeriod = newCurrentPeriod;
      availablePermits = availablePermits < 0 ? availablePermits + elapsedPermits : periodPermits;
    }

    long waitNanos = 0;
    if (requestedPermits > availablePermits) {
      long nextPeriodNanos = (currentPeriod + 1) * periodNanos;
      long nanosToNextPeriod = nextPeriodNanos - currentNanos;
      long permitDeficit = requestedPermits - availablePermits;
      long additionalPeriods = permitDeficit / periodPermits;
      long additionalUnits = permitDeficit % periodPermits;

      // Do not wait for an additional period if we're not using any permits from it
      if (additionalUnits == 0)
        additionalPeriods -= 1;

      // The nanos to wait until the beginning of the next period that will have free permits
      waitNanos = nanosToNextPeriod + (additionalPeriods * periodNanos);

      if (exceedsMaxWaitTime(waitNanos, maxWaitTime))
        return -1;
    }

    availablePermits -= requestedPermits;
    return waitNanos;
  }

  synchronized long getAvailablePermits() {
    return availablePermits;
  }

  synchronized long getCurrentPeriod() {
    return currentPeriod;
  }

  @Override
  synchronized void reset() {
    stopwatch.reset();
    availablePermits = periodPermits;
    currentPeriod = 0;
  }
}