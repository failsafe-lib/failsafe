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
package net.jodah.failsafe.internal;

import net.jodah.failsafe.ExecutionContext;
import net.jodah.failsafe.Fallback;
import net.jodah.failsafe.FallbackBuilder;
import net.jodah.failsafe.FallbackConfig;
import net.jodah.failsafe.event.ExecutionAttemptedEvent;
import net.jodah.failsafe.function.CheckedConsumer;
import net.jodah.failsafe.internal.util.Assert;
import net.jodah.failsafe.spi.AbstractPolicy;
import net.jodah.failsafe.spi.EventHandler;
import net.jodah.failsafe.spi.FailurePolicy;
import net.jodah.failsafe.spi.PolicyExecutor;

import java.util.concurrent.CompletableFuture;

/**
 * A {@link Fallback} implementation.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 * @see FallbackBuilder
 */
public class FallbackImpl<R> extends AbstractPolicy<Fallback<R>, R> implements Fallback<R>, FailurePolicy<R> {
  /**
   * A fallback that will return null if execution fails.
   */
  public static Fallback<Void> NONE = Fallback.<Void>builder(() -> null).build();

  private final FallbackConfig<R> config;
  private volatile EventHandler<R> failedAttemptHandler;

  public FallbackImpl(FallbackConfig<R> config) {
    this.config = config;
  }

  @Override
  public FallbackConfig<R> getConfig() {
    return config;
  }

  /**
   * Registers the {@code listener} to be called when the last execution attempt prior to the fallback failed. You can
   * also use {@link #onFailure(CheckedConsumer) onFailure} to determine when the fallback attempt also fails.
   * <p>Note: Any exceptions that are thrown from within the {@code listener} are ignored.</p>
   */
  @Override
  public FallbackImpl<R> onFailedAttempt(CheckedConsumer<ExecutionAttemptedEvent<R>> listener) {
    failedAttemptHandler = EventHandler.ofAttempted(Assert.notNull(listener, "listener"));
    return this;
  }

  /**
   * Returns the applied fallback result.
   */
  protected R apply(R result, Throwable failure, ExecutionContext<R> context) throws Throwable {
    ExecutionAttemptedEvent<R> event = new ExecutionAttemptedEvent<>(result, failure, context);
    return config.getFallback() != null ?
      config.getFallback().apply(event) :
      config.getFallbackStage().apply(event).get();
  }

  /**
   * Returns a future applied fallback result.
   */
  protected CompletableFuture<R> applyStage(R result, Throwable failure, ExecutionContext<R> context) throws Throwable {
    ExecutionAttemptedEvent<R> event = new ExecutionAttemptedEvent<>(result, failure, context);
    return config.getFallback() != null ?
      CompletableFuture.completedFuture(config.getFallback().apply(event)) :
      config.getFallbackStage().apply(event);
  }

  @Override
  public PolicyExecutor<R> toExecutor(int policyIndex) {
    return new FallbackExecutor<>(this, policyIndex, successHandler, failureHandler, failedAttemptHandler);
  }
}
