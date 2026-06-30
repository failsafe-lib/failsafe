/*
 * Copyright 2018 the original author or authors.
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

import dev.failsafe.ExecutionContext;
import dev.failsafe.RetryPolicyConfig;

import java.time.Duration;

import static dev.failsafe.internal.util.RandomDelay.randomDelay;
import static dev.failsafe.internal.util.RandomDelay.randomDelayInRange;

/**
 * Computes the delay, in nanoseconds, to use before the next retry attempt.
 * <p>
 * Extracted from {@link RetryPolicyExecutor#onFailure}, which previously interleaved this delay-math with
 * retry-exhaustion checks and event-listener dispatch in a single method, and held {@code lastDelayNanos} as
 * executor-wide mutable state even though only the delay computation ever read or wrote it.
 * <p>
 * The computation is a fixed sequence of independent adjustment steps, applied in order: a user-supplied delay
 * function takes precedence if configured; otherwise a fixed-or-random base delay is computed, then adjusted for
 * backoff, then jitter, then clamped to any configured max duration. Structuring it this way (each step consuming
 * the previous step's output, free to leave it unchanged) gives the pipeline its own seam to test and extend
 * independently of retry-decision logic, in the spirit of Chain of Responsibility.
 *
 * @param <R> result type
 */
class RetryDelayCalculator<R> {
  private final RetryPolicyConfig<R> config;

  /** The last fixed, backoff, random or computed delay time in nanoseconds. */
  private volatile long lastDelayNanos;

  RetryDelayCalculator(RetryPolicyConfig<R> config) {
    this.config = config;
  }

  /**
   * Computes the delay to use before the next retry attempt.
   *
   * @param userComputedDelay the result of any user-supplied delay function, or {@code null} if none is configured
   *   or it did not apply for this result/exception. Takes precedence over the fixed/random/backoff steps below.
   * @param context the current execution context, used for attempt-count-based backoff
   * @param elapsedNanos elapsed execution time, used to clamp the result to any configured max duration
   */
  long computeDelayNanos(Duration userComputedDelay, ExecutionContext<R> context, long elapsedNanos) {
    long delayNanos;

    if (userComputedDelay != null) {
      delayNanos = userComputedDelay.toNanos();
    } else {
      delayNanos = getFixedOrRandomDelayNanos(lastDelayNanos);
      delayNanos = adjustForBackoff(context, delayNanos);
      lastDelayNanos = delayNanos;
    }

    if (delayNanos != 0)
      delayNanos = adjustForJitter(delayNanos);
    return adjustForMaxDuration(delayNanos, elapsedNanos);
  }

  private long getFixedOrRandomDelayNanos(long delayNanos) {
    Duration delay = config.getDelay();
    Duration delayMin = config.getDelayMin();
    Duration delayMax = config.getDelayMax();

    if (delayNanos == 0 && delay != null && !delay.equals(Duration.ZERO))
      delayNanos = delay.toNanos();
    else if (delayMin != null && delayMax != null)
      delayNanos = randomDelayInRange(delayMin.toNanos(), delayMax.toNanos(), Math.random());
    return delayNanos;
  }

  private long adjustForBackoff(ExecutionContext<R> context, long delayNanos) {
    if (context.getAttemptCount() != 1 && config.getMaxDelay() != null)
      delayNanos = (long) Math.min(delayNanos * config.getDelayFactor(), config.getMaxDelay().toNanos());
    return delayNanos;
  }

  private long adjustForJitter(long delayNanos) {
    if (config.getJitter() != null)
      delayNanos = randomDelay(delayNanos, config.getJitter().toNanos(), Math.random());
    else if (config.getJitterFactor() > 0.0)
      delayNanos = randomDelay(delayNanos, config.getJitterFactor(), Math.random());
    return delayNanos;
  }

  private long adjustForMaxDuration(long delayNanos, long elapsedNanos) {
    if (config.getMaxDuration() != null) {
      long maxRemainingDelay = config.getMaxDuration().toNanos() - elapsedNanos;
      delayNanos = Math.min(delayNanos, maxRemainingDelay < 0 ? 0 : maxRemainingDelay);
      if (delayNanos < 0)
        delayNanos = 0;
    }
    return delayNanos;
  }
}