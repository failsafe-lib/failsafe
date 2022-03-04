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
import dev.failsafe.function.AsyncRunnable;
import dev.failsafe.internal.TimeoutImpl;
import dev.failsafe.internal.util.Assert;

import java.time.Duration;

/**
 * A policy that cancels and fails an excecution with a {@link TimeoutExceededException TimeoutExceededException} if a
 * timeout is exceeded. Execution {@link TimeoutBuilder#withInterrupt() interruption} is optionally supported.
 * Asynchronous executions are cancelled by calling {@link java.util.concurrent.Future#cancel(boolean) cancel} on their
 * underlying future. Executions can internally cooperate with cancellation by checking {@link
 * ExecutionContext#isCancelled()}.
 * <p>
 * This policy uses a separate thread on the configured scheduler or the common pool to perform timeouts checks.
 * <p>
 * The {@link TimeoutBuilder#onFailure(EventListener)} and {@link TimeoutBuilder#onSuccess(EventListener)} event
 * handlers can be used to handle a timeout being exceeded or not.
 * </p>
 * <p>Note: {@link TimeoutBuilder#withInterrupt() interruption} will have no effect when performing an {@link
 * FailsafeExecutor#getAsyncExecution(AsyncRunnable) async execution} since the async thread is unknown to Failsafe.</p>
 * <p>
 * This class is threadsafe.
 * </p>
 *
 * @param <R> result type
 * @author Jonathan Halterman
 * @see TimeoutConfig
 * @see TimeoutBuilder
 * @see TimeoutExceededException
 */
public interface Timeout<R> extends Policy<R> {
  /**
   * Returns a {@link TimeoutBuilder} that builds {@link Timeout} instances with the given {@code timeout}.
   *
   * @param timeout the duration after which an execution is failed with {@link TimeoutExceededException
   * TimeoutExceededException}.
   * @throws NullPointerException If {@code timeout} is null
   * @throws IllegalArgumentException If {@code timeout} is <= 0
   */
  static <R> TimeoutBuilder<R> builder(Duration timeout) {
    Assert.notNull(timeout, "timeout");
    Assert.isTrue(timeout.toNanos() > 0, "timeout must be > 0");
    return new TimeoutBuilder<>(timeout);
  }

  /**
   * Creates a new TimeoutBuilder that will be based on the {@code config}.
   */
  static <R> TimeoutBuilder<R> builder(TimeoutConfig<R> config) {
    return new TimeoutBuilder<>(config);
  }

  /**
   * Returns a {@link Timeout} that fails an execution with {@link TimeoutExceededException TimeoutExceededException} if
   * it exceeds the {@code timeout}. Alias for {@code Timeout.builder(timeout).build()}. To configure additional options
   * on a Timeout, use {@link #builder(Duration)} instead.
   *
   * @param timeout the duration after which an execution is failed with {@link TimeoutExceededException
   * TimeoutExceededException}.
   * @param <R> result type
   * @throws NullPointerException If {@code timeout} is null
   * @throws IllegalArgumentException If {@code timeout} is <= 0
   *
   * @see #builder(Duration)
   */
  static <R> Timeout<R> of(Duration timeout) {
    return new TimeoutImpl<>(new TimeoutConfig<>(timeout, false));
  }

  /**
   * Returns the {@link TimeoutConfig} that the Timeout was built with.
   */
  @Override
  TimeoutConfig<R> getConfig();
}
