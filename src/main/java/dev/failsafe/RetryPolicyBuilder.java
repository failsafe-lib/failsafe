/*
 * Copyright 2016 the original author or authors.
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

import dev.failsafe.event.EventListener;
import dev.failsafe.event.ExecutionAttemptedEvent;
import dev.failsafe.event.ExecutionCompletedEvent;
import dev.failsafe.event.ExecutionScheduledEvent;
import dev.failsafe.internal.RetryPolicyImpl;
import dev.failsafe.internal.util.Assert;
import dev.failsafe.internal.util.Durations;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Builds {@link RetryPolicy instances}.
 * <ul>
 *   <li>By default, a RetryPolicy will retry up to {@code 2} times when any {@code Exception} is thrown, with no delay
 *   between retry attempts.</li>
 *   <li>You can change the default number of retry attempts and delay between retries by using the {@code with}
 *   configuration methods.</li>
 *   <li>By default, any exception is considered a failure and will be handled by the policy. You can override this by
 *   specifying your own {@code handle} conditions. The default exception handling condition will only be overridden by
 *   another condition that handles failure exceptions such as {@link #handle(Class)} or {@link #handleIf(BiPredicate)}.
 *   Specifying a condition that only handles results, such as {@link #handleResult(Object)} or
 *   {@link #handleResultIf(Predicate)} will not replace the default exception handling condition.</li>
 *   <li>If multiple {@code handle} conditions are specified, any condition that matches an execution result or failure
 *   will trigger policy handling.</li>
 *   <li>The {@code abortOn}, {@code abortWhen} and {@code abortIf} methods describe when retries should be aborted.</li>
 * </ul>
 * <p>
 * Note:
 * <ul>
 *   <li>This class extends {@link DelayablePolicyBuilder} and {@link FailurePolicyBuilder} which offer additional configuration.</li>
 *   <li>This class is <i>not</i> threadsafe.</li>
 * </ul>
 * </p>
 *
 * @param <R> result type
 * @author Jonathan Halterman
 * @see RetryPolicyConfig
 */
public class RetryPolicyBuilder<R> extends DelayablePolicyBuilder<RetryPolicyBuilder<R>, RetryPolicyConfig<R>, R>
  implements PolicyListeners<RetryPolicyBuilder<R>, R> {

  private static final int DEFAULT_MAX_RETRIES = 2;

  RetryPolicyBuilder() {
    super(new RetryPolicyConfig<>());
    config.delay = Duration.ZERO;
    config.maxRetries = DEFAULT_MAX_RETRIES;
    config.abortConditions = new ArrayList<>();
  }

  RetryPolicyBuilder(RetryPolicyConfig<R> config) {
    super(new RetryPolicyConfig<>(config));
  }

  /**
   * Builds a new {@link RetryPolicy} using the builder's configuration.
   */
  public RetryPolicy<R> build() {
    return new RetryPolicyImpl<>(new RetryPolicyConfig<>(config));
  }

  /**
   * Specifies that retries should be aborted if the {@code completionPredicate} matches the completion result.
   *
   * @throws NullPointerException if {@code completionPredicate} is null
   */
  @SuppressWarnings("unchecked")
  public RetryPolicyBuilder<R> abortIf(BiPredicate<R, ? extends Throwable> completionPredicate) {
    Assert.notNull(completionPredicate, "completionPredicate");
    config.abortConditions.add((BiPredicate<R, Throwable>) completionPredicate);
    return this;
  }

  /**
   * Specifies that retries should be aborted if the {@code resultPredicate} matches the result. Predicate is not
   * invoked when the operation fails.
   *
   * @throws NullPointerException if {@code resultPredicate} is null
   */
  public RetryPolicyBuilder<R> abortIf(Predicate<R> resultPredicate) {
    Assert.notNull(resultPredicate, "resultPredicate");
    config.abortConditions.add(resultPredicateFor(resultPredicate));
    return this;
  }

  /**
   * Specifies when retries should be aborted. Any failure that is assignable from the {@code failure} will be result in
   * retries being aborted.
   *
   * @throws NullPointerException if {@code failure} is null
   */
  public RetryPolicyBuilder<R> abortOn(Class<? extends Throwable> failure) {
    Assert.notNull(failure, "failure");
    return abortOn(Arrays.asList(failure));
  }

  /**
   * Specifies when retries should be aborted. Any failure that is assignable from the {@code failures} will be result
   * in retries being aborted.
   *
   * @throws NullPointerException if {@code failures} is null
   * @throws IllegalArgumentException if failures is empty
   */
  @SafeVarargs
  public final RetryPolicyBuilder<R> abortOn(Class<? extends Throwable>... failures) {
    Assert.notNull(failures, "failures");
    Assert.isTrue(failures.length > 0, "Failures cannot be empty");
    return abortOn(Arrays.asList(failures));
  }

  /**
   * Specifies when retries should be aborted. Any failure that is assignable from the {@code failures} will be result
   * in retries being aborted.
   *
   * @throws NullPointerException if {@code failures} is null
   * @throws IllegalArgumentException if failures is null or empty
   */
  public RetryPolicyBuilder<R> abortOn(List<Class<? extends Throwable>> failures) {
    Assert.notNull(failures, "failures");
    Assert.isTrue(!failures.isEmpty(), "failures cannot be empty");
    config.abortConditions.add(failurePredicateFor(failures));
    return this;
  }

  /**
   * Specifies that retries should be aborted if the {@code failurePredicate} matches the failure.
   *
   * @throws NullPointerException if {@code failurePredicate} is null
   */
  public RetryPolicyBuilder<R> abortOn(Predicate<? extends Throwable> failurePredicate) {
    Assert.notNull(failurePredicate, "failurePredicate");
    config.abortConditions.add(failurePredicateFor(failurePredicate));
    return this;
  }

  /**
   * Specifies that retries should be aborted if the execution result matches the {@code result}.
   */
  public RetryPolicyBuilder<R> abortWhen(R result) {
    config.abortConditions.add(resultPredicateFor(result));
    return this;
  }

  /**
   * Registers the {@code listener} to be called when an execution is aborted.
   * <p>Note: Any exceptions that are thrown from within the {@code listener} are ignored. To provide an alternative
   * result for a failed execution, use a {@link Fallback}.</p>
   *
   * @throws NullPointerException if {@code listener} is null
   */
  public RetryPolicyBuilder<R> onAbort(EventListener<ExecutionCompletedEvent<R>> listener) {
    config.abortListener = Assert.notNull(listener, "listener");
    return this;
  }

  /**
   * Registers the {@code listener} to be called when an execution attempt fails. You can also use {@link
   * #onFailure(EventListener) onFailure} to determine when the execution attempt fails <i>and</i> and all retries have
   * failed.
   * <p>Note: Any exceptions that are thrown from within the {@code listener} are ignored. To provide an alternative
   * result for a failed execution, use a {@link Fallback}.</p>
   *
   * @throws NullPointerException if {@code listener} is null
   */
  public RetryPolicyBuilder<R> onFailedAttempt(EventListener<ExecutionAttemptedEvent<R>> listener) {
    config.failedAttemptListener = Assert.notNull(listener, "listener");
    return this;
  }

  /**
   * Registers the {@code listener} to be called when an execution fails and the {@link
   * RetryPolicyConfig#getMaxRetries() max retry attempts} or {@link RetryPolicyConfig#getMaxDuration() max duration}
   * are exceeded.
   * <p>Note: Any exceptions that are thrown from within the {@code listener} are ignored. To provide an alternative
   * result for a failed execution, use a {@link Fallback}.</p>
   *
   * @throws NullPointerException if {@code listener} is null
   */
  public RetryPolicyBuilder<R> onRetriesExceeded(EventListener<ExecutionCompletedEvent<R>> listener) {
    config.retriesExceededListener = Assert.notNull(listener, "listener");
    return this;
  }

  /**
   * Registers the {@code listener} to be called when a retry is about to be attempted.
   * <p>Note: Any exceptions that are thrown from within the {@code listener} are ignored. To provide an alternative
   * result for a failed execution, use a {@link Fallback}.</p>
   *
   * @throws NullPointerException if {@code listener} is null
   * @see #onRetryScheduled(EventListener)
   */
  public RetryPolicyBuilder<R> onRetry(EventListener<ExecutionAttemptedEvent<R>> listener) {
    config.retryListener = Assert.notNull(listener, "listener");
    return this;
  }

  /**
   * Registers the {@code listener} to be called when a retry for an async call is about to be scheduled. This method
   * differs from {@link #onRetry(EventListener)} since it is called when a retry is initially scheduled but before any
   * configured delay, whereas {@link #onRetry(EventListener) onRetry} is called after a delay, just before the retry
   * attempt takes place.
   * <p>
   * <p>Note: Any exceptions that are thrown from within the {@code listener} are ignored. To provide an alternative
   * result for a failed execution, use a {@link Fallback}.</p>
   *
   * @throws NullPointerException if {@code listener} is null
   * @see #onRetry(EventListener)
   */
  public RetryPolicyBuilder<R> onRetryScheduled(EventListener<ExecutionScheduledEvent<R>> listener) {
    config.retryScheduledListener = Assert.notNull(listener, "listener");
    return this;
  }

  /**
   * Sets the {@code delay} between retries, exponentially backing off to the {@code maxDelay} and multiplying
   * consecutive delays by a factor of 2. Replaces any previously configured {@link #withDelay(Duration) fixed} or
   * {@link #withDelay(Duration, Duration) random} delays.
   *
   * @throws NullPointerException if {@code delay} or {@code maxDelay} are null
   * @throws IllegalArgumentException if {@code delay} is <= 0 or {@code delay} is >= {@code maxDelay}
   * @throws IllegalStateException if {@code delay} is >= the {@link #withMaxDuration(Duration) maxDuration} or {@code
   * delay} is < a configured {@link #withJitter(Duration) jitter duration}
   */
  public RetryPolicyBuilder<R> withBackoff(Duration delay, Duration maxDelay) {
    return withBackoff(delay, maxDelay, 2);
  }

  /**
   * Sets the {@code delay} between retries, exponentially backing off to the {@code maxDelay} and multiplying
   * consecutive delays by a factor of 2. Replaces any previously configured {@link #withDelay(Duration) fixed} or
   * {@link #withDelay(Duration, Duration) random} delays.
   *
   * @throws NullPointerException if {@code chronoUnit} is null
   * @throws IllegalArgumentException if {@code delay} is <= 0 or {@code delay} is >= {@code maxDelay}
   * @throws IllegalStateException if {@code delay} is >= the {@link #withMaxDuration(Duration) maxDuration} or {@code
   * delay} is < a configured {@link #withJitter(Duration) jitter duration}
   */
  public RetryPolicyBuilder<R> withBackoff(long delay, long maxDelay, ChronoUnit chronoUnit) {
    return withBackoff(delay, maxDelay, chronoUnit, 2);
  }

  /**
   * Sets the {@code delay} between retries, exponentially backing off to the {@code maxDelay} and multiplying
   * consecutive delays by the {@code delayFactor}. Replaces any previously configured {@link #withDelay(Duration)
   * fixed} or {@link #withDelay(Duration, Duration) random} delays.
   *
   * @throws NullPointerException if {@code chronoUnit} is null
   * @throws IllegalArgumentException if {@code delay} <= 0, {@code delay} is >= {@code maxDelay}, or the {@code
   * delayFactor} is <= 1
   * @throws IllegalStateException if {@code delay} is >= the {@link #withMaxDuration(Duration) maxDuration} or {@code
   * delay} is < a configured {@link #withJitter(Duration) jitter duration}
   */
  public RetryPolicyBuilder<R> withBackoff(long delay, long maxDelay, ChronoUnit chronoUnit, double delayFactor) {
    return withBackoff(Duration.of(delay, chronoUnit), Duration.of(maxDelay, chronoUnit), delayFactor);
  }

  /**
   * Sets the {@code delay} between retries, exponentially backing off to the {@code maxDelay} and multiplying
   * consecutive delays by the {@code delayFactor}. Replaces any previously configured {@link #withDelay(Duration)
   * fixed} or {@link #withDelay(Duration, Duration) random} delays.
   *
   * @throws NullPointerException if {@code delay} or {@code maxDelay} are null
   * @throws IllegalArgumentException if {@code delay} <= 0, {@code delay} is >= {@code maxDelay}, or the {@code
   * delayFactor} is <= 1
   * @throws IllegalStateException if {@code delay} is >= the {@link #withMaxDuration(Duration) maxDuration} or {@code
   * delay} is < a configured {@link #withJitter(Duration) jitter duration}
   */
  public RetryPolicyBuilder<R> withBackoff(Duration delay, Duration maxDelay, double delayFactor) {
    Assert.notNull(delay, "delay");
    Assert.notNull(maxDelay, "maxDelay");
    delay = Durations.ofSafeNanos(delay);
    maxDelay = Durations.ofSafeNanos(maxDelay);
    Assert.isTrue(!delay.isNegative() && !delay.isZero(), "The delay must be > 0");
    Assert.state(config.maxDuration == null || delay.toNanos() < config.maxDuration.toNanos(),
      "delay must be < the maxDuration");
    Assert.state(config.jitter == null || delay.toNanos() >= config.jitter.toNanos(),
      "delay must be >= the jitter duration");
    Assert.isTrue(delay.toNanos() < maxDelay.toNanos(), "delay must be < the maxDelay");
    Assert.isTrue(delayFactor > 1, "delayFactor must be > 1");
    config.delay = delay;
    config.maxDelay = maxDelay;
    config.delayFactor = delayFactor;

    // CLear random delay
    config.delayMin = null;
    config.delayMax = null;
    return this;
  }

  /**
   * Sets the {@code delay} to occur between retries. Replaces any previously configured {@link #withBackoff(Duration,
   * Duration) backoff} or {@link #withDelay(Duration, Duration) random} delays.
   *
   * @throws NullPointerException if {@code delay} is null
   * @throws IllegalArgumentException if {@code delay} <= 0
   * @throws IllegalStateException if {@code delay} is >= the {@link #withMaxDuration(Duration) maxDuration} or {@code
   * delay} is < a configured {@link #withJitter(Duration) jitter duration}
   */
  @Override
  public RetryPolicyBuilder<R> withDelay(Duration delay) {
    Assert.notNull(delay, "delay");
    delay = Durations.ofSafeNanos(delay);
    Assert.state(config.maxDuration == null || delay.toNanos() < config.maxDuration.toNanos(),
      "delay must be < the maxDuration");
    Assert.state(config.jitter == null || delay.toNanos() >= config.jitter.toNanos(),
      "delay must be >= the jitter duration");
    super.withDelay(delay);

    // Clear backoff and random delays
    config.maxDelay = null;
    config.delayMin = null;
    config.delayMax = null;
    return this;
  }

  /**
   * Sets a random delay between the {@code delayMin} and {@code delayMax} (inclusive) to occur between retries.
   * Replaces any previously configured {@link #withDelay(Duration) fixed} or {@link #withBackoff(Duration, Duration)
   * backoff} delays.
   *
   * @throws NullPointerException if {@code chronoUnit} is null
   * @throws IllegalArgumentException if {@code delayMin} or {@code delayMax} are <= 0, or {@code delayMin} >= {@code
   * delayMax}
   * @throws IllegalStateException if {@code delayMax} is >= the {@link #withMaxDuration(Duration) maxDuration} or
   * {@code delayMin} is < a configured {@link #withJitter(Duration) jitter duration}
   */
  public RetryPolicyBuilder<R> withDelay(long delayMin, long delayMax, ChronoUnit chronoUnit) {
    return withDelay(Duration.of(delayMin, chronoUnit), Duration.of(delayMax, chronoUnit));
  }

  /**
   * Sets a random delay between the {@code delayMin} and {@code delayMax} (inclusive) to occur between retries.
   * Replaces any previously configured {@link #withDelay(Duration) fixed} or {@link #withBackoff(Duration, Duration)
   * backoff} delays.
   *
   * @throws NullPointerException if {@code delayMin} or {@code delayMax} are null
   * @throws IllegalArgumentException if {@code delayMin} or {@code delayMax} are <= 0, or {@code delayMin} >= {@code
   * delayMax}
   * @throws IllegalStateException if {@code delayMax} is >= the {@link RetryPolicyBuilder#withMaxDuration(Duration)
   * maxDuration} or {@code delay} is < a configured {@link #withJitter(Duration) jitter duration}
   */
  public RetryPolicyBuilder<R> withDelay(Duration delayMin, Duration delayMax) {
    Assert.notNull(delayMin, "delayMin");
    Assert.notNull(delayMax, "delayMax");
    delayMin = Durations.ofSafeNanos(delayMin);
    delayMax = Durations.ofSafeNanos(delayMax);
    Assert.isTrue(!delayMin.isNegative() && !delayMin.isZero(), "delayMin must be > 0");
    Assert.isTrue(!delayMax.isNegative() && !delayMax.isZero(), "delayMax must be > 0");
    Assert.isTrue(delayMin.toNanos() < delayMax.toNanos(), "delayMin must be < delayMax");
    Assert.state(config.maxDuration == null || delayMax.toNanos() < config.maxDuration.toNanos(),
      "delayMax must be < the maxDuration");
    Assert.state(config.jitter == null || delayMin.toNanos() >= config.jitter.toNanos(),
      "delayMin must be >= the jitter duration");
    config.delayMin = delayMin;
    config.delayMax = delayMax;

    // Clear fixed and random delays
    config.delay = Duration.ZERO;
    config.maxDelay = null;
    return this;
  }

  /**
   * Sets the {@code jitterFactor} to randomly vary retry delays by. For each retry delay, a random portion of the delay
   * multiplied by the {@code jitterFactor} will be added or subtracted to the delay. For example: a retry delay of
   * {@code 100} milliseconds and a {@code jitterFactor} of {@code .25} will result in a random retry delay between
   * {@code 75} and {@code 125} milliseconds. Replaces any previously configured {@link #withJitter(Duration) jitter
   * duration}.
   * <p>
   * Jitter should be combined with {@link #withDelay(Duration) fixed}, {@link #withDelay(long, long, ChronoUnit)
   * random} or {@link #withBackoff(long, long, ChronoUnit) exponential backoff} delays. If no delays are configured,
   * this setting is ignored.
   *
   * @throws IllegalArgumentException if {@code jitterFactor} is < 0 or > 1
   */
  public RetryPolicyBuilder<R> withJitter(double jitterFactor) {
    Assert.isTrue(jitterFactor >= 0.0 && jitterFactor <= 1.0, "jitterFactor must be >= 0 and <= 1");
    config.jitterFactor = jitterFactor;

    // Clear the jitter duration
    config.jitter = null;
    return this;
  }

  /**
   * Sets the {@code jitter} to randomly vary retry delays by. For each retry delay, a random portion of the {@code
   * jitter} will be added or subtracted to the delay. For example: a {@code jitter} of {@code 100} milliseconds will
   * randomly add between {@code -100} and {@code 100} milliseconds to each retry delay. Replaces any previously
   * configured {@link #withJitter(double) jitter factor}.
   * <p>
   * Jitter should be combined with {@link #withDelay(Duration) fixed}, {@link #withDelay(long, long, ChronoUnit)
   * random} or {@link #withBackoff(long, long, ChronoUnit) exponential backoff} delays. If no delays are configured,
   * this setting is ignored.
   *
   * @throws NullPointerException if {@code jitter} is null
   * @throws IllegalArgumentException if {@code jitter} is <= 0
   * @throws IllegalStateException if the jitter is greater than the min configured delay
   */
  public RetryPolicyBuilder<R> withJitter(Duration jitter) {
    Assert.notNull(jitter, "jitter");
    jitter = Durations.ofSafeNanos(jitter);
    Assert.isTrue(jitter.toNanos() > 0, "jitter must be > 0");
    boolean validJitter = config.delayMin != null ?
      jitter.toNanos() <= config.delayMin.toNanos() :
      config.delay == Duration.ZERO || jitter.toNanos() < config.delay.toNanos();
    Assert.state(validJitter, "jitter must be < the minimum configured delay");
    config.jitter = jitter;

    // Clear the jitter factor
    config.jitterFactor = 0;
    return this;
  }

  /**
   * Sets the max number of execution attempts to perform. {@code -1} indicates no limit. This method has the same
   * effect as setting 1 more than {@link #withMaxRetries(int)}. For example, 2 retries equal 3 attempts.
   *
   * @throws IllegalArgumentException if {@code maxAttempts} is 0 or less than -1
   * @see #withMaxRetries(int)
   */
  public RetryPolicyBuilder<R> withMaxAttempts(int maxAttempts) {
    Assert.isTrue(maxAttempts != 0, "maxAttempts cannot be 0");
    Assert.isTrue(maxAttempts >= -1, "maxAttempts must be >= -1");
    config.maxRetries = maxAttempts == -1 ? -1 : maxAttempts - 1;
    return this;
  }

  /**
   * Sets the max duration to perform retries for, else the execution will be failed.
   * <p>
   * Notes:
   * <ul>
   *   <li>This setting will not cause long running executions to be interrupted. For that capability, use a
   *   {@link Timeout} policy {@link TimeoutBuilder#withInterrupt() withInterrupt} set.</li>
   *   <li>This setting will not disable {@link #withMaxRetries(int) max retries}, which are still {@code 2} by default.
   *   A max retries limit can be disabled via <code>withMaxRetries(-1)</code></li>
   * </ul>
   * </p>
   *
   * @throws NullPointerException if {@code maxDuration} is null
   * @throws IllegalStateException if {@code maxDuration} is <= the {@link #withDelay(Duration) delay} or {@code
   * maxDuration} is <= the {@link #withDelay(Duration, Duration) max random delay}.
   */
  public RetryPolicyBuilder<R> withMaxDuration(Duration maxDuration) {
    Assert.notNull(maxDuration, "maxDuration");
    maxDuration = Durations.ofSafeNanos(maxDuration);
    Assert.state(maxDuration.toNanos() > config.delay.toNanos(), "maxDuration must be > the delay");
    Assert.state(config.delayMax == null || maxDuration.toNanos() > config.delayMax.toNanos(),
      "maxDuration must be > the max random delay");
    config.maxDuration = maxDuration;
    return this;
  }

  /**
   * Sets the max number of retries to perform when an execution attempt fails. {@code -1} indicates no limit. This
   * method has the same effect as setting 1 less than {@link #withMaxAttempts(int)}. For example, 2 retries equal 3
   * attempts.
   *
   * @throws IllegalArgumentException if {@code maxRetries} &lt -1
   * @see #withMaxAttempts(int)
   */
  public RetryPolicyBuilder<R> withMaxRetries(int maxRetries) {
    Assert.isTrue(maxRetries >= -1, "maxRetries must be >= to -1");
    config.maxRetries = maxRetries;
    return this;
  }
}
