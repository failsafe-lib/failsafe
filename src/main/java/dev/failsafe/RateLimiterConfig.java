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
package dev.failsafe;

import java.time.Duration;

public class RateLimiterConfig<R> extends PolicyConfig<R> {
  // Smoothing
  Duration executionRate;

  // Bursting
  long maxPermits;
  Duration period;

  // Common
  Duration timeout;

  RateLimiterConfig(Duration executionRate) {
    this.executionRate = executionRate;
  }

  RateLimiterConfig(long maxPermits, Duration period) {
    this.maxPermits = maxPermits;
    this.period = period;
  }

  RateLimiterConfig(RateLimiterConfig<R> config) {
    super(config);
    executionRate = config.executionRate;
    maxPermits = config.maxPermits;
    period = config.period;
    timeout = config.timeout;
  }

  /**
   * For smooth rate limiters, returns the rate at which executions are permitted, else {@code null} if the rate limiter
   * is not smooth.
   *
   * @see RateLimiter#builder(Duration)
   */
  public Duration getExecutionRate() {
    return executionRate;
  }

  /**
   * For bursty rate limiters, returns the max permitted executions per {@link #getPeriod() period}, else {@code null}
   * if the rate limiter is not bursty.
   *
   * @see RateLimiter#builder(long, Duration)
   */
  public long getMaxPermits() {
    return maxPermits;
  }

  /**
   * For bursty rate limiters, returns the period after which permits are reset to {@link #getMaxPermits() maxPermits},
   * else {@code null} if the rate limiter is not bursty.
   *
   * @see RateLimiter#builder(long, Duration)
   */
  public Duration getPeriod() {
    return period;
  }

  /**
   * Returns the timeout to wait for permits to be available. If permits cannot be acquired before the timeout is
   * exceeded, then the rate limiter will throw {@link RateLimitExceededException}.
   *
   * @see RateLimiterBuilder#withTimeout(Duration)
   */
  public Duration getTimeout() {
    return timeout;
  }
}
