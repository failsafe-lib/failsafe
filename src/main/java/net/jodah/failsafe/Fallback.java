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

import net.jodah.failsafe.function.*;
import net.jodah.failsafe.internal.executor.FallbackExecutor;
import net.jodah.failsafe.internal.util.Assert;

/**
 * A Policy that handles failures using a fallback.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public class Fallback<R> extends AbstractPolicy<Fallback<R>, R> {
  private final CheckedBiFunction<R, Throwable, R> fallback;

  /**
   * Configures the {@code fallback} action to be executed if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  private Fallback(CheckedBiFunction<R, Throwable, R> fallback) {
    this.fallback = Assert.notNull(fallback, "fallback");
  }

  /**
   * Configures the {@code fallback} action to be executed if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  @SuppressWarnings("unchecked")
  public static <R> Fallback<R> of(CheckedBiFunction<? extends R, ? extends Throwable, ? extends R> fallback) {
    return new Fallback<>((CheckedBiFunction<R, Throwable, R>) fallback);
  }

  /**
   * Configures the {@code fallback} action to be executed if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  @SuppressWarnings("unchecked")
  public static <R> Fallback<R> of(CheckedSupplier<? extends R> fallback) {
    return new Fallback<>(Functions.fnOf((CheckedSupplier<R>) Assert.notNull(fallback, "fallback")));
  }

  /**
   * Configures the {@code fallback} action to be executed if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  @SuppressWarnings("unchecked")
  public static <R> Fallback<R> of(CheckedBiConsumer<? extends R, ? extends Throwable> fallback) {
    return new Fallback<>(Functions.fnOf((CheckedBiConsumer<R, Throwable>) Assert.notNull(fallback, "fallback")));
  }

  /**
   * Configures the {@code fallback} action to be executed if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  @SuppressWarnings("unchecked")
  public static <R> Fallback<R> of(CheckedConsumer<? extends Throwable> fallback) {
    return new Fallback<>(Functions.fnOf((CheckedConsumer<Throwable>) Assert.notNull(fallback, "fallback")));
  }

  /**
   * Configures the {@code fallback} action to be executed if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  @SuppressWarnings("unchecked")
  public static <R> Fallback<R> of(CheckedFunction<? extends Throwable, ? extends R> fallback) {
    return new Fallback<>(Functions.fnOf((CheckedFunction<Throwable, R>) Assert.notNull(fallback, "fallback")));
  }

  /**
   * Configures the {@code fallback} action to be executed if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  public static <R> Fallback<R> of(CheckedRunnable fallback) {
    return new Fallback<>(Functions.fnOf(Assert.notNull(fallback, "fallback")));
  }

  /**
   * Configures the {@code fallback} result to be returned if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   */
  @SuppressWarnings("rawtypes")
  public static <R> Fallback<R> of(R fallback) {
    return new Fallback<>(Functions.fnOf(Assert.notNull(fallback, "fallback")));
  }

  public Object apply(R result, Throwable failure) throws Exception {
    return fallback.apply(result, failure);
  }

  @Override
  public PolicyExecutor toExecutor() {
    return new FallbackExecutor(this);
  }
}
