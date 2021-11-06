package net.jodah.failsafe;

import net.jodah.failsafe.function.ContextualSupplier;

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
  ContextualSupplier<R, Duration> delayFn;

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
   * Returns the function that determines the next delay before another execution can be performed.
   *
   * @see DelayablePolicyBuilder#withDelayFn(ContextualSupplier)
   * @see DelayablePolicyBuilder#withDelayFnOn(ContextualSupplier, Class)
   * @see DelayablePolicyBuilder#withDelayFnWhen(ContextualSupplier, Object)
   */
  public ContextualSupplier<R, Duration> getDelayFn() {
    return delayFn;
  }

  /**
   * Returns the Throwable that must be matched in order to delay using the {@link #getDelayFn()}.
   *
   * @see DelayablePolicyBuilder#withDelayFnOn(ContextualSupplier, Class)
   */
  public Class<? extends Throwable> getDelayFailure() {
    return delayFailure;
  }

  /**
   * Returns the result that must be matched in order to delay using the {@link #getDelayFn()}.
   *
   * @see DelayablePolicyBuilder#withDelayFnWhen(ContextualSupplier, Object)
   */
  public R getDelayResult() {
    return delayResult;
  }
}
