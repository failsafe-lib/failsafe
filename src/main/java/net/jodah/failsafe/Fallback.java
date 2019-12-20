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

import static net.jodah.failsafe.Functions.toFn;

/**
 * A Policy that handles failures using a fallback function or result.
 * <p>
 * Note: Fallback extends {@link FailurePolicy} which offers additional configuration.
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

  private final CheckedFunction<ExecutionAttemptedEvent, R> fallback;
  private boolean async;

  private Fallback(){
    fallback = null;
  }

  /**
   * Returns the {@code fallback} action to be executed if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  private Fallback(CheckedFunction<ExecutionAttemptedEvent, R> fallback, boolean async) {
    this.fallback = Assert.notNull(fallback, "fallback");
    this.async = async;
  }

  /**
   * Returns the {@code fallback} to be executed if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  public static <R> Fallback<R> of(CheckedRunnable fallback) {
    return new Fallback<>(toFn(Assert.notNull(fallback, "fallback")), false);
  }

  /**
   * Returns the {@code fallback} to be executed if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  @SuppressWarnings("unchecked")
  public static <R> Fallback<R> of(CheckedSupplier<? extends R> fallback) {
    return new Fallback<>(toFn((CheckedSupplier<R>) Assert.notNull(fallback, "fallback")), false);
  }

  /**
   * Returns the {@code fallback} to be executed if execution fails. The {@code fallback} accepts an {@link
   * ExecutionAttemptedEvent}.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  @SuppressWarnings("unchecked")
  public static <R> Fallback<R> of(CheckedConsumer<ExecutionAttemptedEvent<? extends R>> fallback) {
    return new Fallback<>(toFn(Assert.notNull((CheckedConsumer) fallback, "fallback")), false);
  }

  /**
   * Returns the {@code fallback} to be executed if execution fails. The {@code fallback} applies an {@link
   * ExecutionAttemptedEvent}.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  @SuppressWarnings("unchecked")
  public static <R> Fallback<R> of(CheckedFunction<ExecutionAttemptedEvent<? extends R>, ? extends R> fallback) {
    return new Fallback<>(Assert.notNull((CheckedFunction) fallback, "fallback"), false);
  }

  /**
   * Returns the {@code fallback} to be executed if execution fails and allows an alternative exception to be supplied
   * instead. The {@code fallback} applies an {@link ExecutionAttemptedEvent} and must return an exception.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  @SuppressWarnings("unchecked")
  public static <R> Fallback<R> ofException(
    CheckedFunction<ExecutionAttemptedEvent<? extends R>, ? extends Exception> fallback) {
    Assert.notNull((CheckedFunction) fallback, "fallback");
    return new Fallback<>(e -> {
      throw fallback.apply(e);
    }, false);
  }

  /**
   * Returns the {@code fallback} result to be returned if execution fails.
   */
  @SuppressWarnings("rawtypes")
  public static <R> Fallback<R> of(R fallback) {
    return new Fallback<>(toFn(fallback), false);
  }

  /**
   * Returns the {@code fallback} to be executed asynchronously if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  public static <R> Fallback<R> ofAsync(CheckedRunnable fallback) {
    return new Fallback<>(toFn(Assert.notNull(fallback, "fallback")), true);
  }

  /**
   * Returns the {@code fallback} to be executed asynchronously if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  @SuppressWarnings("unchecked")
  public static <R> Fallback<R> ofAsync(CheckedSupplier<? extends R> fallback) {
    return new Fallback<>(toFn((CheckedSupplier<R>) Assert.notNull(fallback, "fallback")), true);
  }

  /**
   * Returns the {@code fallback} to be executed asynchronously if execution fails. The {@code fallback} accepts an
   * {@link ExecutionAttemptedEvent}.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  @SuppressWarnings("unchecked")
  public static <R> Fallback<R> ofAsync(CheckedConsumer<ExecutionAttemptedEvent<? extends R>> fallback) {
    return new Fallback<>(toFn(Assert.notNull((CheckedConsumer) fallback, "fallback")), true);
  }

  /**
   * Returns the {@code fallback} to be executed asynchronously if execution fails. The {@code fallback} applies an
   * {@link ExecutionAttemptedEvent}.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  @SuppressWarnings("unchecked")
  public static <R> Fallback<R> ofAsync(CheckedFunction<ExecutionAttemptedEvent<? extends R>, ? extends R> fallback) {
    return new Fallback<>(Assert.notNull((CheckedFunction) fallback, "fallback"), true);
  }

  public R apply(R result, Throwable failure, ExecutionContext context) throws Exception {
    return fallback.apply(new ExecutionAttemptedEvent<>(result, failure, context));
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
