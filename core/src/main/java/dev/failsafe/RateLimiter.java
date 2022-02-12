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
 * </p>
 * <p>
 * The methods that return immediately include:
 * <ul>
 *   <li>{@link #tryAcquirePermit()}</li>
 *   <li>{@link #tryAcquirePermits(int)}</li>
 *   <li>{@link #reservePermit()}</li>
 *   <li>{@link #reservePermits(int)}</li>
 *   <li>{@link #tryReservePermit(Duration)}</li>
 *   <li>{@link #tryReservePermits(int, Duration)}</li>
 * </ul>
 * </p>
 * <p>
 * This class also provides methods that throw {@link RateLimitExceededException} when permits cannot be acquired, and
 * also methods that return a boolean. The {@code acquire} methods all throw {@link RateLimitExceededException} when
 * permits cannot be acquired, and the {@code tryAcquire} methods return a boolean.
 * </p>
 * <p>
 * The {@code reserve} methods attempt to reserve permits and return an expected wait time before the permit can be
 * used. This helps integrate with scenarios where you need to wait externally
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
   * Returns a smooth {@link RateLimiterBuilder} for the {@code maxExecutions} and {@code period}, which control how
   * frequently an execution is permitted. The individual execution rate is computed as {@code period / maxExecutions}.
   * For example, with {@code maxExecutions} of {@code 100} and a {@code period} of {@code 1000 millis}, individual
   * executions will be permitted at a max rate of one every 10 millis.
   * <p>By default, the returned {@link RateLimiterBuilder} will have a {@link RateLimiterBuilder#withMaxWaitTime max
   * wait time} of {@code 0}.
   * <p>
   * Executions are performed with no delay until they exceed the max rate, after which executions are either rejected
   * or will block and wait until the {@link RateLimiterBuilder#withMaxWaitTime(Duration) max wait time} is exceeded.
   *
   * @param maxExecutions The max number of permitted executions per {@code period}
   * @param period The period after which permitted executions are reset to the {@code maxExecutions}
   */
  static <R> RateLimiterBuilder<R> smoothBuilder(long maxExecutions, Duration period) {
    return new RateLimiterBuilder<>(period.dividedBy(maxExecutions));
  }

  /**
   * Returns a smooth {@link RateLimiterBuilder} for the {@code maxRate}, which controls how frequently an execution is
   * permitted. For example, a {@code maxRate} of {@code Duration.ofMillis(10)} would allow up to one execution every 10
   * milliseconds.
   * <p>By default, the returned {@link RateLimiterBuilder} will have a {@link RateLimiterBuilder#withMaxWaitTime max
   * wait time} of {@code 0}.
   * <p>
   * Executions are performed with no delay until they exceed the {@code maxRate}, after which executions are either
   * rejected or will block and wait until the {@link RateLimiterBuilder#withMaxWaitTime(Duration) max wait time} is
   * exceeded.
   *
   * @param maxRate at which individual executions should be permitted
   */
  static <R> RateLimiterBuilder<R> smoothBuilder(Duration maxRate) {
    return new RateLimiterBuilder<>(maxRate);
  }

  /**
   * Returns a bursty {@link RateLimiterBuilder} for the {@code maxExecutions} per {@code period}. For example, a {@code
   * maxExecutions} value of {@code 100} with a {@code period} of {@code Duration.ofSeconds(1)} would allow up to 100
   * executions every 1 second.
   * <p>By default, the returned {@link RateLimiterBuilder} will have a {@link RateLimiterBuilder#withMaxWaitTime max
   * wait time} of {@code 0}.
   * <p>
   * Executions are performed with no delay up until the {@code maxExecutions} are reached for the current {@code
   * period}, after which executions are either rejected or will block and wait until the {@link
   * RateLimiterBuilder#withMaxWaitTime(Duration) max wait time} is exceeded.
   *
   * @param maxExecutions The max number of permitted executions per {@code period}
   * @param period The period after which permitted executions are reset to the {@code maxExecutions}
   */
  static <R> RateLimiterBuilder<R> burstyBuilder(long maxExecutions, Duration period) {
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
   * @see #reservePermit()
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
   * @see #reservePermits(int)
   */
  void acquirePermits(int permits) throws InterruptedException;

  /**
   * Attempts to acquire a permit to perform an execution against the rate limiter, waiting up to the {@code
   * maxWaitTime} until one is available, else throwing {@link RateLimitExceededException} if a permit will not be
   * available in time.
   *
   * @throws NullPointerException if {@code maxWaitTime} is null
   * @throws RateLimitExceededException if the rate limiter cannot acquire a permit within the {@code maxWaitTime}
   * @throws InterruptedException if the current thread is interrupted while waiting to acquire a permit
   * @see #tryAcquirePermit(Duration)
   */
  default void acquirePermit(Duration maxWaitTime) throws InterruptedException {
    acquirePermits(1, maxWaitTime);
  }

  /**
   * Attempts to acquire the requested {@code permits} to perform executions against the rate limiter, waiting up to the
   * {@code maxWaitTime} until they are available, else throwing {@link RateLimitExceededException} if the permits will
   * not be available in time.
   *
   * @throws IllegalArgumentException if {@code permits} is < 1
   * @throws NullPointerException if {@code maxWaitTime} is null
   * @throws RateLimitExceededException if the rate limiter cannot acquire a permit within the {@code maxWaitTime}
   * @throws InterruptedException if the current thread is interrupted while waiting to acquire the {@code permits}
   * @see #tryAcquirePermits(int, Duration)
   */
  default void acquirePermits(int permits, Duration maxWaitTime) throws InterruptedException {
    if (!tryAcquirePermits(permits, maxWaitTime))
      throw new RateLimitExceededException(this);
  }

  /**
   * Returns whether the rate limiter is smooth.
   *
   * @see #smoothBuilder(long, Duration)
   * @see #smoothBuilder(Duration)
   */
  default boolean isSmooth() {
    return getConfig().getMaxRate() != null;
  }

  /**
   * Returns whether the rate limiter is bursty.
   *
   * @see #burstyBuilder(long, Duration)
   */
  default boolean isBursty() {
    return getConfig().getPeriod() != null;
  }

  /**
   * Reserves a permit to perform an execution against the rate limiter, and returns the time that the caller is
   * expected to wait before acting on the permit. Returns {@code 0} if the permit is immediately available and no
   * waiting is needed.
   *
   * @see #acquirePermit()
   * @see #tryAcquirePermit()
   */
  default Duration reservePermit() {
    return reservePermits(1);
  }

  /**
   * Reserves the {@code permits} to perform executions against the rate limiter, and returns the time that the caller
   * is expected to wait before acting on the permits. Returns {@code 0} if the permits are immediately available and no
   * waiting is needed.
   *
   * @throws IllegalArgumentException if {@code permits} is < 1
   * @see #acquirePermits(int)
   * @see #tryAcquirePermits(int)
   */
  Duration reservePermits(int permits);

  /**
   * Tries to reserve a permit to perform an execution against the rate limiter, and returns the time that the caller is
   * expected to wait before acting on the permit, as long as it's less than the {@code maxWaitTime}.
   * <ul>
   *   <li>Returns the expected wait time for the permit if it was successfully reserved.</li>
   *   <li>Returns {@code 0} if the permit was successfully reserved and no waiting is needed.</li>
   *   <li>Returns {@code -1 nanoseconds} if the permit was not reserved because the wait time would be greater than the {@code maxWaitTime}.</li>
   * </ul>
   *
   * @throws NullPointerException if {@code maxWaitTime} is null
   * @see #acquirePermit(Duration)
   * @see #tryAcquirePermit(Duration)
   */
  default Duration tryReservePermit(Duration maxWaitTime) {
    return tryReservePermits(1, maxWaitTime);
  }

  /**
   * Tries to reserve the {@code permits} to perform executions against the rate limiter, and returns the time that the
   * caller is expected to wait before acting on the permits, as long as it's less than the {@code maxWaitTime}.
   * <ul>
   *   <li>Returns the expected wait time for the permits if they were successfully reserved.</li>
   *   <li>Returns {@code 0} if the permits were successfully reserved and no waiting is needed.</li>
   *   <li>Returns {@code -1 nanoseconds} if the permits were not reserved because the wait time would be greater than the {@code maxWaitTime}.</li>
   * </ul>
   *
   * @throws IllegalArgumentException if {@code permits} is < 1
   * @throws NullPointerException if {@code maxWaitTime} is null
   * @see #acquirePermit(Duration)
   * @see #tryAcquirePermit(Duration)
   */
  Duration tryReservePermits(int permits, Duration maxWaitTime);

  /**
   * Tries to acquire a permit to perform an execution against the rate limiter, returning immediately without waiting.
   *
   * @return whether the requested {@code permits} are successfully acquired or not
   * @see #acquirePermit()
   * @see #reservePermits(int)
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
   * @see #acquirePermits(int)
   */
  boolean tryAcquirePermits(int permits);

  /**
   * Tries to acquire a permit to perform an execution against the rate limiter, waiting up to the {@code maxWaitTime}
   * until they are available.
   *
   * @return whether a permit is successfully acquired
   * @throws NullPointerException if {@code maxWaitTime} is null
   * @throws InterruptedException if the current thread is interrupted while waiting to acquire a permit
   * @see #acquirePermit(Duration)
   */
  default boolean tryAcquirePermit(Duration maxWaitTime) throws InterruptedException {
    return tryAcquirePermits(1, maxWaitTime);
  }

  /**
   * Tries to acquire the requested {@code permits} to perform executions against the rate limiter, waiting up to the
   * {@code maxWaitTime} until they are available.
   *
   * @return whether the requested {@code permits} are successfully acquired or not
   * @throws IllegalArgumentException if {@code permits} is < 1
   * @throws NullPointerException if {@code maxWaitTime} is null
   * @throws InterruptedException if the current thread is interrupted while waiting to acquire the {@code permits}
   * @see #acquirePermits(int, Duration)
   */
  boolean tryAcquirePermits(int permits, Duration maxWaitTime) throws InterruptedException;
}
