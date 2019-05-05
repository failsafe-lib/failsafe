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

import net.jodah.failsafe.event.ExecutionAttemptedEvent;
import net.jodah.failsafe.event.ExecutionCompletedEvent;
import net.jodah.failsafe.function.CheckedConsumer;
import net.jodah.failsafe.internal.EventListener;
import net.jodah.failsafe.internal.executor.RetryPolicyExecutor;
import net.jodah.failsafe.internal.util.Assert;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * A policy that defines when retries should be performed.
 *
 * <p>
 * The {@code handle} methods describe when a retry should be performed for a particular failure. The {@code
 * handleResult} methods describe when a retry should be performed for a particular result. If multiple {@code handle}
 * or {@code handleResult} conditions are specified, any matching condition can allow a retry. The {@code abortOn},
 * {@code abortWhen} and {@code abortIf} methods describe when retries should be aborted.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
@SuppressWarnings("WeakerAccess")
public class RetryPolicy<R> extends FailurePolicy<RetryPolicy<R>, R> {
  private static final int DEFAULT_MAX_RETRIES = 2;

  /**
   * A functional interface for computing delays between retries in conjunction with {@link #withDelay(DelayFunction)}.
   *
   * @param <R> result type
   * @param <F> failure type
   */
  @FunctionalInterface
  public interface DelayFunction<R, F extends Throwable> {
    /**
     * Returns the amount of delay before the next retry based on the result or failure of the last attempt and the
     * execution context (executions so far). This method must complete quickly and should not have side-effects.
     * Unchecked exceptions thrown by this method will <strong>not</strong> be treated as part of the fail-safe
     * processing and will instead abort that processing.
     * <p>
     * Notes:
     * <ul>
     * <li>A negative return value will cause Failsafe to use a configured fixed or backoff delay
     * <li>Any configured jitter is still applied to DelayFunction provided values
     * <li>Any configured max duration is still applied to DelayFunction provided values
     * </ul>
     *
     * @param result the result, if any, of the last attempt
     * @param failure the {@link Throwable} thrown, if any, during the last attempt
     * @param context the {@link ExecutionContext} that describes executions so far
     * @return a non-negative duration to be used as the delay before next retry, otherwise (null or negative duration)
     * means fall back to the fixed or backoff delay for next retry
     * @see #withDelay(DelayFunction)
     * @see #withDelayOn(DelayFunction, Class)
     * @see #withDelayWhen(DelayFunction, R)
     */
    Duration computeDelay(R result, F failure, ExecutionContext context);
  }

  // Policy config
  private Duration delay;
  private Duration delayMin;
  private Duration delayMax;
  private double delayFactor;
  private Duration maxDelay;
  private DelayFunction<R, ? extends Throwable> delayFn;
  private Object delayResult;
  private Class<? extends Throwable> delayFailure;
  private Duration jitter;
  private double jitterFactor;
  private Duration maxDuration;
  private int maxRetries;
  private List<BiPredicate<R, Throwable>> abortConditions;

  // Listeners
  private EventListener abortListener;
  private EventListener failedAttemptListener;
  private EventListener retriesExceededListener;
  private EventListener retryListener;

  /**
   * Creates a retry policy that allows 3 execution attempts max with no delay.
   */
  public RetryPolicy() {
    delay = Duration.ZERO;
    maxRetries = DEFAULT_MAX_RETRIES;
    abortConditions = new ArrayList<>();
  }

  /**
   * Copy constructor.
   */
  private RetryPolicy(RetryPolicy<R> rp) {
    this.delay = rp.delay;
    this.delayMin = rp.delayMin;
    this.delayMax = rp.delayMax;
    this.delayFactor = rp.delayFactor;
    this.maxDelay = rp.maxDelay;
    this.delayFn = rp.delayFn;
    this.delayResult = rp.delayResult;
    this.delayFailure = rp.delayFailure;
    this.maxDuration = rp.maxDuration;
    this.maxRetries = rp.maxRetries;
    this.jitter = rp.jitter;
    this.jitterFactor = rp.jitterFactor;
    this.failuresChecked = rp.failuresChecked;
    this.failureConditions = new ArrayList<>(rp.failureConditions);
    this.abortConditions = new ArrayList<>(rp.abortConditions);
    this.abortListener = rp.abortListener;
    this.failedAttemptListener = rp.failedAttemptListener;
    this.retriesExceededListener = rp.retriesExceededListener;
    this.retryListener = rp.retryListener;
  }

  /**
   * Specifies that retries should be aborted if the {@code completionPredicate} matches the completion result.
   *
   * @throws NullPointerException if {@code completionPredicate} is null
   */
  @SuppressWarnings("unchecked")
  public RetryPolicy<R> abortIf(BiPredicate<R, ? extends Throwable> completionPredicate) {
    Assert.notNull(completionPredicate, "completionPredicate");
    abortConditions.add((BiPredicate<R, Throwable>) completionPredicate);
    return this;
  }

  /**
   * Specifies that retries should be aborted if the {@code resultPredicate} matches the result. Predicate is not
   * invoked when the operation fails.
   *
   * @throws NullPointerException if {@code resultPredicate} is null
   */
  public RetryPolicy<R> abortIf(Predicate<R> resultPredicate) {
    Assert.notNull(resultPredicate, "resultPredicate");
    abortConditions.add(resultPredicateFor(resultPredicate));
    return this;
  }

  /**
   * Specifies when retries should be aborted. Any failure that is assignable from the {@code failure} will be result in
   * retries being aborted.
   *
   * @throws NullPointerException if {@code failure} is null
   */
  @SuppressWarnings({ "rawtypes" })
  public RetryPolicy<R> abortOn(Class<? extends Throwable> failure) {
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
  @SuppressWarnings("unchecked")
  public RetryPolicy<R> abortOn(Class<? extends Throwable>... failures) {
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
  public RetryPolicy<R> abortOn(List<Class<? extends Throwable>> failures) {
    Assert.notNull(failures, "failures");
    Assert.isTrue(!failures.isEmpty(), "failures cannot be empty");
    abortConditions.add(failurePredicateFor(failures));
    return this;
  }

  /**
   * Specifies that retries should be aborted if the {@code failurePredicate} matches the failure.
   *
   * @throws NullPointerException if {@code failurePredicate} is null
   */
  public RetryPolicy<R> abortOn(Predicate<? extends Throwable> failurePredicate) {
    Assert.notNull(failurePredicate, "failurePredicate");
    abortConditions.add(failurePredicateFor(failurePredicate));
    return this;
  }

  /**
   * Specifies that retries should be aborted if the execution result matches the {@code result}.
   */
  public RetryPolicy<R> abortWhen(R result) {
    abortConditions.add(resultPredicateFor(result));
    return this;
  }

  /**
   * Returns whether the policy allows retries according to the configured {@link #withMaxRetries(int) maxRetries} and
   * {@link #withMaxDuration(Duration) maxDuration}.
   *
   * @see #withMaxRetries(int)
   * @see #withMaxDuration(Duration)
   */
  public boolean allowsRetries() {
    return (maxRetries == -1 || maxRetries > 0) && (maxDuration == null || maxDuration.toNanos() > 0);
  }

  /**
   * Returns whether an execution result can be aborted given the configured abort conditions.
   *
   * @see #abortOn(Class...)
   * @see #abortOn(List)
   * @see #abortOn(Predicate)
   * @see #abortIf(BiPredicate)
   * @see #abortIf(Predicate)
   * @see #abortWhen(R)
   */
  public boolean isAbortable(R result, Throwable failure) {
    for (BiPredicate<R, Throwable> predicate : abortConditions) {
      try {
        if (predicate.test(result, failure))
          return true;
      } catch (Exception t) {
        // Ignore confused user-supplied predicates.
        // They should not be allowed to halt execution of the operation.
      }
    }
    return false;
  }

  /**
   * Registers the {@code listener} to be called when an execution is aborted.
   * <p>Note: Any exceptions that are thrown from within the {@code listener} are ignored.</p>
   */
  public RetryPolicy<R> onAbort(CheckedConsumer<? extends ExecutionCompletedEvent<R>> listener) {
    abortListener = EventListener.of(Assert.notNull(listener, "listener"));
    return this;
  }

  /**
   * Registers the {@code listener} to be called when an execution attempt fails.
   * <p>Note: Any exceptions that are thrown from within the {@code listener} are ignored.</p>
   */
  public RetryPolicy<R> onFailedAttempt(CheckedConsumer<? extends ExecutionAttemptedEvent<R>> listener) {
    failedAttemptListener = EventListener.ofAttempt(Assert.notNull(listener, "listener"));
    return this;
  }

  /**
   * Registers the {@code listener} to be called when an execution fails and the {@link RetryPolicy#withMaxRetries(int)
   * max retry attempts} or {@link RetryPolicy#withMaxDuration(Duration) max duration} are exceeded.
   * <p>Note: Any exceptions that are thrown from within the {@code listener} are ignored.</p>
   */
  public RetryPolicy<R> onRetriesExceeded(CheckedConsumer<? extends ExecutionCompletedEvent<R>> listener) {
    retriesExceededListener = EventListener.of(Assert.notNull(listener, "listener"));
    return this;
  }

  /**
   * Registers the {@code listener} to be called before an execution is retried.
   * <p>Note: Any exceptions that are thrown from within the {@code listener} are ignored.</p>
   */
  public RetryPolicy<R> onRetry(CheckedConsumer<? extends ExecutionAttemptedEvent<R>> listener) {
    retryListener = EventListener.ofAttempt(Assert.notNull(listener, "listener"));
    return this;
  }

  /**
   * Returns whether any configured delay function can be applied for an execution result.
   *
   * @see #withDelay(DelayFunction)
   * @see #withDelayOn(DelayFunction, Class)
   * @see #withDelayWhen(DelayFunction, Object)
   */
  public boolean canApplyDelayFn(R result, Throwable failure) {
    return (delayResult == null || delayResult.equals(result)) && (delayFailure == null || (failure != null
      && delayFailure.isAssignableFrom(failure.getClass())));
  }

  /**
   * Returns a copy of this RetryPolicy.
   */
  public RetryPolicy<R> copy() {
    return new RetryPolicy<>(this);
  }

  /**
   * Returns the delay between retries. Defaults to {@link Duration#ZERO}.
   *
   * @see #withDelay(Duration)
   * @see #withBackoff(long, long, ChronoUnit)
   * @see #withBackoff(long, long, ChronoUnit, double)
   */
  public Duration getDelay() {
    return delay;
  }

  /**
   * Returns the min delay between retries.
   *
   * @see #withDelay(long, long, ChronoUnit)
   */
  public Duration getDelayMin() {
    return delayMin;
  }

  /**
   * Returns the max delay between retries.
   *
   * @see #withDelay(long, long, ChronoUnit)
   */
  public Duration getDelayMax() {
    return delayMax;
  }

  /**
   * Returns the function that determines the next delay given a failed attempt with the given {@link Throwable}.
   *
   * @see #withDelay(DelayFunction)
   * @see #withDelayOn(DelayFunction, Class)
   * @see #withDelayWhen(DelayFunction, Object)
   */
  public DelayFunction<R, ? extends Throwable> getDelayFn() {
    return delayFn;
  }

  /**
   * Returns the delay factor for backoff retries.
   *
   * @see #withBackoff(long, long, ChronoUnit, double)
   */
  public double getDelayFactor() {
    return delayFactor;
  }

  /**
   * Returns the jitter, else {@code null} if none has been configured.
   *
   * @see #withJitter(Duration)
   */
  public Duration getJitter() {
    return jitter;
  }

  /**
   * Returns the jitter factor, else {@code 0.0} if none has been configured.
   *
   * @see #withJitter(double)
   */
  public double getJitterFactor() {
    return jitterFactor;
  }

  /**
   * Returns the max number of execution attempts to perform. A value of {@code -1} represents no limit. Defaults to
   * {@code 3}.
   *
   * @see #withMaxAttempts(int)
   * @see #getMaxRetries()
   */
  public int getMaxAttempts() {
    return maxRetries == -1 ? -1 : maxRetries + 1;
  }

  /**
   * Returns the max delay between backoff retries.
   *
   * @see #withBackoff(long, long, ChronoUnit)
   */
  public Duration getMaxDelay() {
    return maxDelay;
  }

  /**
   * Returns the max duration to perform retries for.
   *
   * @see #withMaxDuration(Duration)
   */
  public Duration getMaxDuration() {
    return maxDuration;
  }

  /**
   * Returns the max number of retries to perform when an execution attempt fails. A value of {@code -1} represents no
   * limit. Defaults to {@code 2}.
   *
   * @see #withMaxRetries(int)
   * @see #getMaxAttempts()
   */
  public int getMaxRetries() {
    return maxRetries;
  }

  /**
   * Sets the {@code delay} between retries, exponentially backing off to the {@code maxDelay} and multiplying
   * successive delays by a factor of 2.
   *
   * @throws NullPointerException if {@code chronoUnit} is null
   * @throws IllegalArgumentException if {@code delay} is <= 0 or {@code delay} is >= {@code maxDelay}
   * @throws IllegalStateException if {@code delay} is >= the {@link RetryPolicy#withMaxDuration(Duration) maxDuration},
   * if delays have already been set, or if random delays have already been set
   */
  public RetryPolicy<R> withBackoff(long delay, long maxDelay, ChronoUnit chronoUnit) {
    return withBackoff(delay, maxDelay, chronoUnit, 2);
  }

  /**
   * Sets the {@code delay} between retries, exponentially backing off to the {@code maxDelay} and multiplying
   * successive delays by the {@code delayFactor}.
   *
   * @throws NullPointerException if {@code chronoUnit} is null
   * @throws IllegalArgumentException if {@code delay} <= 0, {@code delay} is >= {@code maxDelay}, or the {@code
   * delayFactor} is <= 1
   * @throws IllegalStateException if {@code delay} is >= the {@link RetryPolicy#withMaxDuration(Duration) maxDuration},
   * if delays have already been set, or if random delays have already been set
   */
  public RetryPolicy<R> withBackoff(long delay, long maxDelay, ChronoUnit chronoUnit, double delayFactor) {
    Assert.notNull(chronoUnit, "chronoUnit");
    Assert.isTrue(delay > 0, "The delay must be greater than 0");
    Duration delayDuration = Duration.of(delay, chronoUnit);
    Duration maxDelayDuration = Duration.of(maxDelay, chronoUnit);
    Assert.state(maxDuration == null || delayDuration.toNanos() < maxDuration.toNanos(),
      "delay must be less than the maxDuration");
    Assert.isTrue(delayDuration.toNanos() < maxDelayDuration.toNanos(), "delay must be less than the maxDelay");
    Assert.isTrue(delayFactor > 1, "delayFactor must be greater than 1");
    Assert.state(this.delay == null || this.delay.equals(Duration.ZERO), "Delays have already been set");
    Assert.state(delayMin == null, "Random delays have already been set");
    this.delay = delayDuration;
    this.maxDelay = maxDelayDuration;
    this.delayFactor = delayFactor;
    return this;
  }

  /**
   * Sets the {@code delay} to occur between retries.
   *
   * @throws NullPointerException if {@code chronoUnit} is null
   * @throws IllegalArgumentException if {@code delay} <= 0
   * @throws IllegalStateException if {@code delay} is >= the {@link RetryPolicy#withMaxDuration(Duration) maxDuration},
   * if random delays have already been set, or if backoff delays have already been set
   */
  public RetryPolicy<R> withDelay(Duration delay) {
    Assert.notNull(delay, "delay");
    Assert.isTrue(delay.toNanos() > 0, "delay must be greater than 0");
    Assert.state(maxDuration == null || delay.toNanos() < maxDuration.toNanos(),
      "delay must be less than the maxDuration");
    Assert.state(delayMin == null, "Random delays have already been set");
    Assert.state(maxDelay == null, "Backoff delays have already been set");
    this.delay = delay;
    return this;
  }

  /**
   * Sets a random delay between the {@code delayMin} and {@code delayMax} (inclusive) to occur between retries.
   *
   * @throws NullPointerException if {@code chronoUnit} is null
   * @throws IllegalArgumentException if {@code delayMin} or {@code delayMax} are <= 0, or {@code delayMin} >= {@code
   * delayMax}
   * @throws IllegalStateException if {@code delayMax} is >= the {@link RetryPolicy#withMaxDuration(Duration)
   * maxDuration}, if delays have already been set, if backoff delays have already been set
   */
  public RetryPolicy<R> withDelay(long delayMin, long delayMax, ChronoUnit chronoUnit) {
    Assert.notNull(chronoUnit, "chronoUnit");
    Assert.isTrue(delayMin > 0, "delayMin must be greater than 0");
    Assert.isTrue(delayMax > 0, "delayMax must be greater than 0");
    Duration delayMinDuration = Duration.of(delayMin, chronoUnit);
    Duration delayMaxDuration = Duration.of(delayMax, chronoUnit);
    Assert.isTrue(delayMinDuration.toNanos() < delayMaxDuration.toNanos(), "delayMin must be less than delayMax");
    Assert.state(maxDuration == null || delayMaxDuration.toNanos() < maxDuration.toNanos(),
      "delayMax must be less than the maxDuration");
    Assert.state(delay == null || delay.equals(Duration.ZERO), "Delays have already been set");
    Assert.state(maxDelay == null, "Backoff delays have already been set");
    this.delayMin = delayMinDuration;
    this.delayMax = delayMaxDuration;
    return this;
  }

  /**
   * Sets the {@code delayFunction} that computes the next delay before retrying.
   *
   * @param delayFunction the function to use to compute the delay before a next attempt
   * @throws NullPointerException if {@code delayFunction} is null
   * @see DelayFunction
   */
  public RetryPolicy<R> withDelay(DelayFunction<R, ? extends Throwable> delayFunction) {
    Assert.notNull(delayFunction, "delayFunction");
    this.delayFn = delayFunction;
    return this;
  }

  /**
   * Sets the {@code delayFunction} that computes the next delay before retrying. Delays will only occur for failures
   * that are assignable from the {@code failure}.
   *
   * @param delayFunction the function to use to compute the delay before a next attempt
   * @param failure the execution failure that is expected in order to trigger the delay
   * @param <F> failure type
   * @throws NullPointerException if {@code delayFunction} or {@code failure} are null
   * @see DelayFunction
   */
  public <F extends Throwable> RetryPolicy<R> withDelayOn(DelayFunction<R, F> delayFunction, Class<F> failure) {
    withDelay(delayFunction);
    Assert.notNull(failure, "failure");
    this.delayFailure = failure;
    return this;
  }

  /**
   * Sets the {@code delayFunction} that computes the next delay before retrying. Delays will only occur for results
   * that equal the {@code result}.
   *
   * @param delayFunction the function to use to compute the delay before a next attempt
   * @param result the execution result that is expected in order to trigger the delay
   * @throws NullPointerException if {@code delayFunction} or {@code result} are null
   * @see DelayFunction
   */
  public RetryPolicy<R> withDelayWhen(DelayFunction<R, ? extends Throwable> delayFunction, R result) {
    withDelay(delayFunction);
    Assert.notNull(result, "result");
    this.delayResult = result;
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
   * @throws IllegalStateException if no delay has been configured or {@link #withJitter(Duration)} has already been
   * called
   */
  public RetryPolicy<R> withJitter(double jitterFactor) {
    Assert.isTrue(jitterFactor >= 0.0 && jitterFactor <= 1.0, "jitterFactor must be >= 0 and <= 1");
    Assert.state(delay != null || delayMin != null, "A delay must be configured");
    Assert.state(jitter == null, "withJitter(Duration) has already been called");
    this.jitterFactor = jitterFactor;
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
   * @throws IllegalStateException if no delay has been configured or {@link #withJitter(double)} has already been
   * called
   */
  public RetryPolicy<R> withJitter(Duration jitter) {
    Assert.notNull(jitter, "jitter");
    Assert.isTrue(jitter.toNanos() > 0, "jitter must be > 0");
    Assert.state(delay != null || delayMin != null, "A delay must be configured");
    Assert.state(jitterFactor == 0.0, "withJitter(double) has already been called");
    Assert.state(jitter.toNanos() <= delay.toNanos(), "jitter must be less than the configured delay");
    this.jitter = jitter;
    return this;
  }

  /**
   * Sets the max number of execution attempts to perform. {@code -1} indicates no limit. This method has the same
   * effect as setting 1 more than {@link #withMaxRetries(int)}. For example, 2 retries equal 3 attempts.
   *
   * @throws IllegalArgumentException if {@code maxAttempts} is 0 or less than -1
   * @see #withMaxRetries(int)
   */
  public RetryPolicy<R> withMaxAttempts(int maxAttempts) {
    Assert.isTrue(maxAttempts != 0, "maxAttempts cannot be 0");
    Assert.isTrue(maxAttempts >= -1, "maxAttempts cannot be less than -1");
    this.maxRetries = maxAttempts == -1 ? -1 : maxAttempts - 1;
    return this;
  }

  /**
   * Sets the max duration to perform retries for, else the execution will be failed.
   *
   * @throws NullPointerException if {@code maxDuration} is null
   * @throws IllegalStateException if {@code maxDuration} is <= the {@link RetryPolicy#withDelay(Duration) delay}
   */
  public RetryPolicy<R> withMaxDuration(Duration maxDuration) {
    Assert.notNull(maxDuration, "maxDuration");
    Assert.state(maxDuration.toNanos() > delay.toNanos(), "maxDuration must be greater than the delay");
    this.maxDuration = maxDuration;
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
  public RetryPolicy<R> withMaxRetries(int maxRetries) {
    Assert.isTrue(maxRetries >= -1, "maxRetries must be greater than or equal to -1");
    this.maxRetries = maxRetries;
    return this;
  }

  @Override
  public PolicyExecutor toExecutor(AbstractExecution execution) {
    return new RetryPolicyExecutor(this, execution, abortListener, failedAttemptListener, retriesExceededListener,
      retryListener);
  }
}
