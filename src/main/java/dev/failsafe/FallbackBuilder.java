/*
 * Copyright 2016 the original author or authors.
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
import dev.failsafe.function.CheckedFunction;
import dev.failsafe.internal.FallbackImpl;
import dev.failsafe.internal.util.Assert;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Builds {@link Fallback} instances.
 * <ul>
 *   <li>By default, any exception is considered a failure and will be handled by the policy. You can override this by
 *   specifying your own {@code handle} conditions. The default exception handling condition will only be overridden by
 *   another condition that handles failure exceptions such as {@link #handle(Class)} or {@link #handleIf(BiPredicate)}.
 *   Specifying a condition that only handles results, such as {@link #handleResult(Object)} or
 *   {@link #handleResultIf(Predicate)} will not replace the default exception handling condition.</li>
 *   <li>If multiple {@code handle} conditions are specified, any condition that matches an execution result or failure
 *   will trigger policy handling.</li>
 * </ul>
 * <p>
 * Note:
 * <ul>
 *   <li>This class extends {@link FailurePolicyBuilder} which offers additional configuration.</li>
 *   <li>This class is <i>not</i> threadsafe.</li>
 * </ul>
 * </p>
 *
 * @param <R> result type
 * @author Jonathan Halterman
 * @see FallbackConfig
 */
public class FallbackBuilder<R> extends FailurePolicyBuilder<FallbackBuilder<R>, FallbackConfig<R>, R>
  implements PolicyListeners<FallbackBuilder<R>, R> {

  FallbackBuilder(CheckedFunction<ExecutionAttemptedEvent<R>, R> fallback,
    CheckedFunction<ExecutionAttemptedEvent<R>, CompletableFuture<R>> fallbackStage) {
    super(new FallbackConfig<>());
    config.fallback = fallback;
    config.fallbackStage = fallbackStage;
  }

  FallbackBuilder(FallbackConfig<R> config) {
    super(new FallbackConfig<>(config));
  }

  /**
   * Builds a new {@link Fallback} using the builder's configuration.
   */
  public Fallback<R> build() {
    return new FallbackImpl<>(new FallbackConfig<>(config));
  }

  /**
   * Registers the {@code listener} to be called when the last execution attempt prior to the fallback failed. You can
   * also use {@link #onFailure(EventListener) onFailure} to determine when the fallback attempt also fails.
   * <p>Note: Any exceptions that are thrown from within the {@code listener} are ignored.</p>
   *
   * @throws NullPointerException if {@code listener} is null
   */
  public FallbackBuilder<R> onFailedAttempt(EventListener<ExecutionAttemptedEvent<R>> listener) {
    config.failedAttemptListener = Assert.notNull(listener, "listener");
    return this;
  }

  /**
   * Configures the fallback to run asynchronously.
   */
  public FallbackBuilder<R> withAsync() {
    config.async = true;
    return this;
  }
}
