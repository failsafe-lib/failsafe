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
import net.jodah.failsafe.internal.util.CancellableFuture;
import net.jodah.failsafe.util.concurrent.Scheduler;

import java.util.concurrent.*;
import java.util.function.Function;

/**
 * Performs asynchronous executions with failures handled according to a configured {@link Policy).
 *
 * @author Jonathan Halterman
 * @param <R> listener result type
 */
@SuppressWarnings("WeakerAccess")
public class AsyncFailsafe<R> extends AsyncFailsafeConfig<R, AsyncFailsafe<R>> {
  AsyncFailsafe(FailsafeConfig<R, ?> config, Scheduler scheduler) {
    super(config, scheduler);
  }

  /**
   * Executes the {@code callable} asynchronously until the resulting future is successfully completed or the configured
   * {@link RetryPolicy} is exceeded.
   * <p>
   * If a configured circuit breaker is open, the resulting future is completed exceptionally with
   * {@link CircuitBreakerOpenException}.
   *
   * @throws NullPointerException if the {@code callable} is null
   * @throws RejectedExecutionException if the {@code callable} cannot be scheduled for execution
   */
  public <T> CompletableFuture<T> future(Callable<? extends CompletionStage<T>> callable) {
    return call(execution -> Functions.asyncOfFuture(callable, execution));
  }

  /**
   * Executes the {@code callable} asynchronously until the resulting future is successfully completed or the configured
   * {@link RetryPolicy} is exceeded.
   * <p>
   * If a configured circuit breaker is open, the resulting future is completed exceptionally with
   * {@link CircuitBreakerOpenException}.
   *
   * @throws NullPointerException if the {@code callable} is null
   * @throws RejectedExecutionException if the {@code callable} cannot be scheduled for execution
   */
  public <T> CompletableFuture<T> future(ContextualCallable<? extends CompletionStage<T>> callable) {
    return call(execution -> Functions.asyncOfFuture(callable, execution));
  }

  /**
   * Executes the {@code callable} asynchronously until the resulting future is successfully completed or the configured
   * {@link RetryPolicy} is exceeded. This method is intended for integration with asynchronous code. Retries must be
   * manually scheduled via one of the {@code AsyncExecution.retry} methods.
   * <p>
   * If a configured circuit breaker is open, the resulting future is completed exceptionally with
   * {@link CircuitBreakerOpenException}.
   *
   * @throws NullPointerException if the {@code callable} is null
   * @throws RejectedExecutionException if the {@code callable} cannot be scheduled for execution
   */
  public <T> CompletableFuture<T> futureAsync(AsyncCallable<? extends CompletionStage<T>> callable) {
    return call(execution -> Functions.asyncOfFuture(callable, execution));
  }

  /**
   * Executes the {@code callable} asynchronously until a successful result is returned or the configured
   * {@link RetryPolicy} is exceeded.
   * <p>
   * If a configured circuit breaker is open, the resulting future is completed with
   * {@link CircuitBreakerOpenException}.
   *
   * @throws NullPointerException if the {@code callable} is null
   * @throws RejectedExecutionException if the {@code callable} cannot be scheduled for execution
   */
  public <T> Future<T> get(Callable<T> callable) {
    return call(execution -> Functions.asyncOf(callable, execution), null);
  }

  /**
   * Executes the {@code callable} asynchronously until a successful result is returned or the configured
   * {@link RetryPolicy} is exceeded.
   * <p>
   * If a configured circuit breaker is open, the resulting future is completed with
   * {@link CircuitBreakerOpenException}.
   *
   * @throws NullPointerException if the {@code callable} is null
   * @throws RejectedExecutionException if the {@code callable} cannot be scheduled for execution
   */
  public <T> Future<T> get(ContextualCallable<T> callable) {
    return call(execution -> Functions.asyncOf(callable, execution), null);
  }

  /**
   * Executes the {@code callable} asynchronously until a successful result is returned or the configured
   * {@link RetryPolicy} is exceeded. This method is intended for integration with asynchronous code. Retries must be
   * manually scheduled via one of the {@code AsyncExecution.retry} methods.
   * <p>
   * If a configured circuit breaker is open, the resulting future is completed with
   * {@link CircuitBreakerOpenException}.
   *
   * @throws NullPointerException if the {@code callable} is null
   * @throws RejectedExecutionException if the {@code callable} cannot be scheduled for execution
   */
  public <T> Future<T> getAsync(AsyncCallable<T> callable) {
    return call(execution -> Functions.asyncOf(callable, execution), null);
  }

  /**
   * Executes the {@code runnable} asynchronously until successful or until the configured {@link RetryPolicy} is
   * exceeded.
   * <p>
   * If a configured circuit breaker is open, the resulting future is completed with
   * {@link CircuitBreakerOpenException}.
   *
   * @throws NullPointerException if the {@code runnable} is null
   * @throws RejectedExecutionException if the {@code runnable} cannot be scheduled for execution
   */
  public Future<Void> run(CheckedRunnable runnable) {
    return call(execution -> Functions.asyncOf(runnable, execution), null);
  }

  /**
   * Executes the {@code runnable} asynchronously until successful or until the configured {@link RetryPolicy} is
   * exceeded.
   * <p>
   * If a configured circuit breaker is open, the resulting future is completed with
   * {@link CircuitBreakerOpenException}.
   *
   * @throws NullPointerException if the {@code runnable} is null
   * @throws RejectedExecutionException if the {@code runnable} cannot be scheduled for execution
   */
  public Future<Void> run(ContextualRunnable runnable) {
    return call(execution -> Functions.asyncOf(runnable, execution), null);
  }

  /**
   * Executes the {@code runnable} asynchronously until successful or until the configured {@link RetryPolicy} is
   * exceeded. This method is intended for integration with asynchronous code. Retries must be manually scheduled via
   * one of the {@code AsyncExecution.retry} methods.
   * <p>
   * If a configured circuit breaker is open, the resulting future is completed with
   * {@link CircuitBreakerOpenException}.
   *
   * @throws NullPointerException if the {@code runnable} is null
   * @throws RejectedExecutionException if the {@code runnable} cannot be scheduled for execution
   */
  public Future<Void> runAsync(AsyncRunnable runnable) {
    return call(execution -> Functions.asyncOf(runnable, execution), null);
  }

  /**
   * Calls the asynchronous {@code callable} via the configured Scheduler, performing retries according to the
   * configured RetryPolicy, and returns a CompletableFuture.
   * <p>
   * If a configured circuit breaker is open, the resulting future is completed with
   * {@link CircuitBreakerOpenException}.
   *
   * @throws NullPointerException if the {@code callableFn} is null
   * @throws RejectedExecutionException if the {@code callableFn} cannot be scheduled for execution
   */
  @SuppressWarnings("unchecked")
  private <T> CompletableFuture<T> call(Function<AsyncExecution, Callable<T>> callableFn) {
    FailsafeFuture<T> future = new FailsafeFuture(listeners);
    CompletableFuture<T> response = CancellableFuture.of(future);
    future.inject(response);
    call(callableFn, future);
    return response;
  }

  /**
   * Calls the asynchronous {@code callable} via the configured Scheduler, performing retries according to the
   * configured RetryPolicy.
   * <p>
   * If a configured circuit breaker is open, the resulting future is completed with
   * {@link CircuitBreakerOpenException}.
   *
   * @throws NullPointerException if the {@code callableFn} is null
   * @throws RejectedExecutionException if the {@code callableFn} cannot be scheduled for execution
   */
  @SuppressWarnings("unchecked")
  private <T> FailsafeFuture<T> call(Function<AsyncExecution, Callable<T>> callableFn, FailsafeFuture<T> future) {
    if (future == null)
      future = new FailsafeFuture(listeners);

    AsyncExecution execution = new AsyncExecution(scheduler, future, this);
    Callable<T> callable = callableFn.apply(execution);
    execution.inject(callable);
    future.inject(execution);

    execution.executeAsync(null, scheduler, (FailsafeFuture<Object>) future);
    return future;
  }
}
