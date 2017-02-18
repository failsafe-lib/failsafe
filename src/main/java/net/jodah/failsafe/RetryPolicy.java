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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import net.jodah.failsafe.function.BiPredicate;
import net.jodah.failsafe.function.Predicate;
import net.jodah.failsafe.internal.util.Assert;
import net.jodah.failsafe.util.Duration;

/**
 * A policy that defines when retries should be performed.
 * 
 * <p>
 * The {@code retryOn} methods describe when a retry should be performed for a particular failure. The {@code retryWhen}
 * method describes when a retry should be performed for a particular result. If multiple {@code retryOn} or
 * {@code retryWhen} conditions are specified, any matching condition can allow a retry. The {@code abortOn},
 * {@code abortWhen} and {@code abortIf} methods describe when retries should be aborted.
 * 
 * @author Jonathan Halterman
 */
public class RetryPolicy {
  static final RetryPolicy NEVER = new RetryPolicy().withMaxRetries(0);

  private Duration delay;
  private double delayFactor;
  private Duration jitter;
  private double jitterFactor;
  private Duration maxDelay;
  private Duration maxDuration;
  private int maxRetries;
  /** Indicates whether failures are checked by a configured retry condition */
  private boolean failuresChecked;
  private List<BiPredicate<Object, Throwable>> retryConditions;
  private List<BiPredicate<Object, Throwable>> abortConditions;

  /**
   * Creates a retry policy that always retries with no delay.
   */
  public RetryPolicy() {
    delay = Duration.NONE;
    maxRetries = -1;
    retryConditions = new ArrayList<BiPredicate<Object, Throwable>>();
    abortConditions = new ArrayList<BiPredicate<Object, Throwable>>();
  }

  /**
   * Copy constructor.
   */
  public RetryPolicy(RetryPolicy rp) {
    this.delay = rp.delay;
    this.delayFactor = rp.delayFactor;
    this.maxDelay = rp.maxDelay;
    this.maxDuration = rp.maxDuration;
    this.maxRetries = rp.maxRetries;
    this.jitter = rp.jitter;
    this.jitterFactor = rp.jitterFactor;
    this.failuresChecked = rp.failuresChecked;
    this.retryConditions = new ArrayList<BiPredicate<Object, Throwable>>(rp.retryConditions);
    this.abortConditions = new ArrayList<BiPredicate<Object, Throwable>>(rp.abortConditions);
  }

  /**
   * Specifies that retries should be aborted if the {@code completionPredicate} matches the completion result.
   * 
   * @throws NullPointerException if {@code completionPredicate} is null
   */
  @SuppressWarnings("unchecked")
  public <T> RetryPolicy abortIf(BiPredicate<T, ? extends Throwable> completionPredicate) {
    Assert.notNull(completionPredicate, "completionPredicate");
    abortConditions.add((BiPredicate<Object, Throwable>) completionPredicate);
    return this;
  }

  /**
   * Specifies that retries should be aborted if the {@code resultPredicate} matches the result.
   * 
   * @throws NullPointerException if {@code resultPredicate} is null
   */
  public <T> RetryPolicy abortIf(Predicate<T> resultPredicate) {
    Assert.notNull(resultPredicate, "resultPredicate");
    abortConditions.add(Predicates.resultPredicateFor(resultPredicate));
    return this;
  }

  /**
   * Specifies when retries should be aborted. Any failure that is assignable from the {@code failure} will be result
   * in retries being aborted.
   * 
   * @throws NullPointerException if {@code failure} is null
   */
  public RetryPolicy abortOn(Class<? extends Throwable> failure) {
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
  public RetryPolicy abortOn(Class<? extends Throwable>... failures) {
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
  public RetryPolicy abortOn(List<Class<? extends Throwable>> failures) {
    Assert.notNull(failures, "failures");
    Assert.isTrue(!failures.isEmpty(), "failures cannot be empty");
    abortConditions.add(Predicates.failurePredicateFor(failures));
    return this;
  }

  /**
   * Specifies that retries should be aborted if the {@code failurePredicate} matches the failure.
   * 
   * @throws NullPointerException if {@code failurePredicate} is null
   */
  public RetryPolicy abortOn(Predicate<? extends Throwable> failurePredicate) {
    Assert.notNull(failurePredicate, "failurePredicate");
    abortConditions.add(Predicates.failurePredicateFor(failurePredicate));
    return this;
  }

  /**
   * Specifies that retries should be aborted if the execution result matches the {@code result}.
   */
  public RetryPolicy abortWhen(Object result) {
    abortConditions.add(Predicates.resultPredicateFor(result));
    return this;
  }

  /**
   * Returns whether the policy allows retries according to the configured {@link #withMaxRetries(int) maxRetries} and
   * {@link #withMaxDuration(long, TimeUnit) maxDuration}.
   * 
   * @see #withMaxRetries(int)
   * @see #withMaxDuration(long, TimeUnit)
   */
  public boolean allowsRetries() {
    return (maxRetries == -1 || maxRetries > 0) && (maxDuration == null || maxDuration.toNanos() > 0);
  }

  /**
   * Returns whether an execution result can be aborted given the configured abort conditions.
   * 
   * @see #abortIf(BiPredicate)
   * @see #abortIf(Predicate)
   * @see #abortOn(Class...)
   * @see #abortOn(List)
   * @see #abortOn(Predicate)
   * @see #abortWhen(Object)
   */
  public boolean canAbortFor(Object result, Throwable failure) {
    for (BiPredicate<Object, Throwable> predicate : abortConditions) {
      if (predicate.test(result, failure))
        return true;
    }
    return false;
  }

  /**
   * Returns whether an execution result can be retried given the configured abort conditions.
   * 
   * @see #retryIf(BiPredicate)
   * @see #retryIf(Predicate)
   * @see #retryOn(Class...)
   * @see #retryOn(List)
   * @see #retryOn(Predicate)
   * @see #retryWhen(Object)
   */
  public boolean canRetryFor(Object result, Throwable failure) {
    for (BiPredicate<Object, Throwable> predicate : retryConditions) {
      if (predicate.test(result, failure))
        return true;
    }

    // Retry by default if a failure is not checked by a retry condition
    return failure != null && !failuresChecked;
  }

  /**
   * Returns a copy of this RetryPolicy.
   */
  public RetryPolicy copy() {
    return new RetryPolicy(this);
  }

  /**
   * Returns the delay between retries. Defaults to {@link Duration#NONE}.
   * 
   * @see #withDelay(long, TimeUnit)
   * @see #withBackoff(long, long, TimeUnit)
   * @see #withBackoff(long, long, TimeUnit, double)
   */
  public Duration getDelay() {
    return delay;
  }

  /**
   * Returns the delay factor for backoff retries.
   * 
   * @see #withBackoff(long, long, TimeUnit, double)
   */
  public double getDelayFactor() {
    return delayFactor;
  }

  /**
   * Returns the jitter, else {@code null} if none has been configured.
   * 
   * @see #withJitter(long, TimeUnit)
   */
  public Duration getJitter() {
    return jitter;
  }

  /**
   * Returns the jitter factor, else {@code 0.0} is none has been configured.
   * 
   * @see #withJitter(double)
   */
  public double getJitterFactor() {
    return jitterFactor;
  }

  /**
   * Returns the max delay between backoff retries.
   * 
   * @see #withBackoff(long, long, TimeUnit)
   */
  public Duration getMaxDelay() {
    return maxDelay;
  }

  /**
   * Returns the max duration to perform retries for.
   * 
   * @see #withMaxDuration(long, TimeUnit)
   */
  public Duration getMaxDuration() {
    return maxDuration;
  }

  /**
   * Returns the max retries. Defaults to {@code 100}, which retries forever.
   * 
   * @see #withMaxRetries(int)
   */
  public int getMaxRetries() {
    return maxRetries;
  }

  /**
   * Specifies that a retry should occur if the {@code completionPredicate} matches the completion result and the retry
   * policy is not exceeded.
   * 
   * @throws NullPointerException if {@code completionPredicate} is null
   */
  @SuppressWarnings("unchecked")
  public <T> RetryPolicy retryIf(BiPredicate<T, ? extends Throwable> completionPredicate) {
    Assert.notNull(completionPredicate, "completionPredicate");
    failuresChecked = true;
    retryConditions.add((BiPredicate<Object, Throwable>) completionPredicate);
    return this;
  }

  /**
   * Specifies that a retry should occur if the {@code resultPredicate} matches the result and the retry policy is not
   * exceeded.
   * 
   * @throws NullPointerException if {@code resultPredicate} is null
   */
  public <T> RetryPolicy retryIf(Predicate<T> resultPredicate) {
    Assert.notNull(resultPredicate, "resultPredicate");
    retryConditions.add(Predicates.resultPredicateFor(resultPredicate));
    return this;
  }

  /**
   * Specifies the failure to retry on. Any failure that is assignable from the {@code failure} will be retried.
   * 
   * @throws NullPointerException if {@code failure} is null
   */
  public RetryPolicy retryOn(Class<? extends Throwable> failure) {
    Assert.notNull(failure, "failure");
    return retryOn(Arrays.asList(failure));
  }
  
  /**
   * Specifies the failures to retry on. Any failure that is assignable from the {@code failures} will be retried.
   * 
   * @throws NullPointerException if {@code failures} is null
   * @throws IllegalArgumentException if failures is empty
   */
  @SuppressWarnings("unchecked")
  public RetryPolicy retryOn(Class<? extends Throwable>... failures) {
    Assert.notNull(failures, "failures");
    Assert.isTrue(failures.length > 0, "Failures cannot be empty");
    return retryOn(Arrays.asList(failures));
  }

  /**
   * Specifies the failures to retry on. Any failure that is assignable from the {@code failures} will be retried.
   * 
   * @throws NullPointerException if {@code failures} is null
   * @throws IllegalArgumentException if failures is null or empty
   */
  public RetryPolicy retryOn(List<Class<? extends Throwable>> failures) {
    Assert.notNull(failures, "failures");
    Assert.isTrue(!failures.isEmpty(), "failures cannot be empty");
    failuresChecked = true;
    retryConditions.add(Predicates.failurePredicateFor(failures));
    return this;
  }

  /**
   * Specifies that a retry should occur if the {@code failurePredicate} matches the failure and the retry policy is not
   * exceeded.
   * 
   * @throws NullPointerException if {@code failurePredicate} is null
   */
  public RetryPolicy retryOn(Predicate<? extends Throwable> failurePredicate) {
    Assert.notNull(failurePredicate, "failurePredicate");
    failuresChecked = true;
    retryConditions.add(Predicates.failurePredicateFor(failurePredicate));
    return this;
  }

  /**
   * Specifies that a retry should occur if the execution result matches the {@code result} and the retry policy is not
   * exceeded.
   */
  public RetryPolicy retryWhen(Object result) {
    retryConditions.add(Predicates.resultPredicateFor(result));
    return this;
  }

  /**
   * Sets the {@code delay} between retries, exponentially backing off to the {@code maxDelay} and multiplying
   * successive delays by a factor of 2.
   * 
   * @throws NullPointerException if {@code timeUnit} is null
   * @throws IllegalStateException if {@code delay} is >= the {@link RetryPolicy#withMaxDuration(long, TimeUnit)
   *           maxDuration}
   * @throws IllegalArgumentException if {@code delay} is <= 0 or {@code delay} is >= {@code maxDelay}
   */
  public RetryPolicy withBackoff(long delay, long maxDelay, TimeUnit timeUnit) {
    return withBackoff(delay, maxDelay, timeUnit, 2);
  }

  /**
   * Sets the {@code delay} between retries, exponentially backing off to the {@code maxDelay} and multiplying
   * successive delays by the {@code delayFactor}.
   * 
   * @throws NullPointerException if {@code timeUnit} is null
   * @throws IllegalStateException if {@code delay} is >= the {@link RetryPolicy#withMaxDuration(long, TimeUnit)
   *           maxDuration}
   * @throws IllegalArgumentException if {@code delay} <= 0, {@code delay} is >= {@code maxDelay}, or the
   *           {@code delayFactor} is <= 1
   */
  public RetryPolicy withBackoff(long delay, long maxDelay, TimeUnit timeUnit, double delayFactor) {
    Assert.notNull(timeUnit, "timeUnit");
    Assert.isTrue(timeUnit.toNanos(delay) > 0, "The delay must be greater than 0");
    Assert.state(maxDuration == null || timeUnit.toNanos(delay) < maxDuration.toNanos(),
        "delay must be less than the maxDuration");
    Assert.isTrue(timeUnit.toNanos(delay) < timeUnit.toNanos(maxDelay), "delay must be less than the maxDelay");
    Assert.isTrue(delayFactor > 1, "delayFactor must be greater than 1");
    this.delay = new Duration(delay, timeUnit);
    this.maxDelay = new Duration(maxDelay, timeUnit);
    this.delayFactor = delayFactor;
    return this;
  }

  /**
   * Sets the {@code delay} between retries.
   * 
   * @throws NullPointerException if {@code timeUnit} is null
   * @throws IllegalArgumentException if {@code delay} <= 0
   * @throws IllegalStateException if {@code delay} is >= the {@link RetryPolicy#withMaxDuration(long, TimeUnit)
   *           maxDuration}
   */
  public RetryPolicy withDelay(long delay, TimeUnit timeUnit) {
    Assert.notNull(timeUnit, "timeUnit");
    Assert.isTrue(timeUnit.toNanos(delay) > 0, "delay must be greater than 0");
    Assert.state(maxDuration == null || timeUnit.toNanos(delay) < maxDuration.toNanos(),
        "delay must be less than the maxDuration");
    Assert.state(maxDelay == null, "Backoff delays have already been set");
    this.delay = new Duration(delay, timeUnit);
    return this;
  }

  /**
   * Sets the {@code jitterFactor} to randomly vary retry delays by. For each retry delay, a random portion of the delay
   * multiplied by the {@code jitterFactor} will be added or subtracted to the delay. For example: a retry delay of
   * {@code 100} milliseconds and a {@code jitterFactor} of {@code .25} will result in a random retry delay between
   * {@code 75} and {@code 125} milliseconds.
   * <p>
   * Jitter should be combined with {@link #withDelay(long, TimeUnit) fixed} or
   * {@link #withBackoff(long, long, TimeUnit) exponential backoff} delays.
   * 
   * @throws IllegalArgumentException if {@code duration} is <= 0 or > 1
   * @throws IllegalStateException if no delay has been configured or {@link #withJitter(long, TimeUnit)} has already
   *           been called
   */
  public RetryPolicy withJitter(double jitterFactor) {
    Assert.isTrue(jitterFactor > 0 && jitterFactor <= 1, "jitterFactor must be > 0 and <= 1");
    Assert.state(delay != null, "A fixed or exponential backoff delay must be configured");
    Assert.state(jitter == null, "withJitter(long, timeUnit) has already been called");
    this.jitterFactor = jitterFactor;
    return this;
  }

  /**
   * Sets the {@code jitter} to randomly vary retry delays by. For each retry delay, a random portion of the
   * {@code jitter} will be added or subtracted to the delay. For example: a {@code jitter} of {@code 100} milliseconds
   * will randomly add between {@code -100} and {@code 100} milliseconds to each retry delay.
   * <p>
   * Jitter should be combined with {@link #withDelay(long, TimeUnit) fixed} or
   * {@link #withBackoff(long, long, TimeUnit) exponential backoff} delays.
   * 
   * @throws NullPointerException if {@code timeUnit} is null
   * @throws IllegalArgumentException if {@code jitter} is <= 0
   * @throws IllegalStateException if no delay has been configured or {@link #withJitter(double)} has already been called
   */
  public RetryPolicy withJitter(long jitter, TimeUnit timeUnit) {
    Assert.notNull(timeUnit, "timeUnit");
    Assert.isTrue(jitter > 0, "jitter must be > 0");
    Assert.state(delay != null, "A fixed or exponential backoff delay must be configured");
    Assert.state(jitterFactor == 0.0, "withJitter(long) has already been called");
    this.jitter = new Duration(jitter, timeUnit);
    return this;
  }

  /**
   * Sets the max duration to perform retries for, else the execution will be failed.
   * 
   * @throws NullPointerException if {@code timeUnit} is null
   * @throws IllegalStateException if {@code maxDuration} is <= the {@link RetryPolicy#withDelay(long, TimeUnit) delay}
   */
  public RetryPolicy withMaxDuration(long maxDuration, TimeUnit timeUnit) {
    Assert.notNull(timeUnit, "timeUnit");
    Assert.state(timeUnit.toNanos(maxDuration) > delay.toNanos(), "maxDuration must be greater than the delay");
    this.maxDuration = new Duration(maxDuration, timeUnit);
    return this;
  }

  /**
   * Sets the max number of retries to perform. {@code -1} indicates to retry forever.
   * 
   * @throws IllegalArgumentException if {@code maxRetries} < -1
   */
  public RetryPolicy withMaxRetries(int maxRetries) {
    Assert.isTrue(maxRetries >= -1, "maxRetries must be greater than or equal to -1");
    this.maxRetries = maxRetries;
    return this;
  }
}
