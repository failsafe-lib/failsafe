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

import net.jodah.failsafe.event.ContextualResultListener;
import net.jodah.failsafe.function.CheckedBiConsumer;
import net.jodah.failsafe.function.CheckedConsumer;
import net.jodah.failsafe.internal.util.Assert;
import net.jodah.failsafe.util.concurrent.Scheduler;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Utilities for creating listeners.
 *
 * @author Jonathan Halterman
 */
final class Listeners {
  @SuppressWarnings("unchecked")
  static <T> ContextualResultListener<T, Throwable> of(
      final ContextualResultListener<? extends T, ? extends Throwable> listener, final ExecutorService executor,
      final Scheduler scheduler) {
    return (result, failure, context) -> {
      Callable<T> callable = () -> {
        ((ContextualResultListener<T, Throwable>) listener).onResult(result, failure, context);
        return null;
      };

      try {
        if (executor != null)
          executor.submit(callable);
        else
          scheduler.schedule(callable, 0, TimeUnit.MILLISECONDS);
      } catch (Exception ignore) {
      }
    };
  }

  @SuppressWarnings("unchecked")
  static <T> ContextualResultListener<T, Throwable> of(final CheckedConsumer<? extends Throwable> listener) {
    Assert.notNull(listener, "listener");
    return (result, failure, context) -> ((CheckedConsumer<Throwable>) listener).accept(failure);
  }

  @SuppressWarnings("unchecked")
  static <T> ContextualResultListener<T, Throwable> of(
      final CheckedBiConsumer<? extends T, ? extends Throwable> listener) {
    Assert.notNull(listener, "listener");
    return (result, failure, context) -> ((CheckedBiConsumer<T, Throwable>) listener).accept(result, failure);
  }

  @SuppressWarnings("unchecked")
  static <T> ContextualResultListener<T, Throwable> ofResult(final CheckedConsumer<? extends T> listener) {
    Assert.notNull(listener, "listener");
    return (result, failure, context) -> ((CheckedConsumer<T>) listener).accept(result);
  }

  @SuppressWarnings("unchecked")
  static <T> ContextualResultListener<T, Throwable> ofResult(
      final CheckedBiConsumer<? extends T, ExecutionContext> listener) {
    Assert.notNull(listener, "listener");
    return (result, failure, context) -> ((CheckedBiConsumer<T, ExecutionContext>) listener).accept(result, context);
  }
}
