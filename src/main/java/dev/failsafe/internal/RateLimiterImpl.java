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

import dev.failsafe.RateLimiter;
import dev.failsafe.RateLimiterConfig;
import dev.failsafe.internal.RateLimiterStats.Stopwatch;
import dev.failsafe.internal.util.Assert;
import dev.failsafe.internal.util.Durations;
import dev.failsafe.spi.PolicyExecutor;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * A RateLimiter implementation that supports smooth and bursty rate limiting.
 *
 * @param <R> result type
 */
public class RateLimiterImpl<R> implements RateLimiter<R> {
  private final RateLimiterConfig<R> config;
  private final RateLimiterStats stats;

  public RateLimiterImpl(RateLimiterConfig<R> config) {
    this(config, new Stopwatch());
  }

  RateLimiterImpl(RateLimiterConfig<R> config, Stopwatch stopwatch) {
    this.config = config;
    stats = config.getMaxRate() != null ?
      new SmoothRateLimiterStats(config, stopwatch) :
      new BurstyRateLimiterStats(config, stopwatch);
  }

  @Override
  public RateLimiterConfig<R> getConfig() {
    return config;
  }

  @Override
  public void acquirePermits(int permits) throws InterruptedException {
    long waitNanos = reservePermits(permits).toNanos();
    if (waitNanos > 0)
      TimeUnit.NANOSECONDS.sleep(waitNanos);
  }

  @Override
  public Duration reservePermits(int permits) {
    Assert.isTrue(permits > 0, "permits must be > 0");
    return Duration.ofNanos(stats.acquirePermits(permits, null));
  }

  @Override
  public boolean tryAcquirePermits(int permits) {
    return reservePermits(permits, Duration.ZERO) == 0;
  }

  @Override
  public boolean tryAcquirePermits(int permits, Duration maxWaitTime) throws InterruptedException {
    long waitNanos = reservePermits(permits, maxWaitTime);
    if (waitNanos == -1)
      return false;
    if (waitNanos > 0)
      TimeUnit.NANOSECONDS.sleep(waitNanos);
    return true;
  }

  @Override
  public Duration tryReservePermits(int permits, Duration maxWaitTime) {
    return Duration.ofNanos(reservePermits(permits, maxWaitTime));
  }

  @Override
  public PolicyExecutor<R> toExecutor(int policyIndex) {
    return new RateLimiterExecutor<>(this, policyIndex);
  }

  long reservePermits(int permits, Duration maxWaitTime) {
    Assert.isTrue(permits > 0, "permits must be > 0");
    Assert.notNull(maxWaitTime, "maxWaitTime");
    return stats.acquirePermits(permits, Durations.ofSafeNanos(maxWaitTime));
  }
}
