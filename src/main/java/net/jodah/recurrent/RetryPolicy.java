package net.jodah.recurrent;

import java.util.concurrent.TimeUnit;

import net.jodah.recurrent.internal.util.Assert;
import net.jodah.recurrent.util.BiPredicate;
import net.jodah.recurrent.util.Duration;
import net.jodah.recurrent.util.Predicate;

/**
 * A policy that defines when retries should be performed.
 * 
 * <p>
 * The {@code retryOn} methods describe when a retry should be performed for a particular failure. The {@code retryWhen}
 * methods describe when a retry should be performed for a particular result. If multiple {@code retryOn} or
 * {@code retryWhen} conditions are specified, any matching condition can allow a retry.
 * 
 * @author Jonathan Halterman
 */
public final class RetryPolicy {
  private static final Object DEFAULT_RESULT_VALUE = new Object();

  private Duration delay;
  private double delayMultiplier;
  private Duration maxDelay;
  private Duration maxDuration;
  private int maxRetries;
  private Class<? extends Throwable>[] failureTypes;
  private Predicate<Throwable> failurePredicate;
  private Object resultValue = DEFAULT_RESULT_VALUE;
  private Predicate<Object> resultPredicate;
  private BiPredicate<Object, Throwable> completionPredicate;

  /**
   * Creates a retry policy that retries forever with no delay between retries.
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
    this.failureTypes = rp.failureTypes;
    this.resultValue = rp.resultValue;
    this.failurePredicate = rp.failurePredicate;
    this.resultPredicate = rp.resultPredicate;
    this.completionPredicate = rp.completionPredicate;
  }

  /**
   * Returns whether the policy allows any retries based on the configured maxRetries and maxDuration.
   */
  public boolean allowsRetries() {
    return (maxRetries == -1 || maxRetries > 0) && (maxDuration == null || maxDuration.length > 0);
  }

  /**
   * Returns whether the policy will allow retries for the {@code failure}.
   */
  public boolean allowsRetriesFor(Object result, Throwable failure) {
    if (!allowsRetries())
      return false;

    // Check completion condition
    if (completionPredicate != null && completionPredicate.test(result, failure))
      return true;

    // Check failure condition(s)
    if (failure != null) {
      if (failurePredicate != null && failurePredicate.test(failure))
        return true;
      if (failureTypes != null)
        for (Class<? extends Throwable> failureType : failureTypes)
          if (failure.getClass().isAssignableFrom(failureType))
            return true;

      // Retry if failure was not checked
      return completionPredicate == null && failurePredicate == null && failureTypes == null;
    }

    // Check result condition(s)
    if (resultPredicate != null && resultPredicate.test(result))
      return true;
    if (!DEFAULT_RESULT_VALUE.equals(resultValue))
      return resultValue == null ? result == null : resultValue.equals(result);

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
   * @see #withBackoff(long, long, TimeUnit, int)
   */
  public Duration getDelay() {
    return delay;
  }

  /**
   * Returns the delay multiplier for backoff retries.
   * 
   * @see #withBackoff(long, long, TimeUnit, int)
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
   * Specifies the failures to retry on. Any failure that is assignable from the {@code failures} will be retried.
   * 
   * @throws NullPointerException if {@code failures} is null
   * @throws IllegalArgumentException if failures is empty
   */
  @SuppressWarnings("unchecked")
  public RetryPolicy retryOn(Class<? extends Throwable>... failures) {
    Assert.notNull(failures, "failures");
    Assert.isTrue(failures.length > 0, "Failures cannot be empty");
    this.failureTypes = failures;
    return this;
  }

  /**
   * Specifies when a retry should occur for a particular failure. If the {@code retryPredicate} returns true then
   * retries may be performed, else the failure will be re-thrown.
   * 
   * @throws NullPointerException if {@code failurePredicate} is null
   */
  @SuppressWarnings("unchecked")
  public RetryPolicy retryOn(Predicate<? extends Throwable> failurePredicate) {
    Assert.notNull(failurePredicate, "failurePredicate");
    this.failurePredicate = (Predicate<Throwable>) failurePredicate;
    return this;
  }

  /**
   * Specifies when a retry should occur for a particular result and failure. If the {@code completionPredicate} returns
   * true then retries may be performed, else the failure will be re-thrown or the result returned.
   * 
   * @throws NullPointerException if {@code completionPredicate} is null
   */
  @SuppressWarnings("unchecked")
  public <T> RetryPolicy retryWhen(BiPredicate<T, ? extends Throwable> completionPredicate) {
    Assert.notNull(completionPredicate, "completionPredicate");
    this.completionPredicate = (BiPredicate<Object, Throwable>) completionPredicate;
    return this;
  }

  /**
   * Specifies when a retry should occur for a particular result. If the {@code resultPredicate} returns true then
   * retries may be performed, else the result will be returned.
   * 
   * @throws NullPointerException if {@code failurePredicate} is null
   */
  @SuppressWarnings("unchecked")
  public <T> RetryPolicy retryWhen(Predicate<T> resultPredicate) {
    Assert.notNull(resultPredicate, "resultPredicate");
    this.resultPredicate = (Predicate<Object>) resultPredicate;
    return this;
  }

  /**
   * Specifies when a retry should occur for a particular result. If the result matches {@code result} then retries may
   * be performed, else the result will be returned.
   */
  public <T> RetryPolicy retryWhen(T result) {
    this.resultValue = result;
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
    Assert.isTrue(this.delay.toNanos() > 0, "The delay must be greater tha 0");
    if (maxDuration != null)
      Assert.state(this.delay.toNanos() < this.maxDuration.toNanos(), "The delay must be less than the maxDuration");
    Assert.isTrue(this.delay.toNanos() < this.maxDelay.toNanos(), "The delay must be less than the maxDelay");
    Assert.isTrue(delayMultiplier > 1, "The delayMultiplier must be greater than 1");
    return this;
  }

  /**
   * Sets the {@code delay} between retries.
   * 
   * @throws NullPointerException if {@code timeUnit} is null
   * @throws IllegalArgumentException if {@code delay} <= 0
   * @throws IllegalStateException if {@code delay} is >= the maxDuration, or backoff delays have already been set via
   *           {@link #withBackoff(Duration, Duration)} or {@link #withBackoff(Duration, Duration, int)}
   */
  public RetryPolicy withDelay(long delay, TimeUnit timeUnit) {
    Assert.notNull(timeUnit, "timeUnit");
    this.delay = new Duration(delay, timeUnit);
    Assert.isTrue(this.delay.toNanos() > 0, "The delay must be greater tha 0");
    if (maxDuration != null)
      Assert.state(this.delay.toNanos() < maxDuration.toNanos(), "The delay must be less than the maxDuration");
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
    Assert.state(this.maxDuration.toNanos() > delay.toNanos(), "The maxDuration must be greater than the delay");
    return this;
  }

  /**
   * Sets the max number of retries to perform. -1 indicates to retry forever.
   * 
   * @throws IllegalArgumentException if {@code maxRetries} < -1
   */
  public RetryPolicy withMaxRetries(int maxRetries) {
    Assert.isTrue(maxRetries >= -1, "The maxRetries must be greater than or equal to -1");
    this.maxRetries = maxRetries;
    return this;
  }
}
