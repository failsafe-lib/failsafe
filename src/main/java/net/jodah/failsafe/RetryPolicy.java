package net.jodah.failsafe;

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
public final class RetryPolicy {
  static final RetryPolicy NEVER = new RetryPolicy().withMaxRetries(0);
  private static final Object DEFAULT_VALUE = new Object();

  private Duration delay;
  private double delayMultiplier;
  private Duration maxDelay;
  private Duration maxDuration;
  private int maxRetries;
  private List<Class<? extends Throwable>> retryableFailures;
  private List<Class<? extends Throwable>> abortableFailures;
  private Predicate<Throwable> retryableFailurePredicate;
  private Predicate<Throwable> abortableFailurePredicate;
  private Object retryableValue = DEFAULT_VALUE;
  private Object abortableValue = DEFAULT_VALUE;
  private Predicate<Object> retryableResultPredicate;
  private Predicate<Object> abortableResultPredicate;
  private BiPredicate<Object, Throwable> retryableCompletionPredicate;
  private BiPredicate<Object, Throwable> abortableCompletionPredicate;

  /**
   * Creates a retry policy that always retries with no delay.
   */
  public RetryPolicy() {
    delay = Duration.NONE;
    maxRetries = -1;
  }

  /**
   * Copy constructor.
   */
  public RetryPolicy(RetryPolicy rp) {
    this.delay = rp.delay;
    this.delayMultiplier = rp.delayMultiplier;
    this.maxDelay = rp.maxDelay;
    this.maxDuration = rp.maxDuration;
    this.maxRetries = rp.maxRetries;
    this.retryableFailures = rp.retryableFailures;
    this.abortableFailures = rp.abortableFailures;
    this.retryableFailurePredicate = rp.retryableFailurePredicate;
    this.abortableFailurePredicate = rp.abortableFailurePredicate;
    this.retryableValue = rp.retryableValue;
    this.abortableValue = rp.abortableValue;
    this.retryableResultPredicate = rp.retryableResultPredicate;
    this.abortableResultPredicate = rp.abortableResultPredicate;
    this.retryableCompletionPredicate = rp.retryableCompletionPredicate;
    this.abortableCompletionPredicate = rp.abortableCompletionPredicate;
  }

  /**
   * Specifies that retries should be aborted if the {@code completionPredicate} matches the completion result.
   * 
   * @throws NullPointerException if {@code completionPredicate} is null
   */
  @SuppressWarnings("unchecked")
  public <T> RetryPolicy abortIf(BiPredicate<T, ? extends Throwable> completionPredicate) {
    Assert.notNull(completionPredicate, "completionPredicate");
    this.abortableCompletionPredicate = (BiPredicate<Object, Throwable>) completionPredicate;
    return this;
  }

  /**
   * Specifies that retries should be aborted if the {@code resultPredicate} matches the result.
   * 
   * @throws NullPointerException if {@code resultPredicate} is null
   */
  @SuppressWarnings("unchecked")
  public <T> RetryPolicy abortIf(Predicate<T> resultPredicate) {
    Assert.notNull(resultPredicate, "resultPredicate");
    this.abortableResultPredicate = (Predicate<Object>) resultPredicate;
    return this;
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
    this.abortableFailures = Arrays.asList(failures);
    return this;
  }

  /**
   * Specifies when retries should be aborted. Any failure that is assignable from the {@code failures} will be result
   * in retries being aborted.
   * 
   * @throws NullPointerException if {@code failures} is null
   * @throws IllegalArgumentException if failures is empty
   */
  public RetryPolicy abortOn(List<Class<? extends Throwable>> failures) {
    Assert.notNull(failures, "failures");
    Assert.isTrue(!failures.isEmpty(), "failures cannot be empty");
    this.abortableFailures = failures;
    return this;
  }

  /**
   * Specifies that retries should be aborted if the {@code failurePredicate} matches the failure.
   * 
   * @throws NullPointerException if {@code failurePredicate} is null
   */
  @SuppressWarnings("unchecked")
  public RetryPolicy abortOn(Predicate<? extends Throwable> failurePredicate) {
    Assert.notNull(failurePredicate, "failurePredicate");
    this.abortableFailurePredicate = (Predicate<Throwable>) failurePredicate;
    return this;
  }

  /**
   * Specifies that retries should be aborted if the execution result matches the {@code result}.
   */
  public RetryPolicy abortWhen(Object result) {
    this.abortableValue = result;
    return this;
  }

  /**
   * Returns whether an execution can be aborted for the {@code result} and {@code failure} according to the policy.
   */
  public boolean canAbortFor(Object result, Throwable failure) {
    // Check completion condition
    if (abortableCompletionPredicate != null && abortableCompletionPredicate.test(result, failure))
      return true;

    // Check failure condition(s)
    if (failure != null) {
      if (abortableFailurePredicate != null && abortableFailurePredicate.test(failure))
        return true;
      if (abortableFailures != null)
        for (Class<? extends Throwable> failureType : abortableFailures)
          if (failureType.isAssignableFrom(failure.getClass()))
            return true;
    }

    // Check result condition(s)
    if (abortableResultPredicate != null && abortableResultPredicate.test(result))
      return true;
    if (!DEFAULT_VALUE.equals(abortableValue) && abortableValue == null ? result == null
        : abortableValue.equals(result))
      return true;

    return false;
  }

  /**
   * Returns whether an execution can be retried according to the configured maxRetries and maxDuration.
   */
  public boolean canRetry() {
    return (maxRetries == -1 || maxRetries > 0) && (maxDuration == null || maxDuration.toNanos() > 0);
  }

  /**
   * Returns whether an execution can be retried for the {@code result} and {@code failure} according to the policy.
   */
  public boolean canRetryFor(Object result, Throwable failure) {
    if (!canRetry())
      return false;

    // Check completion condition
    if (retryableCompletionPredicate != null && retryableCompletionPredicate.test(result, failure))
      return true;

    // Check failure condition(s)
    if (failure != null) {
      if (retryableFailurePredicate != null && retryableFailurePredicate.test(failure))
        return true;
      if (retryableFailures != null)
        for (Class<? extends Throwable> failureType : retryableFailures)
          if (failureType.isAssignableFrom(failure.getClass()))
            return true;

      // Can retry if no configuration
      return retryableCompletionPredicate == null && retryableFailurePredicate == null && retryableFailures == null;
    }

    // Check result condition(s)
    if (retryableResultPredicate != null && retryableResultPredicate.test(result))
      return true;
    if (!DEFAULT_VALUE.equals(retryableValue))
      return retryableValue == null ? result == null : retryableValue.equals(result);

    return false;
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
   * Returns the delay multiplier for backoff retries.
   * 
   * @see #withBackoff(long, long, TimeUnit, double)
   */
  public double getDelayMultiplier() {
    return delayMultiplier;
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
   * Returns the max retries. Defaults to -1, which retries forever.
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
    this.retryableCompletionPredicate = (BiPredicate<Object, Throwable>) completionPredicate;
    return this;
  }

  /**
   * Specifies that a retry should occur if the {@code resultPredicate} matches the result and the retry policy is not
   * exceeded.
   * 
   * @throws NullPointerException if {@code resultPredicate} is null
   */
  @SuppressWarnings("unchecked")
  public <T> RetryPolicy retryIf(Predicate<T> resultPredicate) {
    Assert.notNull(resultPredicate, "resultPredicate");
    this.retryableResultPredicate = (Predicate<Object>) resultPredicate;
    return this;
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
    this.retryableFailures = Arrays.asList(failures);
    return this;
  }

  /**
   * Specifies the failures to retry on. Any failure that is assignable from the {@code failures} will be retried.
   * 
   * @throws NullPointerException if {@code failures} is null
   * @throws IllegalArgumentException if failures is empty
   */
  public RetryPolicy retryOn(List<Class<? extends Throwable>> failures) {
    Assert.notNull(failures, "failures");
    Assert.isTrue(!failures.isEmpty(), "failures cannot be empty");
    this.retryableFailures = failures;
    return this;
  }

  /**
   * Specifies that a retry should occur if the {@code failurePredicate} matches the failure and the retry policy is not
   * exceeded.
   * 
   * @throws NullPointerException if {@code failurePredicate} is null
   */
  @SuppressWarnings("unchecked")
  public RetryPolicy retryOn(Predicate<? extends Throwable> failurePredicate) {
    Assert.notNull(failurePredicate, "failurePredicate");
    this.retryableFailurePredicate = (Predicate<Throwable>) failurePredicate;
    return this;
  }

  /**
   * Specifies that a retry should occur if the execution result matches the {@code result} and the retry policy is not
   * exceeded.
   */
  public RetryPolicy retryWhen(Object result) {
    this.retryableValue = result;
    return this;
  }

  /**
   * Sets the {@code delay} between retries, exponentially backing of to the {@code maxDelay} and multiplying successive
   * delays by a factor of 2.
   * 
   * @throws NullPointerException if {@code timeUnit} is null
   * @throws IllegalArgumentException if {@code delay} is <= 0 or {@code delay} is >= {@code maxDelay}
   */
  public RetryPolicy withBackoff(long delay, long maxDelay, TimeUnit timeUnit) {
    return withBackoff(delay, maxDelay, timeUnit, 2);
  }

  /**
   * Sets the {@code delay} between retries, exponentially backing of to the {@code maxDelay} and multiplying successive
   * delays by the {@code delayMultiplier}.
   * 
   * @throws NullPointerException if {@code timeUnit} is null
   * @throws IllegalStateException if {@code delay} is >= the maxDuration
   * @throws IllegalArgumentException if {@code delay} <= 0, {@code delay} is >= {@code maxDelay}, or the
   *           {@code delayMultiplier} is <= 1
   */
  public RetryPolicy withBackoff(long delay, long maxDelay, TimeUnit timeUnit, double delayMultiplier) {
    Assert.notNull(timeUnit, "timeUnit");
    this.delay = new Duration(delay, timeUnit);
    this.maxDelay = new Duration(maxDelay, timeUnit);
    this.delayMultiplier = delayMultiplier;
    Assert.isTrue(this.delay.toNanos() > 0, "The delay must be greater than 0");
    if (maxDuration != null)
      Assert.state(this.delay.toNanos() < this.maxDuration.toNanos(), "delay must be less than the maxDuration");
    Assert.isTrue(this.delay.toNanos() < this.maxDelay.toNanos(), "delay must be less than the maxDelay");
    Assert.isTrue(delayMultiplier > 1, "delayMultiplier must be greater than 1");
    return this;
  }

  /**
   * Sets the {@code delay} between retries.
   * 
   * @throws NullPointerException if {@code timeUnit} is null
   * @throws IllegalArgumentException if {@code delay} <= 0
   * @throws IllegalStateException if {@code delay} is >= the maxDuration
   */
  public RetryPolicy withDelay(long delay, TimeUnit timeUnit) {
    Assert.notNull(timeUnit, "timeUnit");
    this.delay = new Duration(delay, timeUnit);
    Assert.isTrue(this.delay.toNanos() > 0, "delay must be greater than 0");
    if (maxDuration != null)
      Assert.state(this.delay.toNanos() < maxDuration.toNanos(), "delay must be less than the maxDuration");
    Assert.state(maxDelay == null, "Backoff delays have already been set");
    return this;
  }

  /**
   * Sets the max duration to perform retries for.
   * 
   * @throws NullPointerException if {@code timeUnit} is null
   * @throws IllegalStateException if {@code maxDuration} is <= the delay
   */
  public RetryPolicy withMaxDuration(long maxDuration, TimeUnit timeUnit) {
    Assert.notNull(timeUnit, "timeUnit");
    this.maxDuration = new Duration(maxDuration, timeUnit);
    Assert.state(this.maxDuration.toNanos() > delay.toNanos(), "maxDuration must be greater than the delay");
    return this;
  }

  /**
   * Sets the max number of retries to perform. -1 indicates to retry forever.
   * 
   * @throws IllegalArgumentException if {@code maxRetries} < -1
   */
  public RetryPolicy withMaxRetries(int maxRetries) {
    Assert.isTrue(maxRetries >= -1, "maxRetries must be greater than or equal to -1");
    this.maxRetries = maxRetries;
    return this;
  }
}
