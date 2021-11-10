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
package net.jodah.failsafe;

import net.jodah.failsafe.event.EventListener;
import net.jodah.failsafe.event.ExecutionAttemptedEvent;
import net.jodah.failsafe.event.ExecutionCompletedEvent;
import net.jodah.failsafe.event.ExecutionScheduledEvent;
import net.jodah.failsafe.internal.RetryPolicyImpl;
import net.jodah.failsafe.internal.util.Assert;

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
 * @see RetryPolicy
 */
public class RetryPolicyBuilder<R> extends DelayablePolicyBuilder<RetryPolicyBuilder<R>, RetryPolicyConfig<R>, R>
  implements RetryPolicyListeners<RetryPolicyBuilder<R>, R> {

  private static final int DEFAULT_MAX_RETRIES = 2;

  RetryPolicyBuilder() {
    super(new RetryPolicyConfig<>());
    config.delay = Duration.ZERO;
    config.maxRetries = DEFAULT_MAX_RETRIES;
    config.abortConditions = new ArrayList<>();
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

  @Override
  public RetryPolicyBuilder<R> onAbort(EventListener<ExecutionCompletedEvent<R>> listener) {
    config.abortListener = Assert.notNull(listener, "listener");
    return this;
  }

  @Override
  public RetryPolicyBuilder<R> onFailedAttempt(EventListener<ExecutionAttemptedEvent<R>> listener) {
    config.failedAttemptListener = Assert.notNull(listener, "listener");
    return this;
  }

  @Override
  public RetryPolicyBuilder<R> onRetriesExceeded(EventListener<ExecutionCompletedEvent<R>> listener) {
    config.retriesExceededListener = Assert.notNull(listener, "listener");
    return this;
  }

  @Override
  public RetryPolicyBuilder<R> onRetry(EventListener<ExecutionAttemptedEvent<R>> listener) {
    config.retryListener = Assert.notNull(listener, "listener");
    return this;
  }

  @Override
  public RetryPolicyBuilder<R> onRetryScheduled(EventListener<ExecutionScheduledEvent<R>> listener) {
    config.retryScheduledListener = Assert.notNull(listener, "listener");
    return this;
  }

  /**
   * Sets the {@code delay} between retries, exponentially backing off to the {@code maxDelay} and multiplying
   * successive delays by a factor of 2.
   *
   * @throws NullPointerException if {@code delay} or {@code maxDelay} are null
   * @throws IllegalArgumentException if {@code delay} is <= 0 or {@code delay} is >= {@code maxDelay}
   * @throws IllegalStateException if {@code delay} is >= the {@link RetryPolicyBuilder#withMaxDuration(Duration)
   * maxDuration}, if delays have already been set, or if random delays have already been set
   */
  public RetryPolicyBuilder<R> withBackoff(Duration delay, Duration maxDelay) {
    return withBackoff(delay, maxDelay, 2);
  }

  /**
   * Sets the {@code delay} between retries, exponentially backing off to the {@code maxDelay} and multiplying
   * successive delays by a factor of 2.
   *
   * @throws NullPointerException if {@code chronoUnit} is null
   * @throws IllegalArgumentException if {@code delay} is <= 0 or {@code delay} is >= {@code maxDelay}
   * @throws IllegalStateException if {@code delay} is >= the {@link RetryPolicyBuilder#withMaxDuration(Duration)
   * maxDuration}, if delays have already been set, or if random delays have already been set
   */
  public RetryPolicyBuilder<R> withBackoff(long delay, long maxDelay, ChronoUnit chronoUnit) {
    return withBackoff(delay, maxDelay, chronoUnit, 2);
  }

  /**
   * Sets the {@code delay} between retries, exponentially backing off to the {@code maxDelay} and multiplying
   * successive delays by the {@code delayFactor}.
   *
   * @throws NullPointerException if {@code chronoUnit} is null
   * @throws IllegalArgumentException if {@code delay} <= 0, {@code delay} is >= {@code maxDelay}, or the {@code
   * delayFactor} is <= 1
   * @throws IllegalStateException if {@code delay} is >= the {@link RetryPolicyBuilder#withMaxDuration(Duration)
   * maxDuration}, if delays have already been set, or if random delays have already been set
   */
  public RetryPolicyBuilder<R> withBackoff(long delay, long maxDelay, ChronoUnit chronoUnit, double delayFactor) {
    return withBackoff(Duration.of(delay, chronoUnit), Duration.of(maxDelay, chronoUnit), delayFactor);
  }

  /**
   * Sets the {@code delay} between retries, exponentially backing off to the {@code maxDelay} and multiplying
   * successive delays by the {@code delayFactor}.
   *
   * @throws NullPointerException if {@code delay} or {@code maxDelay} are null
   * @throws IllegalArgumentException if {@code delay} <= 0, {@code delay} is >= {@code maxDelay}, or the {@code
   * delayFactor} is <= 1
   * @throws IllegalStateException if {@code delay} is >= the {@link RetryPolicyBuilder#withMaxDuration(Duration)
   * maxDuration}, if delays have already been set, or if random delays have already been set
   */
  public RetryPolicyBuilder<R> withBackoff(Duration delay, Duration maxDelay, double delayFactor) {
    Assert.notNull(delay, "delay");
    Assert.notNull(maxDelay, "maxDelay");
    Assert.isTrue(!delay.isNegative() && !delay.isZero(), "The delay must be greater than 0");
    Assert.state(config.maxDuration == null || delay.toNanos() < config.maxDuration.toNanos(),
      "delay must be less than the maxDuration");
    Assert.isTrue(delay.toNanos() < maxDelay.toNanos(), "delay must be less than the maxDelay");
    Assert.isTrue(delayFactor > 1, "delayFactor must be greater than 1");
    Assert.state(config.delay == null || config.delay.equals(Duration.ZERO), "Delays have already been set");
    Assert.state(config.delayMin == null, "Random delays have already been set");
    config.delay = delay;
    config.maxDelay = maxDelay;
    config.delayFactor = delayFactor;
    return this;
  }

  /**
   * Sets the {@code delay} to occur between retries.
   *
   * @throws NullPointerException if {@code delay} is null
   * @throws IllegalArgumentException if {@code delay} <= 0
   * @throws IllegalStateException if {@code delay} is >= the {@link RetryPolicyBuilder#withMaxDuration(Duration)
   * maxDuration}, if random delays have already been set, or if backoff delays have already been set
   */
  @Override
  public RetryPolicyBuilder<R> withDelay(Duration delay) {
    Assert.notNull(delay, "delay");
    Assert.state(config.maxDuration == null || delay.toNanos() < config.maxDuration.toNanos(),
      "delay must be less than the maxDuration");
    Assert.state(config.delayMin == null, "Random delays have already been set");
    Assert.state(config.maxDelay == null, "Backoff delays have already been set");
    return super.withDelay(delay);
  }

  /**
   * Sets a random delay between the {@code delayMin} and {@code delayMax} (inclusive) to occur between retries.
   *
   * @throws NullPointerException if {@code chronoUnit} is null
   * @throws IllegalArgumentException if {@code delayMin} or {@code delayMax} are <= 0, or {@code delayMin} >= {@code
   * delayMax}
   * @throws IllegalStateException if {@code delayMax} is >= the {@link RetryPolicyBuilder#withMaxDuration(Duration)
   * maxDuration}, if delays have already been set, if backoff delays have already been set
   */
  public RetryPolicyBuilder<R> withDelay(long delayMin, long delayMax, ChronoUnit chronoUnit) {
    return withDelay(Duration.of(delayMin, chronoUnit), Duration.of(delayMax, chronoUnit));
  }

  /**
   * Sets a random delay between the {@code delayMin} and {@code delayMax} (inclusive) to occur between retries.
   *
   * @throws NullPointerException if {@code delayMin} or {@code delayMax} are null
   * @throws IllegalArgumentException if {@code delayMin} or {@code delayMax} are <= 0, or {@code delayMin} >= {@code
   * delayMax}
   * @throws IllegalStateException if {@code delayMax} is >= the {@link RetryPolicyBuilder#withMaxDuration(Duration)
   * maxDuration}, if delays have already been set, if backoff delays have already been set
   */
  public RetryPolicyBuilder<R> withDelay(Duration delayMin, Duration delayMax) {
    Assert.notNull(delayMin, "delayMin");
    Assert.notNull(delayMax, "delayMax");
    Assert.isTrue(!delayMin.isNegative() && !delayMin.isZero(), "delayMin must be greater than 0");
    Assert.isTrue(!delayMax.isNegative() && !delayMax.isZero(), "delayMax must be greater than 0");
    Assert.isTrue(delayMin.toNanos() < delayMax.toNanos(), "delayMin must be less than delayMax");
    Assert.state(config.maxDuration == null || delayMax.toNanos() < config.maxDuration.toNanos(),
      "delayMax must be less than the maxDuration");
    Assert.state(config.delay == null || config.delay.equals(Duration.ZERO), "Delays have already been set");
    Assert.state(config.maxDelay == null, "Backoff delays have already been set");
    config.delayMin = delayMin;
    config.delayMax = delayMax;
    return this;
  }

  /**
   * Sets the {@code jitterFactor} to randomly vary retry delays by. For each retry delay, a random portion of the delay
   * multiplied by the {@code jitterFactor} will be added or subtracted to the delay. For example: a retry delay of
   * {@code 100} milliseconds and a {@code jitterFactor} of {@code .25} will result in a random retry delay between
   * {@code 75} and {@code 125} milliseconds.
   * <p>
   * Jitter should be combined with {@link #withDelay(Duration) fixed}, {@link #withDelay(long, long, ChronoUnit)
   * random} or {@link #withBackoff(long, long, ChronoUnit) exponential backoff} delays.
   *
   * @throws IllegalArgumentException if {@code jitterFactor} is < 0 or > 1
   * @throws IllegalStateException if {@link #withJitter(Duration)} has already been called
   */
  public RetryPolicyBuilder<R> withJitter(double jitterFactor) {
    Assert.isTrue(jitterFactor >= 0.0 && jitterFactor <= 1.0, "jitterFactor must be >= 0 and <= 1");
    Assert.state(config.jitter == null, "withJitter(Duration) has already been called");
    config.jitterFactor = jitterFactor;
    return this;
  }

  /**
   * Sets the {@code jitter} to randomly vary retry delays by. For each retry delay, a random portion of the {@code
   * jitter} will be added or subtracted to the delay. For example: a {@code jitter} of {@code 100} milliseconds will
   * randomly add between {@code -100} and {@code 100} milliseconds to each retry delay.
   * <p>
   * Jitter should be combined with {@link #withDelay(Duration) fixed}, {@link #withDelay(long, long, ChronoUnit)
   * random} or {@link #withBackoff(long, long, ChronoUnit) exponential backoff} delays.
   *
   * @throws NullPointerException if {@code jitter} is null
   * @throws IllegalArgumentException if {@code jitter} is <= 0
   * @throws IllegalStateException if {@link #withJitter(double)} has already been called or the jitter is greater than
   * the min configured delay
   */
  public RetryPolicyBuilder<R> withJitter(Duration jitter) {
    Assert.notNull(jitter, "jitter");
    Assert.isTrue(jitter.toNanos() > 0, "jitter must be > 0");
    Assert.state(config.jitterFactor == 0.0, "withJitter(double) has already been called");
    Assert.state(jitter.toNanos() <= (config.delayMin != null ? config.delayMin.toNanos() : config.delay.toNanos()),
      "jitter must be less than the minimum configured delay");
    config.jitter = jitter;
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
    Assert.isTrue(maxAttempts >= -1, "maxAttempts cannot be less than -1");
    config.maxRetries = maxAttempts == -1 ? -1 : maxAttempts - 1;
    return this;
  }

  /**
   * Sets the max duration to perform retries for, else the execution will be failed.
   * <p>
   * Notes:
   * <ul>
   *   <li>This setting will not cause long running executions to be interrupted. For that capability, use a
   *   {@link Timeout} policy {@link TimeoutBuilder#withInterrupt() withInterrupt} set to {@code true}.</li>
   *   <li>This setting will not disable {@link #withMaxRetries(int) max retries}, which are still {@code 2} by default.
   *   A max retries limit can be disabled via <code>withMaxRetries(-1)</code></li>
   * </ul>
   * </p>
   *
   * @throws NullPointerException if {@code maxDuration} is null
   * @throws IllegalStateException if {@code maxDuration} is <= the {@link RetryPolicyBuilder#withDelay(Duration)
   * delay}
   */
  public RetryPolicyBuilder<R> withMaxDuration(Duration maxDuration) {
    Assert.notNull(maxDuration, "maxDuration");
    Assert.state(maxDuration.toNanos() > config.delay.toNanos(), "maxDuration must be greater than the delay");
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
    Assert.isTrue(maxRetries >= -1, "maxRetries must be greater than or equal to -1");
    config.maxRetries = maxRetries;
    return this;
  }
}
