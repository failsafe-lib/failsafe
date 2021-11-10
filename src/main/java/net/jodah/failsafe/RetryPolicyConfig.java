package net.jodah.failsafe;

import net.jodah.failsafe.event.EventListener;
import net.jodah.failsafe.event.ExecutionAttemptedEvent;
import net.jodah.failsafe.event.ExecutionCompletedEvent;
import net.jodah.failsafe.event.ExecutionScheduledEvent;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Configuration for a {@link RetryPolicy}.
 * <p>
 * This class is threadsafe.
 * </p>
 *
 * @param <R> result type
 * @author Jonathan Halterman
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
  List<BiPredicate<R, Throwable>> abortConditions;

  // Listeners
  volatile EventListener<ExecutionCompletedEvent<R>> abortListener;
  volatile EventListener<ExecutionAttemptedEvent<R>> failedAttemptListener;
  volatile EventListener<ExecutionCompletedEvent<R>> retriesExceededListener;
  volatile EventListener<ExecutionAttemptedEvent<R>> retryListener;
  volatile EventListener<ExecutionScheduledEvent<R>> retryScheduledListener;

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
   * Returns the conditions for which an executionr esult or failure will cause retries to be aborted.
   *
   * @see RetryPolicyBuilder#abortOn(Class...)
   * @see RetryPolicyBuilder#abortOn(List)
   * @see RetryPolicyBuilder#abortOn(Predicate)
   * @see RetryPolicyBuilder#abortIf(BiPredicate)
   * @see RetryPolicyBuilder#abortIf(Predicate)
   * @see RetryPolicyBuilder#abortWhen(R)
   */
  public List<BiPredicate<R, Throwable>> getAbortConditions() {
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
   * @see RetryPolicyListeners#onAbort(EventListener)
   */
  public EventListener<ExecutionCompletedEvent<R>> getAbortListener() {
    return abortListener;
  }

  /**
   * Returns the failed attempt event listener.
   *
   * @see RetryPolicyListeners#onFailedAttempt(EventListener)
   */
  public EventListener<ExecutionAttemptedEvent<R>> getFailedAttemptListener() {
    return failedAttemptListener;
  }

  /**
   * Returns the retries exceeded event listener.
   *
   * @see RetryPolicyListeners#onRetriesExceeded(EventListener)
   */
  public EventListener<ExecutionCompletedEvent<R>> getRetriesExceededListener() {
    return retriesExceededListener;
  }

  /**
   * Returns the retry event listener.
   *
   * @see RetryPolicyListeners#onRetry(EventListener)
   */
  public EventListener<ExecutionAttemptedEvent<R>> getRetryListener() {
    return retryListener;
  }

  /**
   * Returns the retry scheduled event listener.
   *
   * @see RetryPolicyListeners#onRetryScheduled(EventListener)
   */
  public EventListener<ExecutionScheduledEvent<R>> getRetryScheduledListener() {
    return retryScheduledListener;
  }
}
