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

import net.jodah.failsafe.event.FailsafeEvent;
import net.jodah.failsafe.function.*;
import net.jodah.failsafe.internal.util.Assert;
import net.jodah.failsafe.internal.util.CancellableFuture;
import net.jodah.failsafe.internal.util.CommonPoolScheduler;
import net.jodah.failsafe.util.concurrent.Scheduler;
import net.jodah.failsafe.util.concurrent.Schedulers;

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
public class FailsafeExecutor<R> extends PolicyListeners<FailsafeExecutor<R>, R> {
  private Scheduler scheduler = CommonPoolScheduler.INSTANCE;
  RetryPolicy<R> retryPolicy = RetryPolicy.NEVER;
  CircuitBreaker<R> circuitBreaker;
  Fallback<R> fallback;
  /** Policies sorted outer-most first */
  List<Policy> policies;
  private EventListener completeListener;

  FailsafeExecutor(CircuitBreaker<R> circuitBreaker) {
    this.circuitBreaker = circuitBreaker;
  }

  FailsafeExecutor(RetryPolicy<R> retryPolicy) {
    this.retryPolicy = retryPolicy;
  }

  FailsafeExecutor(List<Policy> policies) {
    this.policies = policies;
  }

  /**
   * Executes the {@code callable} until a successful result is returned or the configured {@link RetryPolicy} is
   * exceeded.
   *
   * @throws NullPointerException if the {@code callable} is null
   * @throws FailsafeException if the {@code callable} fails with a checked Exception or if interrupted while
   *     waiting to perform a retry.
   * @throws CircuitBreakerOpenException if a configured circuit is open.
   */
  public <T extends R> T get(Callable<T> callable) {
    return call(execution -> Assert.notNull(callable, "callable"));
  }

  /**
   * Executes the {@code callable} until a successful result is returned or the configured {@link RetryPolicy} is
   * exceeded.
   *
   * @throws NullPointerException if the {@code callable} is null
   * @throws FailsafeException if the {@code callable} fails with a checked Exception or if interrupted while
   *     waiting to perform a retry.
   * @throws CircuitBreakerOpenException if a configured circuit is open.
   */
  public <T extends R> T get(ContextualCallable<T> callable) {
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
  public <T extends R> Future<T> getAsync(Callable<T> callable) {
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
  public <T extends R> Future<T> getAsync(ContextualCallable<T> callable) {
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
  public <T extends R> Future<T> getAsyncExecution(AsyncCallable<T> callable) {
    return callAsync(execution -> Functions.asyncOf(callable, execution), null);
  }

  void handleComplete(ExecutionResult result, ExecutionContext context) {
    if (successListener != null && result.getSuccessAll())
      successListener.handle(result, context.copy());
    else if (failureListener != null && !result.getSuccessAll())
      failureListener.handle(result, context.copy());
    if (completeListener != null)
      completeListener.handle(result, context.copy());
  }

  /**
   * Registers the {@code listener} to be called when an execution is complete for all of the configured {@link Policy
   * policies}.
   */
  public FailsafeExecutor<R> onComplete(CheckedConsumer<? extends FailsafeEvent<R>> listener) {
    completeListener = EventListener.of(Assert.notNull(listener, "listener"));
    return this;
  }

  /**
   * Registers the {@code listener} to be called when an execution fails. If multiple policies, are configured, this
   * handler is called when execution is complete and any policy fails.
   */
  @Override
  public FailsafeExecutor<R> onFailure(CheckedConsumer<? extends FailsafeEvent<R>> listener) {
    return super.onFailure(listener);
  }

  /**
   * Registers the {@code listener} to be called when an execution is successful. If multiple policies, are configured,
   * this handler is called when execution is complete and all policies succeed.
   */
  @Override
  public FailsafeExecutor<R> onSuccess(CheckedConsumer<? extends FailsafeEvent<R>> listener) {
    return super.onSuccess(listener);
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
  public <T extends R> CompletableFuture<T> future(Callable<? extends CompletionStage<T>> callable) {
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
  public <T extends R> CompletableFuture<T> future(ContextualCallable<? extends CompletionStage<T>> callable) {
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
  public <T extends R> CompletableFuture<T> futureAsyncExecution(AsyncCallable<? extends CompletionStage<T>> callable) {
    return callAsync(execution -> Functions.asyncOfFuture(callable, execution));
  }

  /**
   * Executes the {@code runnable} until successful or until the configured {@link RetryPolicy} is exceeded.
   *
   * @throws NullPointerException if the {@code runnable} is null
   * @throws FailsafeException if the {@code callable} fails with a checked Exception or if interrupted while
   *     waiting to perform a retry.
   * @throws CircuitBreakerOpenException if a configured circuit is open.
   */
  public void run(CheckedRunnable runnable) {
    call(execution -> Functions.callableOf(runnable));
  }

  /**
   * Executes the {@code runnable} until successful or until the configured {@link RetryPolicy} is exceeded.
   *
   * @throws NullPointerException if the {@code runnable} is null
   * @throws FailsafeException if the {@code runnable} fails with a checked Exception or if interrupted while
   *     waiting to perform a retry.
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
   * Configures the {@code executor} to use for performing asynchronous executions and listener callbacks.
   *
   * @throws NullPointerException if {@code executor} is null
   */
  public FailsafeExecutor<R> with(ScheduledExecutorService executor) {
    this.scheduler = Schedulers.of(executor);
    return this;
  }

  /**
   * Configures the {@code scheduler} to use for performing asynchronous executions and listener callbacks.
   *
   * @throws NullPointerException if {@code scheduler} is null
   */
  public FailsafeExecutor<R> with(Scheduler scheduler) {
    this.scheduler = Assert.notNull(scheduler, "scheduler");
    return this;
  }

  /**
   * Configures the {@code circuitBreaker} to be used to control the rate of event execution.
   *
   * @throws NullPointerException if {@code circuitBreaker} is null
   * @throws IllegalStateException if a circuit breaker is already configured or if ordered policies have been
   *     configured
   */
  public FailsafeExecutor<R> with(CircuitBreaker<R> circuitBreaker) {
    Assert.state(this.circuitBreaker == null, "A circuit breaker has already been configured");
    Assert.state(policies == null || policies.isEmpty(), "Policies have already been configured");
    this.circuitBreaker = Assert.notNull(circuitBreaker, "circuitBreaker");
    return this;
  }

  /**
   * Configures the {@code retryPolicy} to be used for retrying failed executions.
   *
   * @throws NullPointerException if {@code retryPolicy} is null
   * @throws IllegalStateException if a retry policy is already configured or if ordered policies have been
   *     configured
   */
  public FailsafeExecutor<R> with(RetryPolicy<R> retryPolicy) {
    Assert.state(this.retryPolicy == RetryPolicy.NEVER, "A retry policy has already been configurd");
    Assert.state(policies == null || policies.isEmpty(), "Policies have already been configured");
    this.retryPolicy = Assert.notNull(retryPolicy, "retryPolicy");
    return this;
  }

  /**
   * Configures the {@code fallback} action to be executed if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   * @throws IllegalStateException if {@code withFallback} method has already been called or if ordered policies
   *     have been configured
   */
  public FailsafeExecutor<R> withFallback(Callable<? extends R> fallback) {
    return withFallback(Fallback.of(fallback));
  }

  /**
   * Configures the {@code fallback} action to be executed if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   * @throws IllegalStateException if {@code withFallback} method has already been called or if ordered policies
   *     have been configured
   */
  public FailsafeExecutor<R> withFallback(CheckedBiConsumer<? extends R, ? extends Throwable> fallback) {
    return withFallback(Fallback.of(fallback));
  }

  /**
   * Configures the {@code fallback} action to be executed if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   * @throws IllegalStateException if {@code withFallback} method has already been called or if ordered policies
   *     have been configured
   */
  public FailsafeExecutor<R> withFallback(CheckedBiFunction<? extends R, ? extends Throwable, ? extends R> fallback) {
    withFallback(Fallback.of(fallback));
    return this;
  }

  /**
   * Configures the {@code fallback} action to be executed if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   * @throws IllegalStateException if {@code withFallback} method has already been called or if ordered policies
   *     have been configured
   */
  public FailsafeExecutor<R> withFallback(CheckedConsumer<? extends Throwable> fallback) {
    return withFallback(Fallback.of(fallback));
  }

  /**
   * Configures the {@code fallback} action to be executed if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   * @throws IllegalStateException if {@code withFallback} method has already been called or if ordered policies
   *     have been configured
   */
  public FailsafeExecutor<R> withFallback(CheckedFunction<? extends Throwable, ? extends R> fallback) {
    return withFallback(Fallback.of(fallback));
  }

  /**
   * Configures the {@code fallback} action to be executed if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   * @throws IllegalStateException if {@code withFallback} method has already been called or if ordered policies
   *     have been configured
   */
  public FailsafeExecutor<R> withFallback(CheckedRunnable fallback) {
    return withFallback(Fallback.of(fallback));
  }

  /**
   * Configures the {@code fallback} result to be returned if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   * @throws IllegalStateException if {@code withFallback} method has already been called or if ordered policies
   *     have been configured
   */
  public FailsafeExecutor<R> withFallback(R fallback) {
    return withFallback(Fallback.of(fallback));
  }

  /**
   * Configures the {@code fallback} result to be returned if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   * @throws IllegalStateException if {@code withFallback} method has already been called or if ordered policies
   *     have been configured
   */
  public FailsafeExecutor<R> withFallback(Fallback<R> fallback) {
    Assert.state(this.fallback == null, "withFallback has already been called");
    Assert.state(policies == null || policies.isEmpty(), "Policies have already been configured");
    this.fallback = Assert.notNull(fallback, "fallback");
    return this;
  }

  /**
   * Calls the {@code callable} synchronously, performing retries according to the {@code retryPolicy}.
   *
   * @throws FailsafeException if the {@code callable} fails with a checked Exception or if interrupted while
   *     waiting to perform a retry.
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
    FailsafeFuture<T> future = new FailsafeFuture(this);
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
      future = new FailsafeFuture(this);

    AsyncExecution execution = new AsyncExecution(scheduler, future, this);
    Callable<T> callable = callableFn.apply(execution);
    execution.inject(callable);
    future.inject(execution);

    execution.executeAsync(null, scheduler, (FailsafeFuture<Object>) future);
    return future;
  }
}