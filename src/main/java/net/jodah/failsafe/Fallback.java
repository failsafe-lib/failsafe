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
package net.jodah.failsafe;

import net.jodah.failsafe.event.ExecutionAttemptedEvent;
import net.jodah.failsafe.function.CheckedConsumer;
import net.jodah.failsafe.function.CheckedFunction;
import net.jodah.failsafe.function.CheckedRunnable;
import net.jodah.failsafe.function.CheckedSupplier;
import net.jodah.failsafe.internal.util.Assert;
import net.jodah.failsafe.spi.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import static net.jodah.failsafe.Functions.toFn;

/**
 * A Policy that handles failures using a fallback function or result.
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
 * Note: Fallback extends {@link FailurePolicy} and {@link PolicyListeners} which offer additional configuration.
 * </p>
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public class Fallback<R> extends FailurePolicy<Fallback<R>, R> {
  /**
   * A fallback that will return a void result if execution fails.
   */
  public static final Fallback<Void> VOID = new Fallback<>();

  private final CheckedFunction<ExecutionAttemptedEvent<R>, R> fallback;
  private final CheckedFunction<ExecutionAttemptedEvent<R>, CompletableFuture<R>> fallbackStage;
  private final boolean async;
  private EventHandler<R> failedAttemptHandler;

  private Fallback() {
    this(null, null, false);
  }

  /**
   * Returns the {@code fallback} action to be executed if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  private Fallback(CheckedFunction<ExecutionAttemptedEvent<R>, R> fallback,
    CheckedFunction<ExecutionAttemptedEvent<R>, CompletableFuture<R>> fallbackStage, boolean async) {
    this.fallback = fallback;
    this.fallbackStage = fallbackStage;
    this.async = async;
  }

  /**
   * Returns the {@code fallback} to be executed if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  public static <R> Fallback<R> of(CheckedRunnable fallback) {
    return new Fallback<>(toFn(Assert.notNull(fallback, "fallback")), null, false);
  }

  /**
   * Returns the {@code fallback} to be executed if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  @SuppressWarnings("unchecked")
  public static <R> Fallback<R> of(CheckedSupplier<? extends R> fallback) {
    return new Fallback<>(toFn((CheckedSupplier<R>) Assert.notNull(fallback, "fallback")), null, false);
  }

  /**
   * Returns the {@code fallback} to be executed if execution fails. The {@code fallback} accepts an {@link
   * ExecutionAttemptedEvent}.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public static <R> Fallback<R> of(CheckedConsumer<ExecutionAttemptedEvent<? extends R>> fallback) {
    return new Fallback<>(toFn(Assert.notNull((CheckedConsumer) fallback, "fallback")), null, false);
  }

  /**
   * Returns the {@code fallback} to be executed if execution fails. The {@code fallback} applies an {@link
   * ExecutionAttemptedEvent}.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public static <R> Fallback<R> of(CheckedFunction<ExecutionAttemptedEvent<? extends R>, ? extends R> fallback) {
    return new Fallback<>(Assert.notNull((CheckedFunction) fallback, "fallback"), null, false);
  }

  /**
   * Returns the {@code fallback} to be executed if execution fails and allows an alternative exception to be supplied
   * instead. The {@code fallback} applies an {@link ExecutionAttemptedEvent} and must return an exception.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  public static <R> Fallback<R> ofException(
    CheckedFunction<ExecutionAttemptedEvent<? extends R>, ? extends Exception> fallback) {
    Assert.notNull(fallback, "fallback");
    return new Fallback<>(e -> {
      throw fallback.apply(e);
    }, null, false);
  }

  /**
   * Returns the {@code fallbackResult} to be provided if execution fails.
   */
  public static <R> Fallback<R> of(R fallbackResult) {
    return new Fallback<>(toFn(fallbackResult), null, false);
  }

  /**
   * Returns the {@code fallback} to be executed asynchronously if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  public static <R> Fallback<R> ofAsync(CheckedRunnable fallback) {
    return new Fallback<>(toFn(Assert.notNull(fallback, "fallback")), null, true);
  }

  /**
   * Returns the {@code fallback} to be executed asynchronously if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  @SuppressWarnings("unchecked")
  public static <R> Fallback<R> ofAsync(CheckedSupplier<? extends R> fallback) {
    return new Fallback<>(toFn((CheckedSupplier<R>) Assert.notNull(fallback, "fallback")), null, true);
  }

  /**
   * Returns the {@code fallback} to be executed asynchronously if execution fails. The {@code fallback} accepts an
   * {@link ExecutionAttemptedEvent}.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public static <R> Fallback<R> ofAsync(CheckedConsumer<ExecutionAttemptedEvent<? extends R>> fallback) {
    return new Fallback<>(toFn(Assert.notNull((CheckedConsumer) fallback, "fallback")), null, true);
  }

  /**
   * Returns the {@code fallback} to be executed asynchronously if execution fails. The {@code fallback} applies an
   * {@link ExecutionAttemptedEvent}.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public static <R> Fallback<R> ofAsync(CheckedFunction<ExecutionAttemptedEvent<? extends R>, ? extends R> fallback) {
    return new Fallback<>(Assert.notNull((CheckedFunction) fallback, "fallback"), null, true);
  }

  /**
   * Returns the {@code fallback} to be executed if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public static <R> Fallback<R> ofStage(CheckedSupplier<? extends CompletionStage<R>> fallback) {
    return new Fallback<>(null, (CheckedFunction) toFn(Assert.notNull(fallback, "fallback")), false);
  }

  /**
   * Returns the {@code fallback} to be executed if execution fails. The {@code fallback} accepts an {@link
   * ExecutionAttemptedEvent}.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public static <R> Fallback<R> ofStage(
    CheckedFunction<ExecutionAttemptedEvent<? extends R>, ? extends CompletionStage<R>> fallback) {
    return new Fallback<>(null, Assert.notNull((CheckedFunction) fallback, "fallback"), false);
  }

  /**
   * Returns the {@code fallback} to be executed asynchronously if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public static <R> Fallback<R> ofStageAsync(CheckedSupplier<? extends CompletionStage<R>> fallback) {
    return new Fallback<>(null, (CheckedFunction) toFn(Assert.notNull(fallback, "fallback")), true);
  }

  /**
   * Returns the {@code fallback} to be executed asynchronously if execution fails. The {@code fallback} accepts an
   * {@link ExecutionAttemptedEvent}.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public static <R> Fallback<R> ofStageAsync(
    CheckedFunction<ExecutionAttemptedEvent<? extends R>, ? extends CompletionStage<R>> fallback) {
    return new Fallback<>(null, Assert.notNull((CheckedFunction) fallback, "fallback"), true);
  }

  /**
   * Returns whether the Fallback is configured to handle execution results asynchronously, separate from execution.
   */
  public boolean isAsync() {
    return async;
  }

  /**
   * Registers the {@code listener} to be called when the last execution attempt prior to the fallback failed. You can
   * also use {@link #onFailure(CheckedConsumer) onFailure} to determine when the fallback attempt also fails.
   * <p>Note: Any exceptions that are thrown from within the {@code listener} are ignored.</p>
   */
  public Fallback<R> onFailedAttempt(CheckedConsumer<ExecutionAttemptedEvent<R>> listener) {
    failedAttemptHandler = EventHandler.ofAttempt(Assert.notNull(listener, "listener"));
    return this;
  }

  /**
   * Returns the applied fallback result.
   */
  R apply(R result, Throwable failure, ExecutionContext<R> context) throws Throwable {
    ExecutionAttemptedEvent<R> event = new ExecutionAttemptedEvent<>(result, failure, context);
    return fallback != null ? fallback.apply(event) : fallbackStage.apply(event).get();
  }

  /**
   * Returns a future applied fallback result.
   */
  CompletableFuture<R> applyStage(R result, Throwable failure, ExecutionContext<R> context) throws Throwable {
    ExecutionAttemptedEvent<R> event = new ExecutionAttemptedEvent<>(result, failure, context);
    return fallback != null ? CompletableFuture.completedFuture(fallback.apply(event)) : fallbackStage.apply(event);
  }

  @Override
  public PolicyExecutor<R, ? extends Policy<R>> toExecutor(int policyIndex) {
    return new FallbackExecutor<>(this, policyIndex, failurePolicyInternal, policyHandlers, failedAttemptHandler);
  }
}
