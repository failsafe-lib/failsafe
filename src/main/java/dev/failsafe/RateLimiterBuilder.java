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

import dev.failsafe.internal.RateLimiterImpl;

import java.time.Duration;

/**
 * Builds {@link RateLimiter} instances.
 * <p>
 * This class is <i>not</i> threadsafe.
 * </p>
 *
 * @param <R> result type
 * @author Jonathan Halterman
 * @see RateLimiterConfig
 * @see RateLimitExceededException
 */
public class RateLimiterBuilder<R> extends PolicyBuilder<RateLimiterBuilder<R>, RateLimiterConfig<R>, R> {
  RateLimiterBuilder(Duration executionRate) {
    super(new RateLimiterConfig<>(executionRate));
  }

  RateLimiterBuilder(long maxPermits, Duration period) {
    super(new RateLimiterConfig<>(maxPermits, period));
  }

  RateLimiterBuilder(RateLimiterConfig<R> config) {
    super(new RateLimiterConfig<>(config));
  }

  /**
   * Builds a new {@link RateLimiter} using the builder's configuration.
   */
  public RateLimiter<R> build() {
    return new RateLimiterImpl<>(new RateLimiterConfig<>(config));
  }

  /**
   * Configures the {@code timeout} to wait for permits to be available. If permits cannot be acquired before the {@code
   * timeout} is exceeded, then the rate limiter will throw {@link RateLimitExceededException}.
   */
  public RateLimiterBuilder<R> withTimeout(Duration timeout) {
    config.timeout = timeout;
    return this;
  }
}
