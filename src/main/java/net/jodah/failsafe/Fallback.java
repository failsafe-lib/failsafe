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
import net.jodah.failsafe.internal.executor.FallbackExecutor;
import net.jodah.failsafe.internal.util.Assert;

/**
 * A Policy that handles failures using a fallback function or result.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public class Fallback<R> extends FailurePolicy<Fallback<R>, R> {
  private final CheckedFunction<ExecutionAttemptedEvent, R> fallback;
  private boolean async;

  /**
   * Configures the {@code fallback} action to be executed if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  private Fallback(CheckedFunction<ExecutionAttemptedEvent, R> fallback, boolean async) {
    this.fallback = Assert.notNull(fallback, "fallback");
    this.async = async;
  }

  /**
   * Configures the {@code fallback} to be executed if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  public static <R> Fallback<R> of(CheckedRunnable fallback) {
    return new Fallback<>(Functions.fnOf(Assert.notNull(fallback, "fallback")), false);
  }

  /**
   * Configures the {@code fallback} to be executed asynchronously if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  public static <R> Fallback<R> ofAsync(CheckedRunnable fallback) {
    return new Fallback<>(Functions.fnOf(Assert.notNull(fallback, "fallback")), true);
  }

  /**
   * Configures the {@code fallback} to be executed if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  @SuppressWarnings("unchecked")
  public static <R> Fallback<R> of(CheckedSupplier<? extends R> fallback) {
    return new Fallback<>(Functions.fnOf((CheckedSupplier<R>) Assert.notNull(fallback, "fallback")), false);
  }

  /**
   * Configures the {@code fallback} to be executed asynchronously if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  @SuppressWarnings("unchecked")
  public static <R> Fallback<R> ofAsync(CheckedSupplier<? extends R> fallback) {
    return new Fallback<>(Functions.fnOf((CheckedSupplier<R>) Assert.notNull(fallback, "fallback")), true);
  }

  /**
   * Configures the {@code fallback} to be executed if execution fails. The {@code fallback} accepts an {@link
   * ExecutionAttemptedEvent}.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  @SuppressWarnings("unchecked")
  public static <R> Fallback<R> of(CheckedConsumer<ExecutionAttemptedEvent<? extends R>> fallback) {
    return new Fallback<>(Functions.fnOf(Assert.notNull((CheckedConsumer) fallback, "fallback")), false);
  }

  /**
   * Configures the {@code fallback} to be executed asynchronously if execution fails. The {@code fallback} accepts an
   * {@link ExecutionAttemptedEvent}.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  @SuppressWarnings("unchecked")
  public static <R> Fallback<R> ofAsync(CheckedConsumer<ExecutionAttemptedEvent<? extends R>> fallback) {
    return new Fallback<>(Functions.fnOf(Assert.notNull((CheckedConsumer) fallback, "fallback")), true);
  }

  /**
   * Configures the {@code fallback} to be executed if execution fails. The {@code fallback} applies an {@link
   * ExecutionAttemptedEvent}.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  @SuppressWarnings("unchecked")
  public static <R> Fallback<R> of(CheckedFunction<ExecutionAttemptedEvent<? extends R>, ? extends R> fallback) {
    return new Fallback<>(Assert.notNull((CheckedFunction) fallback, "fallback"), false);
  }

  /**
   * Configures the {@code fallback} to be executed asynchronously if execution fails. The {@code fallback} applies an
   * {@link ExecutionAttemptedEvent}.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  @SuppressWarnings("unchecked")
  public static <R> Fallback<R> ofAsync(CheckedFunction<ExecutionAttemptedEvent<? extends R>, ? extends R> fallback) {
    return new Fallback<>(Assert.notNull((CheckedFunction) fallback, "fallback"), true);
  }

  /**
   * Configures the {@code fallback} result to be returned if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  @SuppressWarnings("rawtypes")
  public static <R> Fallback<R> of(R fallback) {
    return new Fallback<>(Functions.fnOf(Assert.notNull(fallback, "fallback")), false);
  }

  public R apply(R result, Throwable failure, ExecutionContext context) throws Exception {
    return fallback.apply(new ExecutionAttemptedEvent<R>(result, failure, context));
  }

  /**
   * Returns whether the Fallback is configured to handle execution results asynchronously, separate from execution..
   */
  public boolean isAsync() {
    return async;
  }

  @Override
  public PolicyExecutor toExecutor(AbstractExecution execution) {
    return new FallbackExecutor(this, execution);
  }
}
