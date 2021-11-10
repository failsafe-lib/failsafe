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

import dev.failsafe.function.CheckedConsumer;
import dev.failsafe.function.CheckedRunnable;
import dev.failsafe.function.CheckedSupplier;
import dev.failsafe.event.ExecutionAttemptedEvent;
import dev.failsafe.function.CheckedFunction;
import dev.failsafe.internal.FallbackImpl;
import dev.failsafe.internal.util.Assert;

import java.util.concurrent.CompletionStage;

import static dev.failsafe.Functions.toFn;

/**
 * A Policy that handles failures using a fallback function or result.
 * <p>
 * This class is threadsafe.
 * </p>
 *
 * @param <R> result type
 * @author Jonathan Halterman
 * @see TimeoutConfig
 * @see FallbackBuilder
 */
public interface Fallback<R> extends Policy<R> {
  /**
   * Returns the {@code fallback} to be executed if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  static <R> FallbackBuilder<R> builder(CheckedRunnable fallback) {
    return new FallbackBuilder<>(toFn(Assert.notNull(fallback, "fallback")), null);
  }

  /**
   * Returns the {@code fallback} to be executed if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  static <R> FallbackBuilder<R> builder(CheckedSupplier<? extends R> fallback) {
    return new FallbackBuilder<>(toFn(Assert.notNull(fallback, "fallback")), null);
  }

  /**
   * Returns the {@code fallback} to be executed if execution fails. The {@code fallback} accepts an {@link
   * ExecutionAttemptedEvent}.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  static <R> FallbackBuilder<R> builder(CheckedConsumer<ExecutionAttemptedEvent<? extends R>> fallback) {
    return new FallbackBuilder<>(toFn(Assert.notNull((CheckedConsumer) fallback, "fallback")), null);
  }

  /**
   * Returns the {@code fallback} to be executed if execution fails. The {@code fallback} applies an {@link
   * ExecutionAttemptedEvent}.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  static <R> FallbackBuilder<R> builder(CheckedFunction<ExecutionAttemptedEvent<? extends R>, ? extends R> fallback) {
    return new FallbackBuilder<>(Assert.notNull((CheckedFunction) fallback, "fallback"), null);
  }

  /**
   * Returns the {@code fallbackResult} to be provided if execution fails.
   */
  static <R> FallbackBuilder<R> builder(R fallbackResult) {
    return new FallbackBuilder<>(toFn(fallbackResult), null);
  }

  /**
   * Returns the {@code fallback} to be executed if execution fails and allows an alternative exception to be supplied
   * instead. The {@code fallback} applies an {@link ExecutionAttemptedEvent} and must return an exception.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  static <R> FallbackBuilder<R> builderOfException(
    CheckedFunction<ExecutionAttemptedEvent<? extends R>, ? extends Exception> fallback) {
    Assert.notNull(fallback, "fallback");
    return new FallbackBuilder<>(e -> {
      throw fallback.apply(e);
    }, null);
  }

  /**
   * Returns the {@code fallback} to be executed if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  static <R> FallbackBuilder<R> builderOfStage(CheckedSupplier<? extends CompletionStage<R>> fallback) {
    return new FallbackBuilder<>(null, (CheckedFunction) toFn(Assert.notNull(fallback, "fallback")));
  }

  /**
   * Returns the {@code fallback} to be executed if execution fails. The {@code fallback} accepts an {@link
   * ExecutionAttemptedEvent}.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  static <R> FallbackBuilder<R> builderOfStage(
    CheckedFunction<ExecutionAttemptedEvent<? extends R>, ? extends CompletionStage<R>> fallback) {
    return new FallbackBuilder<>(null, Assert.notNull((CheckedFunction) fallback, "fallback"));
  }

  /**
   * Returns the {@code fallback} to be executed if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  static <R> Fallback<R> of(CheckedRunnable fallback) {
    return new FallbackImpl<>(new FallbackConfig<>(toFn(Assert.notNull(fallback, "fallback")), null));
  }

  /**
   * Returns the {@code fallback} to be executed if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  static <R> Fallback<R> of(CheckedSupplier<? extends R> fallback) {
    return new FallbackImpl<>(new FallbackConfig<>(toFn(Assert.notNull(fallback, "fallback")), null));
  }

  /**
   * Returns the {@code fallback} to be executed if execution fails. The {@code fallback} accepts an {@link
   * ExecutionAttemptedEvent}.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  static <R> Fallback<R> of(CheckedConsumer<ExecutionAttemptedEvent<? extends R>> fallback) {
    return new FallbackImpl<>(new FallbackConfig<>(toFn(Assert.notNull((CheckedConsumer) fallback, "fallback")), null));
  }

  /**
   * Returns the {@code fallback} to be executed if execution fails. The {@code fallback} applies an {@link
   * ExecutionAttemptedEvent}.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  static <R> Fallback<R> of(CheckedFunction<ExecutionAttemptedEvent<? extends R>, ? extends R> fallback) {
    return new FallbackImpl<>(new FallbackConfig<>(Assert.notNull((CheckedFunction) fallback, "fallback"), null));
  }

  /**
   * Returns the {@code fallback} to be executed if execution fails and allows an alternative exception to be supplied
   * instead. The {@code fallback} applies an {@link ExecutionAttemptedEvent} and must return an exception.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  static <R> Fallback<R> ofException(
    CheckedFunction<ExecutionAttemptedEvent<? extends R>, ? extends Exception> fallback) {
    Assert.notNull(fallback, "fallback");
    return new FallbackImpl<>(new FallbackConfig<>(e -> {
      throw fallback.apply(e);
    }, null));
  }

  /**
   * Returns the {@code fallbackResult} to be provided if execution fails.
   */
  static <R> Fallback<R> of(R fallbackResult) {
    return new FallbackImpl<>(new FallbackConfig<>(toFn(fallbackResult), null));
  }

  /**
   * Returns the {@code fallback} to be executed if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  static <R> Fallback<R> ofStage(CheckedSupplier<? extends CompletionStage<R>> fallback) {
    return new FallbackImpl<>(new FallbackConfig<>(null, (CheckedFunction) toFn(Assert.notNull(fallback, "fallback"))));
  }

  /**
   * Returns the {@code fallback} to be executed if execution fails. The {@code fallback} accepts an {@link
   * ExecutionAttemptedEvent}.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  static <R> Fallback<R> ofStage(
    CheckedFunction<ExecutionAttemptedEvent<? extends R>, ? extends CompletionStage<R>> fallback) {
    return new FallbackImpl<>(new FallbackConfig<>(null, Assert.notNull((CheckedFunction) fallback, "fallback")));
  }

  /**
   * Returns a fallback that will return a null if execution fails.
   */
  static Fallback<Void> none() {
    return FallbackImpl.NONE;
  }

  /**
   * Returns the {@link FallbackConfig} that the Fallback was built with.
   */
  FallbackConfig<R> getConfig();
}
