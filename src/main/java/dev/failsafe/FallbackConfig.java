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

import dev.failsafe.event.EventListener;
import dev.failsafe.event.ExecutionAttemptedEvent;
import dev.failsafe.function.CheckedConsumer;
import dev.failsafe.function.CheckedFunction;
import dev.failsafe.function.CheckedRunnable;
import dev.failsafe.function.CheckedSupplier;

import java.util.concurrent.CompletableFuture;

/**
 * Configuration for a {@link Fallback}.
 * <p>
 * This class is threadsafe.
 * </p>
 *
 * @param <R> result type
 * @author Jonathan Halterman
 * @see FallbackBuilder
 */
public class FallbackConfig<R> extends FailurePolicyConfig<R> {
  CheckedFunction<ExecutionAttemptedEvent<R>, R> fallback;
  CheckedFunction<ExecutionAttemptedEvent<R>, CompletableFuture<R>> fallbackStage;
  boolean async;

  // Listeners
  EventListener<ExecutionAttemptedEvent<R>> failedAttemptListener;

  FallbackConfig() {
  }

  FallbackConfig(FallbackConfig<R> config) {
    super(config);
    fallback = config.fallback;
    fallbackStage = config.fallbackStage;
    async = config.async;
    failedAttemptListener = config.failedAttemptListener;
  }

  FallbackConfig(CheckedFunction<ExecutionAttemptedEvent<R>, R> fallback,
    CheckedFunction<ExecutionAttemptedEvent<R>, CompletableFuture<R>> fallbackStage) {
    this.fallback = fallback;
    this.fallbackStage = fallbackStage;
  }

  /**
   * Returns the fallback function, else {@code null} if a fallback stage function was configured instead.
   *
   * @see Fallback#of(CheckedRunnable)
   * @see Fallback#of(CheckedSupplier)
   * @see Fallback#of(CheckedConsumer)
   * @see Fallback#of(CheckedFunction)
   * @see Fallback#of(Object)
   * @see Fallback#ofException(CheckedFunction)
   * @see Fallback#builder(CheckedRunnable)
   * @see Fallback#builder(CheckedSupplier)
   * @see Fallback#builder(CheckedConsumer)
   * @see Fallback#builder(CheckedFunction)
   * @see Fallback#builder(Object)
   * @see Fallback#builderOfException(CheckedFunction)
   */
  public CheckedFunction<ExecutionAttemptedEvent<R>, R> getFallback() {
    return fallback;
  }

  /**
   * Returns the fallback stage function, else {@code null} if a fallback function was configured instead.
   *
   * @see Fallback#ofStage(CheckedSupplier)
   * @see Fallback#ofStage(CheckedFunction)
   * @see Fallback#builderOfStage(CheckedSupplier)
   * @see Fallback#builderOfStage(CheckedFunction)
   */
  public CheckedFunction<ExecutionAttemptedEvent<R>, CompletableFuture<R>> getFallbackStage() {
    return fallbackStage;
  }

  /**
   * Returns whether the Fallback is configured to handle execution results asynchronously, separate from execution.
   *
   * @see FallbackBuilder#withAsync()
   */
  public boolean isAsync() {
    return async;
  }

  /**
   * Returns the failed attempt event listener.
   *
   * @see FallbackBuilder#onFailedAttempt(EventListener)
   */
  public EventListener<ExecutionAttemptedEvent<R>> getFailedAttemptListener() {
    return failedAttemptListener;
  }
}
