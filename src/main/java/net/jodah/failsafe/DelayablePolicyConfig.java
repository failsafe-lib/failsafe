package net.jodah.failsafe;

import net.jodah.failsafe.function.DelayFunction;

import java.time.Duration;

/**
 * Configuration for policies that can delay between executions.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public abstract class DelayablePolicyConfig<R> extends FailurePolicyConfig<R> {
  Duration delay;
  R delayResult;
  Class<? extends Throwable> delayFailure;
  DelayFunction<R, ? extends Throwable> delayFn;

  protected DelayablePolicyConfig() {
  }

  protected DelayablePolicyConfig(DelayablePolicyConfig<R> config) {
    super(config);
    delay = config.delay;
    delayResult = config.delayResult;
    delayFailure = config.delayFailure;
    delayFn = config.delayFn;
  }

  /**
   * Returns the delay until the next execution attempt can be performed.
   *
   * @see DelayablePolicyBuilder#withDelay(Duration)
   */
  public Duration getDelay() {
    return delay;
  }

  /**
   * Returns the result that must be matched in order to delay using the {@link #getDelayFn()}.
   *
   * @see DelayablePolicyBuilder#withDelayWhen(DelayFunction, Object)
   */
  public R getDelayResult() {
    return delayResult;
  }

  /**
   * Returns the Throwable that must be matched in order to delay using the {@link #getDelayFn()}.
   *
   * @see DelayablePolicyBuilder#withDelayOn(DelayFunction, Class)
   */
  public Class<? extends Throwable> getDelayFailure() {
    return delayFailure;
  }

  /**
   * Returns the function that determines the next delay before another execution can be performed.
   *
   * @see DelayablePolicyBuilder#withDelay(DelayFunction)
   * @see DelayablePolicyBuilder#withDelayOn(DelayFunction, Class)
   * @see DelayablePolicyBuilder#withDelayWhen(DelayFunction, Object)
   */
  public DelayFunction<R, ? extends Throwable> getDelayFn() {
    return delayFn;
  }
}
