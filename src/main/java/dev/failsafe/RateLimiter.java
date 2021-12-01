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

/**
 * A rate limiter allows you to control the rate of executions as a way of preventing system overload.
 * <p>
 * There are two types of rate limiting: <i>smooth</i> and <i>bursty</i>. <i>Smooth</i> rate limiting will evenly spread
 * out execution requests over-time, effectively smoothing out uneven execution request rates. <i>Bursty</i> rate
 * limiting allows potential bursts of executions to occur, up to a configured max per time period.</p>
 * <p>Rate limiting is based on permits, which can be requested in order to perform rate limited execution.
 * Permits are automatically refreshed over time based on the rate limiter's configuration.</p>
 * <p>
 * This class provides methods that block while waiting for permits to become available, and also methods that return
 * immediately. The blocking methods include:
 * <ul>
 *   <li>{@link #acquirePermit()}</li>
 *   <li>{@link #acquirePermits(int)}</li>
 *   <li>{@link #acquirePermit(Duration)}</li>
 *   <li>{@link #acquirePermits(int, Duration)}</li>
 *   <li>{@link #tryAcquirePermit(Duration)}</li>
 *   <li>{@link #tryAcquirePermits(int, Duration)}</li>
 * </ul>
 * The methods that return immediately include:
 * <ul>
 *   <li>{@link #tryAcquirePermit()}</li>
 *   <li>{@link #tryAcquirePermits(int)}</li>
 * </ul>
 * </p>
 * <p>
 * This class also provides methods that throw {@link RateLimitExceededException} when permits cannot be acquired, and
 * also methods that return a boolean. The {@code acquire} methods all throw {@link RateLimitExceededException} when
 * permits cannot be acquired, and the {@code tryAcquire} methods return a boolean.
 * </p>
 * <p>
 * This class is threadsafe.
 * </p>
 *
 * @param <R> result type
 * @author Jonathan Halterman
 * @see RateLimiterConfig
 * @see RateLimiterBuilder
 * @see RateLimitExceededException
 */
public interface RateLimiter<R> extends Policy<R> {
  /**
   * Returns a smooth {@link RateLimiterBuilder} for the {@code executionRate}, which controls how frequently an
   * execution is permitted. For example, a executionRate of {@code Duration.ofMillis(10)} would allow one execution
   * every 10 milliseconds.
   * <p>
   * Executions are permitted with a delay between executions equal to the {@code executionRate}. If too many executions
   * are being requested, the delay may be greater.
   *
   * @param executionRate at which executions should be permitted
   */
  static <R> RateLimiterBuilder<R> builder(Duration executionRate) {
    return new RateLimiterBuilder<>(executionRate);
  }

  /**
   * Returns a bursty {@link RateLimiterBuilder} for the {@code maxExecutions} per {@code period}. For example, a {@code
   * maxExecutions} value of {@code 100} with a {@code period} of {@code Duration.ofSeconds(1)} would allow up to 100
   * executions every 1 second.
   * <p>
   * Executions are performed with no delay up until the {@code maxExecutions} are reached for the current period, after
   * which executions are either rejected, or if a {@link RateLimiterBuilder#withTimeout(Duration) timeout is
   * configured}, they will block until a permit is free or the timeout is exceeded.
   *
   * @param maxExecutions The max number of permitted executions per {@code period}
   * @param period The period after which permitted executions are reset to the {@code maxExecutions}
   */
  static <R> RateLimiterBuilder<R> builder(long maxExecutions, Duration period) {
    return new RateLimiterBuilder<>(maxExecutions, period);
  }

  /**
   * Creates a new RateLimiterBuilder that will be based on the {@code config}.
   */
  static <R> RateLimiterBuilder<R> builder(RateLimiterConfig<R> config) {
    return new RateLimiterBuilder<>(config);
  }

  /**
   * Returns the {@link RateLimiterConfig} that the RateLimiter was built with.
   */
  @Override
  RateLimiterConfig<R> getConfig();

  /**
   * Attempts to acquire a permit to perform an execution against the rate limiter, waiting until one is available or
   * the thread is interrupted.
   *
   * @throws InterruptedException if the current thread is interrupted while waiting to acquire a permit
   * @see #tryAcquirePermit()
   */
  default void acquirePermit() throws InterruptedException {
    acquirePermits(1);
  }

  /**
   * Attempts to acquire the requested {@code permits} to perform executions against the rate limiter, waiting until
   * they are available or the thread is interrupted.
   *
   * @throws IllegalArgumentException if {@code permits} is < 1
   * @throws InterruptedException if the current thread is interrupted while waiting to acquire the {@code permits}
   * @see #tryAcquirePermits(int)
   */
  void acquirePermits(int permits) throws InterruptedException;

  /**
   * Attempts to acquire a permit to perform an execution against the rate limiter, waiting up to the {@code time
   * timeout} until one is available, else throwing {@link RateLimitExceededException} if a permit will not be available
   * in time.
   *
   * @throws NullPointerException if {@code timeout} is null
   * @throws RateLimitExceededException if the rate limiter cannot acquire a permit before the {@code timeout} sicne it
   * is exceeded
   * @throws InterruptedException if the current thread is interrupted while waiting to acquire a permit
   * @see #tryAcquirePermit(Duration)
   */
  default void acquirePermit(Duration timeout) throws InterruptedException {
    acquirePermits(1, timeout);
  }

  /**
   * Attempts to acquire the requested {@code permits} to perform executions against the rate limiter, waiting up to the
   * {@code time timeout} until they are available, else throwing {@link RateLimitExceededException} if the permits will
   * not be available in time.
   *
   * @throws IllegalArgumentException if {@code permits} is < 1
   * @throws NullPointerException if {@code timeout} is null
   * @throws RateLimitExceededException if the rate limiter cannot acquire the {@code permits} before the {@code
   * timeout} sicne it is exceeded
   * @throws InterruptedException if the current thread is interrupted while waiting to acquire the {@code permits}
   * @see #tryAcquirePermits(int, Duration)
   */
  default void acquirePermits(int permits, Duration timeout) throws InterruptedException {
    if (!tryAcquirePermits(permits, timeout))
      throw new RateLimitExceededException(this);
  }

  /**
   * Returns whether the rate limiter is smooth.
   *
   * @see #builder(Duration)
   */
  default boolean isSmooth() {
    return getConfig().getExecutionRate() != null;
  }

  /**
   * Returns whether the rate limiter is bursty.
   *
   * @see #builder(long, Duration)
   */
  default boolean isBursty() {
    return getConfig().getPeriod() != null;
  }

  /**
   * Tries to acquire a permit to perform an execution against the rate limiter, returning immediately without waiting.
   *
   * @return whether the requested {@code permits} are successfully acquired or not
   */
  default boolean tryAcquirePermit() {
    return tryAcquirePermits(1);
  }

  /**
   * Tries to acquire the requested {@code permits} to perform executions against the rate limiter, returning
   * immediately without waiting.
   *
   * @return whether the requested {@code permits} are successfully acquired or not
   * @throws IllegalArgumentException if {@code permits} is < 1
   */
  boolean tryAcquirePermits(int permits);

  /**
   * Tries to acquire a permit to perform an execution against the rate limiter, waiting up to the {@code timeout} until
   * they are available.
   *
   * @return whether a permit is successfully acquired
   * @throws NullPointerException if {@code timeout} is null
   * @throws InterruptedException if the current thread is interrupted while waiting to acquire a permit
   */
  default boolean tryAcquirePermit(Duration timeout) throws InterruptedException {
    return tryAcquirePermits(1, timeout);
  }

  /**
   * Tries to acquire the requested {@code permits} to perform executions against the rate limiter, waiting up to the
   * {@code timeout} until they are available.
   *
   * @return whether the requested {@code permits} are successfully acquired or not
   * @throws IllegalArgumentException if {@code permits} is < 1
   * @throws NullPointerException if {@code timeout} is null
   * @throws InterruptedException if the current thread is interrupted while waiting to acquire the {@code permits}
   */
  boolean tryAcquirePermits(int permits, Duration timeout) throws InterruptedException;
}
