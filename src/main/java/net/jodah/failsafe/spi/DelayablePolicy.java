package net.jodah.failsafe.spi;

import net.jodah.failsafe.DelayablePolicyConfig;
import net.jodah.failsafe.ExecutionContext;
import net.jodah.failsafe.function.DelayFunction;

import java.time.Duration;

/**
 * A policy that can be delayed between executions.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public interface DelayablePolicy<R> {
  DelayablePolicyConfig<R> getConfig();

  /**
   * Returns a computed delay for the {@code result} and {@code context} else {@code null} if no delay function is
   * configured or the computed delay is invalid.
   */
  @SuppressWarnings("unchecked")
  default Duration computeDelay(ExecutionContext<R> context) {
    DelayablePolicyConfig<R> config = getConfig();
    Duration computed = null;
    if (context != null && config.getDelayFn() != null) {
      R exResult = context.getLastResult();
      Throwable exFailure = context.getLastFailure();

      R delayResult = config.getDelayResult();
      Class<? extends Throwable> delayFailure = config.getDelayFailure();
      boolean delayResultMatched = delayResult == null || delayResult.equals(exResult);
      boolean delayFailureMatched =
        delayFailure == null || (exFailure != null && delayFailure.isAssignableFrom(exFailure.getClass()));
      if (delayResultMatched && delayFailureMatched) {
        computed = ((DelayFunction<R, Throwable>) config.getDelayFn()).computeDelay(exResult, exFailure, context);
      }
    }

    return computed != null && !computed.isNegative() ? computed : null;
  }
}
