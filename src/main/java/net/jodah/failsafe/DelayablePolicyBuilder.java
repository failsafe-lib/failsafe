package net.jodah.failsafe;

import net.jodah.failsafe.function.DelayFunction;
import net.jodah.failsafe.internal.util.Assert;

import java.time.Duration;

/**
 * A builder of policies that can be delayed between executions.
 *
 * @param <S> self type
 * @param <C> config type
 * @param <R> result type
 * @author Jonathan Halterman
 */
public abstract class DelayablePolicyBuilder<S, C extends DelayablePolicyConfig<R>, R>
  extends FailurePolicyBuilder<S, C, R> {
  protected DelayablePolicyBuilder(C config) {
    super(config);
  }

  /**
   * Sets the {@code delay} to occur between execution attempts.
   *
   * @throws NullPointerException if {@code delay} is null
   * @throws IllegalArgumentException if {@code delay} <= 0
   */
  @SuppressWarnings("unchecked")
  public S withDelay(Duration delay) {
    Assert.notNull(delay, "delay");
    Assert.isTrue(delay.toNanos() > 0, "delay must be greater than 0");
    config.delay = delay;
    return (S) this;
  }

  /**
   * Sets the {@code delayFunction} that computes the next delay before allowing another execution.
   *
   * @param delayFunction the function to use to compute the delay before a next attempt
   * @throws NullPointerException if {@code delayFunction} is null
   * @see DelayFunction
   */
  @SuppressWarnings("unchecked")
  public S withDelay(DelayFunction<R, ? extends Throwable> delayFunction) {
    Assert.notNull(delayFunction, "delayFunction");
    config.delayFn = delayFunction;
    return (S) this;
  }

  /**
   * Sets the {@code delayFunction} that computes the next delay before allowing another execution. Delays will only
   * occur for failures that are assignable from the {@code failure}.
   *
   * @param delayFunction the function to use to compute the delay before a next attempt
   * @param failure the execution failure that is expected in order to trigger the delay
   * @param <F> failure type
   * @throws NullPointerException if {@code delayFunction} or {@code failure} are null
   * @see DelayFunction
   */
  @SuppressWarnings("unchecked")
  public <F extends Throwable> S withDelayOn(DelayFunction<R, F> delayFunction, Class<F> failure) {
    withDelay(delayFunction);
    Assert.notNull(failure, "failure");
    config.delayFailure = failure;
    return (S) this;
  }

  /**
   * Sets the {@code delayFunction} that computes the next delay before allowing another execution. Delays will only
   * occur for results that equal the {@code result}.
   *
   * @param delayFunction the function to use to compute the delay before a next attempt
   * @param result the execution result that is expected in order to trigger the delay
   * @throws NullPointerException if {@code delayFunction} or {@code result} are null
   * @see DelayFunction
   */
  @SuppressWarnings("unchecked")
  public S withDelayWhen(DelayFunction<R, ? extends Throwable> delayFunction, R result) {
    withDelay(delayFunction);
    Assert.notNull(result, "result");
    config.delayResult = result;
    return (S) this;
  }
}
