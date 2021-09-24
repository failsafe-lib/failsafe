package net.jodah.failsafe.function;

import net.jodah.failsafe.ExecutionContext;
import net.jodah.failsafe.spi.Policy;

import java.time.Duration;

/**
 * A functional interface for computing delays between {@link Policy} execution attempts.
 *
 * @param <R> result type
 * @param <F> failure type
 */
@FunctionalInterface
public interface DelayFunction<R, F extends Throwable> {
  /**
   * Returns the amount of delay before the next {@link Policy} execution attempt based on the result
   * or failure of the last attempt and the execution context (executions so far). This method must complete quickly,
   * not have side-effects, and always return the same result for the same input. Unchecked exceptions thrown by this
   * method will <strong>not</strong> be treated as part of the fail-safe processing and will instead abort that
   * processing.
   * <p>
   * Notes:
   * <ul>
   * <li>A negative return value will cause Failsafe to use a configured fixed or backoff delay
   * <li>Any configured jitter is still applied to DelayFunction provided values
   * <li>Any configured max duration is still applied to DelayFunction provided values
   * </ul>
   *
   * @param result the result, if any, of the last attempt
   * @param failure the {@link Throwable} thrown, if any, during the last attempt
   * @param context the {@link ExecutionContext} that describes executions so far. May be {@code null} if the execution
   * failure was manually recorded outside of a Failsafe execution.
   * @return a non-negative duration to be used as the delay before next execution attempt, otherwise (null or negative
   * duration) means fall back to the otherwise configured delay
   */
  Duration computeDelay(R result, F failure, ExecutionContext<R> context);
}