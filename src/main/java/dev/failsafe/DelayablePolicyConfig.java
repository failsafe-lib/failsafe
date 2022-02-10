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
  Class<? extends Throwable> delayException;
  ContextualSupplier<R, Duration> delayFn;

  protected DelayablePolicyConfig() {
  }

  protected DelayablePolicyConfig(DelayablePolicyConfig<R> config) {
    super(config);
    delay = config.delay;
    delayResult = config.delayResult;
    delayException = config.delayException;
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
  public Class<? extends Throwable> getDelayException() {
    return delayException;
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
