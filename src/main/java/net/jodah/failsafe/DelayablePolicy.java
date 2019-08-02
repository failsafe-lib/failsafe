package net.jodah.failsafe;

import net.jodah.failsafe.function.DelayFunction;
import net.jodah.failsafe.internal.util.Assert;

import java.time.Duration;

/**
 * A policy that can be delayed between executions.
 *
 * @param <S> self type
 * @param <R> result type
 * @author Jonathan Halterman
 */
public abstract class DelayablePolicy<S, R> extends FailurePolicy<S, R> {
  protected DelayFunction<R, ? extends Throwable> delayFn;
  protected Object delayResult;
  protected Class<? extends Throwable> delayFailure;

  /**
   * Returns the function that determines the next delay before allowing another execution.
   *
   * @see #withDelay(DelayFunction)
   * @see #withDelayOn(DelayFunction, Class)
   * @see #withDelayWhen(DelayFunction, Object)
   */
  public DelayFunction<R, ? extends Throwable> getDelayFn() {
    return delayFn;
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
    this.delayFn = delayFunction;
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
    this.delayFailure = failure;
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
    this.delayResult = result;
    return (S) this;
  }

  /**
   * Returns a computed delay for the {@code result} and {@code context} else {@code null} if no delay function is
   * configured or the computed delay is invalid.
   */
  @SuppressWarnings("unchecked")
  public Duration computeDelay(ExecutionContext context) {
    Duration computed = null;
    if (context != null && delayFn != null) {
      Object exResult = context.getLastResult();
      Throwable exFailure = context.getLastFailure();

      if ((delayResult == null || delayResult.equals(exResult)) && (delayFailure == null || (exFailure != null
        && delayFailure.isAssignableFrom(exFailure.getClass())))) {
        computed = ((DelayFunction<Object, Throwable>) delayFn).computeDelay(exResult, exFailure, context);
      }
    }

    return computed != null && !computed.isNegative() ? computed : null;
  }
}
