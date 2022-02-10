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
package dev.failsafe;

import dev.failsafe.event.EventListener;
import dev.failsafe.event.ExecutionAttemptedEvent;
import dev.failsafe.event.ExecutionCompletedEvent;
import dev.failsafe.event.ExecutionScheduledEvent;
import dev.failsafe.function.CheckedBiPredicate;
import dev.failsafe.function.CheckedPredicate;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for a {@link RetryPolicy}.
 * <p>
 * This class is threadsafe.
 * </p>
 *
 * @param <R> result type
 * @author Jonathan Halterman
 * @see RetryPolicyBuilder
 */
public class RetryPolicyConfig<R> extends DelayablePolicyConfig<R> {
  Duration delayMin;
  Duration delayMax;
  double delayFactor;
  Duration maxDelay;
  Duration jitter;
  double jitterFactor;
  Duration maxDuration;
  int maxRetries;
  List<CheckedBiPredicate<R, Throwable>> abortConditions;

  // Listeners
  EventListener<ExecutionCompletedEvent<R>> abortListener;
  EventListener<ExecutionAttemptedEvent<R>> failedAttemptListener;
  EventListener<ExecutionCompletedEvent<R>> retriesExceededListener;
  EventListener<ExecutionAttemptedEvent<R>> retryListener;
  EventListener<ExecutionScheduledEvent<R>> retryScheduledListener;

  RetryPolicyConfig() {
  }

  RetryPolicyConfig(RetryPolicyConfig<R> config) {
    super(config);
    delayMin = config.delayMin;
    delayMax = config.delayMax;
    delayFactor = config.delayFactor;
    maxDelay = config.maxDelay;
    jitter = config.jitter;
    jitterFactor = config.jitterFactor;
    maxDuration = config.maxDuration;
    maxRetries = config.maxRetries;
    abortConditions = new ArrayList<>(config.abortConditions);
    abortListener = config.abortListener;
    failedAttemptListener = config.failedAttemptListener;
    retriesExceededListener = config.retriesExceededListener;
    retryListener = config.retryListener;
    retryScheduledListener = config.retryScheduledListener;
  }

  /**
   * Returns whether the policy config allows retries according to the configured {@link
   * RetryPolicyConfig#getMaxRetries() maxRetries} and {@link RetryPolicyConfig#getMaxDuration() maxDuration}.
   *
   * @see RetryPolicyBuilder#withMaxRetries(int)
   * @see RetryPolicyBuilder#withMaxDuration(Duration)
   */
  public boolean allowsRetries() {
    int maxRetries = getMaxRetries();
    Duration maxDuration = getMaxDuration();
    return (maxRetries == -1 || maxRetries > 0) && (maxDuration == null || maxDuration.toNanos() > 0);
  }

  /**
   * Returns the conditions for which an execution result or exception will cause retries to be aborted.
   *
   * @see RetryPolicyBuilder#abortOn(Class...)
   * @see RetryPolicyBuilder#abortOn(List)
   * @see RetryPolicyBuilder#abortOn(CheckedPredicate)
   * @see RetryPolicyBuilder#abortIf(CheckedBiPredicate)
   * @see RetryPolicyBuilder#abortIf(CheckedPredicate) 
   * @see RetryPolicyBuilder#abortWhen(Object) 
   */
  public List<CheckedBiPredicate<R, Throwable>> getAbortConditions() {
    return abortConditions;
  }

  /**
   * Returns the delay between retries. Defaults to {@link Duration#ZERO}.
   *
   * @see RetryPolicyBuilder#withDelay(Duration)
   * @see RetryPolicyBuilder#withBackoff(Duration, Duration, double)
   * @see RetryPolicyBuilder#withBackoff(Duration, Duration, double)
   * @see RetryPolicyBuilder#withBackoff(long, long, ChronoUnit)
   * @see RetryPolicyBuilder#withBackoff(long, long, ChronoUnit, double)
   */
  public Duration getDelay() {
    return super.getDelay();
  }

  /**
   * Returns the min delay between retries.
   *
   * @see RetryPolicyBuilder#withDelay(Duration, Duration)
   * @see RetryPolicyBuilder#withDelay(long, long, ChronoUnit)
   */
  public Duration getDelayMin() {
    return delayMin;
  }

  /**
   * Returns the max delay between retries.
   *
   * @see RetryPolicyBuilder#withDelay(Duration, Duration)
   * @see RetryPolicyBuilder#withDelay(long, long, ChronoUnit)
   */
  public Duration getDelayMax() {
    return delayMax;
  }

  /**
   * Returns the delay factor for backoff retries.
   *
   * @see RetryPolicyBuilder#withBackoff(Duration, Duration, double)
   * @see RetryPolicyBuilder#withBackoff(long, long, ChronoUnit, double)
   */
  public double getDelayFactor() {
    return delayFactor;
  }

  /**
   * Returns the jitter, else {@code null} if none has been configured.
   *
   * @see RetryPolicyBuilder#withJitter(Duration)
   */
  public Duration getJitter() {
    return jitter;
  }

  /**
   * Returns the jitter factor, else {@code 0.0} if none has been configured.
   *
   * @see RetryPolicyBuilder#withJitter(double)
   */
  public double getJitterFactor() {
    return jitterFactor;
  }

  /**
   * Returns the max number of execution attempts to perform. A value of {@code -1} represents no limit. Defaults to
   * {@code 3}.
   *
   * @see RetryPolicyBuilder#withMaxAttempts(int)
   * @see #getMaxRetries()
   */
  public int getMaxAttempts() {
    return maxRetries == -1 ? -1 : maxRetries + 1;
  }

  /**
   * Returns the max delay between backoff retries.
   *
   * @see RetryPolicyBuilder#withBackoff(Duration, Duration)
   * @see RetryPolicyBuilder#withBackoff(Duration, Duration, double)
   * @see RetryPolicyBuilder#withBackoff(long, long, ChronoUnit)
   * @see RetryPolicyBuilder#withBackoff(long, long, ChronoUnit, double)
   */
  public Duration getMaxDelay() {
    return maxDelay;
  }

  /**
   * Returns the max duration to perform retries for.
   *
   * @see RetryPolicyBuilder#withMaxDuration(Duration)
   */
  public Duration getMaxDuration() {
    return maxDuration;
  }

  /**
   * Returns the max number of retries to perform when an execution attempt fails. A value of {@code -1} represents no
   * limit. Defaults to {@code 2}.
   *
   * @see RetryPolicyBuilder#withMaxRetries(int)
   * @see #getMaxAttempts()
   */
  public int getMaxRetries() {
    return maxRetries;
  }

  /**
   * Returns the abort event listener.
   *
   * @see RetryPolicyBuilder#onAbort(EventListener)
   */
  public EventListener<ExecutionCompletedEvent<R>> getAbortListener() {
    return abortListener;
  }

  /**
   * Returns the failed attempt event listener.
   *
   * @see RetryPolicyBuilder#onFailedAttempt(EventListener)
   */
  public EventListener<ExecutionAttemptedEvent<R>> getFailedAttemptListener() {
    return failedAttemptListener;
  }

  /**
   * Returns the retries exceeded event listener.
   *
   * @see RetryPolicyBuilder#onRetriesExceeded(EventListener)
   */
  public EventListener<ExecutionCompletedEvent<R>> getRetriesExceededListener() {
    return retriesExceededListener;
  }

  /**
   * Returns the retry event listener.
   *
   * @see RetryPolicyBuilder#onRetry(EventListener)
   */
  public EventListener<ExecutionAttemptedEvent<R>> getRetryListener() {
    return retryListener;
  }

  /**
   * Returns the retry scheduled event listener.
   *
   * @see RetryPolicyBuilder#onRetryScheduled(EventListener)
   */
  public EventListener<ExecutionScheduledEvent<R>> getRetryScheduledListener() {
    return retryScheduledListener;
  }
}
