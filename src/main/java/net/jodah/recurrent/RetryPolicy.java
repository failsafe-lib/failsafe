package net.jodah.recurrent;

import java.util.concurrent.TimeUnit;

import net.jodah.recurrent.internal.util.Assert;
import net.jodah.recurrent.util.Duration;

/**
 * Policy that defines how retries should be performed.
 * 
 * @author Jonathan Halterman
 */
public final class RetryPolicy {
  private Duration delay;
  private double delayMultiplier;
  private Duration maxDelay;
  private Duration maxDuration;
  private int maxRetries;

  /**
   * Creates a retry policy that retries forever with no delay between retries.
   */
  public RetryPolicy() {
    delay = Duration.NONE;
    maxRetries = -1;
  }

  /**
   * Returns whether the policy allows any retries based on the configured maxRetries and maxDuration.
   */
  public boolean allowsRetries() {
    return (maxRetries == -1 || maxRetries > 0) && (maxDuration == null || maxDuration.length > 0);
  }

  /**
   * Returns the delay between retries. Defaults to {@link Duration#NONE}.
   * 
   * @see #withDelay(Duration)
   * @see #withBackoff(Duration, Duration)
   * @see #withBackoff(Duration, Duration, int)
   */
  public Duration getDelay() {
    return delay;
  }

  /**
   * Returns the delay multiplier for backoff retries.
   * 
   * @see #withBackoff(Duration, Duration, int)
   */
  public double getDelayMultiplier() {
    return delayMultiplier;
  }

  /**
   * Returns the max delay between backoff retries.
   * 
   * @see #withBackoff(Duration, Duration)
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
   * Returns the max retries. Defaults to -1, which retries forever.
   * 
   * @see #withMaxRetries(int)
   */
  public int getMaxRetries() {
    return maxRetries;
  }

  /**
   * Sets the {@code delay} between retries, exponentially backing of to the {@code maxDelay} and multiplying successive
   * delays by a factor of 2.
   * 
   * @throws NullPointerException if {@code delay} or {@code maxDelay} are null
   * @throws IllegalArgumentException if {@code delay} is <= 0 or {@code delay} is >= {@code maxDelay}
   */
  public RetryPolicy withBackoff(long delay, long maxDelay, TimeUnit timeUnit) {
    return withBackoff(delay, maxDelay, timeUnit, 2);
  }

  /**
   * Sets the {@code delay} between retries, exponentially backing of to the {@code maxDelay} and multiplying successive
   * delays by the {@code delayMultiplier}.
   * 
   * @throws NullPointerException if {@code delay} or {@code maxDelay} are null
   * @throws IllegalStateException if {@code delay} is >= the maxDuration
   * @throws IllegalArgumentException if {@code delay} <= 0, {@code delay} is >= {@code maxDelay}, or the
   *           {@code delayMultiplier} is <= 1
   */
  public RetryPolicy withBackoff(long delay, long maxDelay, TimeUnit timeUnit, double delayMultiplier) {
    Assert.notNull(delay, "delay");
    Assert.notNull(maxDelay, "maxDelay");
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
   * @throws NullPointerException if {@code delay} is null
   * @throws IllegalArgumentException if {@code delay} <= 0
   * @throws IllegalStateException if {@code delay} is >= the maxDuration, or backoff delays have already been set via
   *           {@link #withBackoff(Duration, Duration)} or {@link #withBackoff(Duration, Duration, int)}
   */
  public RetryPolicy withDelay(long delay, TimeUnit timeUnit) {
    Assert.notNull(delay, "delay");
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
   * @throws NullPointerException if {@code maxDuration} is null
   * @throws IllegalStateException if {@code maxDuration} is <= the delay
   */
  public RetryPolicy withMaxDuration(long maxDuration, TimeUnit timeUnit) {
    Assert.notNull(maxDuration, "maxDuration");
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
