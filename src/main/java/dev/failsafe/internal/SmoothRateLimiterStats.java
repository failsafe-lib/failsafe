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
import dev.failsafe.internal.util.Maths;

import java.time.Duration;

/**
 * A rate limiter implementation that evenly distributes permits over time, based on the max permits per period. This
 * implementation focuses on the interval between permits, and tracks the next interval in which a permit is free.
 */
class SmoothRateLimiterStats extends RateLimiterStats {
  /* The nanos per interval between permits */
  final long intervalNanos;

  // The amount of time, relative to the start time, that the next permit will be free.
  // Will be a multiple of intervalNanos.
  private long nextFreePermitNanos;

  SmoothRateLimiterStats(RateLimiterConfig<?> config, Stopwatch stopwatch) {
    super(stopwatch);
    intervalNanos = config.getExecutionRate().toNanos();
  }

  @Override
  public synchronized long acquirePermits(long requestedPermits, Duration timeout) {
    long currentNanos = stopwatch.elapsedNanos();
    long requestedPermitNanos = requestedPermits * intervalNanos;
    long waitNanos;
    long newNextFreePermitNanos;

    // If a permit is currently available
    if (currentNanos >= nextFreePermitNanos) {
      // Nanos at the start of the current interval
      long currentIntervalNanos = Maths.roundDown(currentNanos, intervalNanos);
      newNextFreePermitNanos = Maths.add(currentIntervalNanos, requestedPermitNanos);
    } else {
      newNextFreePermitNanos = Maths.add(nextFreePermitNanos, requestedPermitNanos);
    }

    waitNanos = Math.max(newNextFreePermitNanos - currentNanos - intervalNanos, 0);

    if (exceedsTimeout(waitNanos, timeout))
      return -1;

    nextFreePermitNanos = newNextFreePermitNanos;
    return waitNanos;
  }

  synchronized long getNextFreePermitNanos() {
    return nextFreePermitNanos;
  }

  @Override
  synchronized void reset() {
    stopwatch.reset();
    nextFreePermitNanos = 0;
  }
}