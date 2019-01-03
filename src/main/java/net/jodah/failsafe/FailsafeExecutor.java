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
import net.jodah.failsafe.internal.util.Assert;
import net.jodah.failsafe.internal.util.CancellableFuture;

import java.util.List;
import java.util.concurrent.*;
import java.util.function.Function;

/**
 * An executor capable of performing executions that where failures are handled according to configured {@link Policy
 * policies}.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public class FailsafeExecutor<R> extends FailsafeConfig<FailsafeExecutor<R>, R> {
  FailsafeExecutor(CircuitBreaker circuitBreaker) {
    this.circuitBreaker = circuitBreaker;
  }

  FailsafeExecutor(RetryPolicy retryPolicy) {
    this.retryPolicy = retryPolicy;
  }

  FailsafeExecutor(List<Policy> policies) {
    super(policies);
  }

  /**
   * Executes the {@code callable} until a successful result is returned or the configured {@link RetryPolicy} is
   * exceeded.
   *
   * @throws NullPointerException if the {@code callable} is null
   * @throws FailsafeException if the {@code callable} fails with a checked Exception or if interrupted while waiting to
   * perform a retry.
   * @throws CircuitBreakerOpenException if a configured circuit is open.
   */
  public <T> T get(Callable<T> callable) {
    return call(execution -> Assert.notNull(callable, "callable"));
  }

  /**
   * Executes the {@code callable} until a successful result is returned or the configured {@link RetryPolicy} is
   * exceeded.
   *
   * @throws NullPointerException if the {@code callable} is null
   * @throws FailsafeException if the {@code callable} fails with a checked Exception or if interrupted while waiting to
   * perform a retry.
   * @throws CircuitBreakerOpenException if a configured circuit is open.
   */
  public <T> T get(ContextualCallable<T> callable) {
    return call(execution -> Functions.callableOf(callable, execution));
  }

  /**
   * Executes the {@code callable} asynchronously until a successful result is returned or the configured {@link
   * RetryPolicy} is exceeded.
   * <p>
   * If a configured circuit breaker is open, the resulting future is completed with {@link
   * CircuitBreakerOpenException}.
   *
   * @throws NullPointerException if the {@code callable} is null
   * @throws RejectedExecutionException if the {@code callable} cannot be scheduled for execution
   */
  public <T> Future<T> getAsync(Callable<T> callable) {
    return callAsync(execution -> Functions.asyncOf(callable, execution), null);
  }

  /**
   * Executes the {@code callable} asynchronously until a successful result is returned or the configured {@link
   * RetryPolicy} is exceeded.
   * <p>
   * If a configured circuit breaker is open, the resulting future is completed with {@link
   * CircuitBreakerOpenException}.
   *
   * @throws NullPointerException if the {@code callable} is null
   * @throws RejectedExecutionException if the {@code callable} cannot be scheduled for execution
   */
  public <T> Future<T> getAsync(ContextualCallable<T> callable) {
    return callAsync(execution -> Functions.asyncOf(callable, execution), null);
  }

  /**
   * Executes the {@code callable} asynchronously until a successful result is returned or the configured {@link
   * RetryPolicy} is exceeded. This method is intended for integration with asynchronous code. Retries must be manually
   * scheduled via one of the {@code AsyncExecution.retry} methods.
   * <p>
   * If a configured circuit breaker is open, the resulting future is completed with {@link
   * CircuitBreakerOpenException}.
   *
   * @throws NullPointerException if the {@code callable} is null
   * @throws RejectedExecutionException if the {@code callable} cannot be scheduled for execution
   */
  public <T> Future<T> getAsyncExecution(AsyncCallable<T> callable) {
    return callAsync(execution -> Functions.asyncOf(callable, execution), null);
  }

  /**
   * Executes the {@code callable} asynchronously until the resulting future is successfully completed or the configured
   * {@link RetryPolicy} is exceeded.
   * <p>
   * If a configured circuit breaker is open, the resulting future is completed exceptionally with {@link
   * CircuitBreakerOpenException}.
   *
   * @throws NullPointerException if the {@code callable} is null
   * @throws RejectedExecutionException if the {@code callable} cannot be scheduled for execution
   */
  public <T> CompletableFuture<T> future(Callable<? extends CompletionStage<T>> callable) {
    return callAsync(execution -> Functions.asyncOfFuture(callable, execution));
  }

  /**
   * Executes the {@code callable} asynchronously until the resulting future is successfully completed or the configured
   * {@link RetryPolicy} is exceeded.
   * <p>
   * If a configured circuit breaker is open, the resulting future is completed exceptionally with {@link
   * CircuitBreakerOpenException}.
   *
   * @throws NullPointerException if the {@code callable} is null
   * @throws RejectedExecutionException if the {@code callable} cannot be scheduled for execution
   */
  public <T> CompletableFuture<T> future(ContextualCallable<? extends CompletionStage<T>> callable) {
    return callAsync(execution -> Functions.asyncOfFuture(callable, execution));
  }

  /**
   * Executes the {@code callable} asynchronously until the resulting future is successfully completed or the configured
   * {@link RetryPolicy} is exceeded. This method is intended for integration with asynchronous code. Retries must be
   * manually scheduled via one of the {@code AsyncExecution.retry} methods.
   * <p>
   * If a configured circuit breaker is open, the resulting future is completed exceptionally with {@link
   * CircuitBreakerOpenException}.
   *
   * @throws NullPointerException if the {@code callable} is null
   * @throws RejectedExecutionException if the {@code callable} cannot be scheduled for execution
   */
  public <T> CompletableFuture<T> futureAsyncExecution(AsyncCallable<? extends CompletionStage<T>> callable) {
    return callAsync(execution -> Functions.asyncOfFuture(callable, execution));
  }

  /**
   * Executes the {@code runnable} until successful or until the configured {@link RetryPolicy} is exceeded.
   *
   * @throws NullPointerException if the {@code runnable} is null
   * @throws FailsafeException if the {@code callable} fails with a checked Exception or if interrupted while waiting to
   * perform a retry.
   * @throws CircuitBreakerOpenException if a configured circuit is open.
   */
  public void run(CheckedRunnable runnable) {
    call(execution -> Functions.callableOf(runnable));
  }

  /**
   * Executes the {@code runnable} until successful or until the configured {@link RetryPolicy} is exceeded.
   *
   * @throws NullPointerException if the {@code runnable} is null
   * @throws FailsafeException if the {@code runnable} fails with a checked Exception or if interrupted while waiting to
   * perform a retry.
   * @throws CircuitBreakerOpenException if a configured circuit is open.
   */
  public void run(ContextualRunnable runnable) {
    call(execution -> Functions.callableOf(runnable, execution));
  }

  /**
   * Executes the {@code runnable} asynchronously until successful or until the configured {@link RetryPolicy} is
   * exceeded.
   * <p>
   * If a configured circuit breaker is open, the resulting future is completed with {@link
   * CircuitBreakerOpenException}.
   *
   * @throws NullPointerException if the {@code runnable} is null
   * @throws RejectedExecutionException if the {@code runnable} cannot be scheduled for execution
   */
  public Future<Void> runAsync(CheckedRunnable runnable) {
    return callAsync(execution -> Functions.asyncOf(runnable, execution), null);
  }

  /**
   * Executes the {@code runnable} asynchronously until successful or until the configured {@link RetryPolicy} is
   * exceeded.
   * <p>
   * If a configured circuit breaker is open, the resulting future is completed with {@link
   * CircuitBreakerOpenException}.
   *
   * @throws NullPointerException if the {@code runnable} is null
   * @throws RejectedExecutionException if the {@code runnable} cannot be scheduled for execution
   */
  public Future<Void> runAsync(ContextualRunnable runnable) {
    return callAsync(execution -> Functions.asyncOf(runnable, execution), null);
  }

  /**
   * Executes the {@code runnable} asynchronously until successful or until the configured {@link RetryPolicy} is
   * exceeded. This method is intended for integration with asynchronous code. Retries must be manually scheduled via
   * one of the {@code AsyncExecution.retry} methods.
   * <p>
   * If a configured circuit breaker is open, the resulting future is completed with {@link
   * CircuitBreakerOpenException}.
   *
   * @throws NullPointerException if the {@code runnable} is null
   * @throws RejectedExecutionException if the {@code runnable} cannot be scheduled for execution
   */
  public Future<Void> runAsyncExecution(AsyncRunnable runnable) {
    return callAsync(execution -> Functions.asyncOf(runnable, execution), null);
  }

  /**
   * Calls the {@code callable} synchronously, performing retries according to the {@code retryPolicy}.
   *
   * @throws FailsafeException if the {@code callable} fails with a checked Exception or if interrupted while waiting to
   * perform a retry.
   * @throws CircuitBreakerOpenException if a configured circuit breaker is open
   */
  @SuppressWarnings("unchecked")
  private <T> T call(Function<Execution, Callable<T>> callableFn) {
    Execution execution = new Execution(this);
    Callable<T> callable = callableFn.apply(execution);
    execution.inject(callable);

    ExecutionResult result = execution.executeSync();
    if (result.failure != null)
      throw result.failure instanceof RuntimeException ?
          (RuntimeException) result.failure :
          new FailsafeException(result.failure);
    return (T) result.result;
  }

  /**
   * Calls the asynchronous {@code callable} via the configured Scheduler, performing retries according to the
   * configured RetryPolicy, and returns a CompletableFuture.
   * <p>
   * If a configured circuit breaker is open, the resulting future is completed with {@link
   * CircuitBreakerOpenException}.
   *
   * @throws NullPointerException if the {@code callableFn} is null
   * @throws RejectedExecutionException if the {@code callableFn} cannot be scheduled for execution
   */
  @SuppressWarnings("unchecked")
  private <T> CompletableFuture<T> callAsync(Function<AsyncExecution, Callable<T>> callableFn) {
    FailsafeFuture<T> future = new FailsafeFuture(listeners);
    CompletableFuture<T> response = CancellableFuture.of(future);
    future.inject(response);
    callAsync(callableFn, future);
    return response;
  }

  /**
   * Calls the asynchronous {@code callable} via the configured Scheduler, performing retries according to the
   * configured RetryPolicy.
   * <p>
   * If a configured circuit breaker is open, the resulting future is completed with {@link
   * CircuitBreakerOpenException}.
   *
   * @throws NullPointerException if the {@code callableFn} is null
   * @throws RejectedExecutionException if the {@code callableFn} cannot be scheduled for execution
   */
  @SuppressWarnings("unchecked")
  private <T> FailsafeFuture<T> callAsync(Function<AsyncExecution, Callable<T>> callableFn, FailsafeFuture<T> future) {
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