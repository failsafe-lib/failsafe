/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package dev.failsafe;

import dev.failsafe.function.ContextualSupplier;
import dev.failsafe.internal.util.Assert;

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
   * <p>
   * The {@code delayFunction} must complete quickly, not have side-effects, and always return the same result for the
   * same input. Exceptions thrown by the {@code delayFunction} method will <strong>not</strong> be handled and will
   * cause Failsafe's execution to abort.
   * </p>
   * <p>
   * Notes:
   * <ul>
   * <li>A negative return value will cause Failsafe to use a configured fixed or backoff delay
   * <li>Any configured jitter is still applied to DelayFunction provided values
   * <li>Any configured max duration is still applied to DelayFunction provided values
   * <li>The {@link ExecutionContext} that is provided to the {@code delayFunction} may be {@code null} if the prior execution
   * exception was manually recorded outside of a Failsafe execution.</li>
   * </ul>
   * </p>
   *
   * @throws NullPointerException if {@code delayFunction} is null
   */
  @SuppressWarnings("unchecked")
  public S withDelayFn(ContextualSupplier<R, Duration> delayFunction) {
    Assert.notNull(delayFunction, "delayFunction");
    config.delayFn = delayFunction;
    return (S) this;
  }

  /**
   * Sets the {@code delayFunction} that computes the next delay before allowing another execution. Delays will only
   * occur for exceptions that are assignable from the {@code exception}.
   * <p>
   * The {@code delayFunction} must complete quickly, not have side-effects, and always return the same result for the
   * same input. Exceptions thrown by the {@code delayFunction} method will <strong>not</strong> be handled and will
   * cause Failsafe's execution to abort.
   * </p>
   * <p>
   * Notes:
   * <ul>
   * <li>A negative return value will cause Failsafe to use a configured fixed or backoff delay
   * <li>Any configured jitter is still applied to DelayFunction provided values
   * <li>Any configured max duration is still applied to DelayFunction provided values
   * <li>The {@link ExecutionContext} that is provided to the {@code delayFunction} may be {@code null} if the prior execution
   * exception was manually recorded outside of a Failsafe execution.</li>
   * </ul>
   * </p>
   *
   * @param delayFunction the function to use to compute the delay before a next attempt
   * @param exception the execution exception that is expected in order to trigger the delay
   * @param <F> exception type
   * @throws NullPointerException if {@code delayFunction} or {@code exception} are null
   */
  @SuppressWarnings("unchecked")
  public <F extends Throwable> S withDelayFnOn(ContextualSupplier<R, Duration> delayFunction, Class<F> exception) {
    withDelayFn(delayFunction);
    Assert.notNull(exception, "exception");
    config.delayException = exception;
    return (S) this;
  }

  /**
   * Sets the {@code delayFunction} that computes the next delay before allowing another execution. Delays will only
   * occur for results that equal the {@code result}.
   * <p>
   * The {@code delayFunction} must complete quickly, not have side-effects, and always return the same result for the
   * same input. Exceptions thrown by the {@code delayFunction} method will <strong>not</strong> be handled and will
   * cause Failsafe's execution to abort.
   * </p>
   * <p>
   * Notes:
   * <ul>
   * <li>A negative return value will cause Failsafe to use a configured fixed or backoff delay
   * <li>Any configured jitter is still applied to DelayFunction provided values
   * <li>Any configured max duration is still applied to DelayFunction provided values
   * <li>The {@link ExecutionContext} that is provided to the {@code delayFunction} may be {@code null} if the prior execution
   * exception was manually recorded outside of a Failsafe execution.</li>
   * </ul>
   * </p>
   *
   * @param delayFunction the function to use to compute the delay before a next attempt
   * @param result the execution result that is expected in order to trigger the delay
   * @throws NullPointerException if {@code delayFunction} or {@code result} are null
   */
  @SuppressWarnings("unchecked")
  public S withDelayFnWhen(ContextualSupplier<R, Duration> delayFunction, R result) {
    withDelayFn(delayFunction);
    Assert.notNull(result, "result");
    config.delayResult = result;
    return (S) this;
  }
}
