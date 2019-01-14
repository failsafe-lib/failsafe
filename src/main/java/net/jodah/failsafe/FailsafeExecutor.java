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
import net.jodah.failsafe.internal.util.CommonPoolScheduler;
import net.jodah.failsafe.util.concurrent.Scheduler;
import net.jodah.failsafe.util.concurrent.Schedulers;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * An executor capable of performing executions that where failures are handled according to configured {@link Policy
 * policies}.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public class FailsafeExecutor<R> extends PolicyListeners<FailsafeExecutor<R>, R> {
  Scheduler scheduler = CommonPoolScheduler.INSTANCE;
  RetryPolicy<R> retryPolicy = RetryPolicy.NEVER;
  CircuitBreaker<R> circuitBreaker;
  Fallback<R> fallback;
  /** Policies sorted outer-most first */
  List<Policy<R>> policies;
  private EventListener completeListener;

  FailsafeExecutor(CircuitBreaker<R> circuitBreaker) {
    this.circuitBreaker = circuitBreaker;
  }

  FailsafeExecutor(RetryPolicy<R> retryPolicy) {
    this.retryPolicy = retryPolicy;
  }

  FailsafeExecutor(List<Policy<R>> policies) {
    this.policies = policies;
  }

  /**
   * Executes the {@code supplier} until a successful result is returned or the configured {@link RetryPolicy} is
   * exceeded.
   *
   * @throws NullPointerException if the {@code supplier} is null
   * @throws FailsafeException if the {@code supplier} fails with a checked Exception or if interrupted while
   *     waiting to perform a retry.
   * @throws CircuitBreakerOpenException if a configured circuit is open.
   */
  public <T extends R> T get(CheckedSupplier<T> supplier) {
    return call(execution -> Assert.notNull(supplier, "supplier"));
  }

  /**
   * Executes the {@code supplier} until a successful result is returned or the configured {@link RetryPolicy} is
   * exceeded.
   *
   * @throws NullPointerException if the {@code supplier} is null
   * @throws FailsafeException if the {@code supplier} fails with a checked Exception or if interrupted while
   *     waiting to perform a retry.
   * @throws CircuitBreakerOpenException if a configured circuit is open.
   */
  public <T extends R> T get(ContextualSupplier<T> supplier) {
    return call(execution -> Functions.supplierOf(supplier, execution));
  }

  /**
   * Executes the {@code supplier} asynchronously until a successful result is returned or the configured {@link
   * RetryPolicy} is exceeded.
   * <p>
   * If a configured circuit breaker is open, the resulting future is completed with {@link
   * CircuitBreakerOpenException}.
   *
   * @throws NullPointerException if the {@code supplier} is null
   * @throws RejectedExecutionException if the {@code supplier} cannot be scheduled for execution
   */
  public <T extends R> CompletableFuture<T> getAsync(CheckedSupplier<T> supplier) {
    return callAsync(execution -> Functions.promiseOf(supplier, execution));
  }

  /**
   * Executes the {@code supplier} asynchronously until a successful result is returned or the configured {@link
   * RetryPolicy} is exceeded.
   * <p>
   * If a configured circuit breaker is open, the resulting future is completed with {@link
   * CircuitBreakerOpenException}.
   *
   * @throws NullPointerException if the {@code supplier} is null
   * @throws RejectedExecutionException if the {@code supplier} cannot be scheduled for execution
   */
  public <T extends R> CompletableFuture<T> getAsync(ContextualSupplier<T> supplier) {
    return callAsync(execution -> Functions.promiseOf(supplier, execution));
  }

  /**
   * Executes the {@code supplier} asynchronously until a successful result is returned or the configured {@link
   * RetryPolicy} is exceeded. This method is intended for integration with asynchronous code. Retries must be manually
   * scheduled via one of the {@code AsyncExecution.retry} methods.
   * <p>
   * If a configured circuit breaker is open, the resulting future is completed with {@link
   * CircuitBreakerOpenException}.
   *
   * @throws NullPointerException if the {@code supplier} is null
   * @throws RejectedExecutionException if the {@code supplier} cannot be scheduled for execution
   */
  public <T extends R> CompletableFuture<T> getAsyncExecution(AsyncSupplier<T> supplier) {
    return callAsyncExecution(execution -> Functions.asyncOfExecution(supplier, execution));
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
   * Executes the {@code supplier} asynchronously until the resulting future is successfully completed or the configured
   * {@link RetryPolicy} is exceeded.
   * <p>
   * If a configured circuit breaker is open, the resulting future is completed exceptionally with {@link
   * CircuitBreakerOpenException}.
   *
   * @throws NullPointerException if the {@code supplier} is null
   * @throws RejectedExecutionException if the {@code supplier} cannot be scheduled for execution
   */
  public <T extends R> CompletableFuture<T> futureAsync(CheckedSupplier<? extends CompletionStage<T>> supplier) {
    return callAsync(execution -> Functions.promiseOfStage(supplier, execution));
  }

  /**
   * Executes the {@code supplier} asynchronously until the resulting future is successfully completed or the configured
   * {@link RetryPolicy} is exceeded.
   * <p>
   * If a configured circuit breaker is open, the resulting future is completed exceptionally with {@link
   * CircuitBreakerOpenException}.
   *
   * @throws NullPointerException if the {@code supplier} is null
   * @throws RejectedExecutionException if the {@code supplier} cannot be scheduled for execution
   */
  public <T extends R> CompletableFuture<T> futureAsync(ContextualSupplier<? extends CompletionStage<T>> supplier) {
    return callAsync(execution -> Functions.promiseOfStage(supplier, execution));
  }

  /**
   * Executes the {@code supplier} asynchronously until the resulting future is successfully completed or the configured
   * {@link RetryPolicy} is exceeded. This method is intended for integration with asynchronous code. Retries must be
   * manually scheduled via one of the {@code AsyncExecution.retry} methods.
   * <p>
   * If a configured circuit breaker is open, the resulting future is completed exceptionally with {@link
   * CircuitBreakerOpenException}.
   *
   * @throws NullPointerException if the {@code supplier} is null
   * @throws RejectedExecutionException if the {@code supplier} cannot be scheduled for execution
   */
  public <T extends R> CompletableFuture<T> futureAsyncExecution(AsyncSupplier<? extends CompletionStage<T>> supplier) {
    return callAsyncExecution(execution -> Functions.asyncOfFutureExecution(supplier, execution));
  }

  /**
   * Executes the {@code runnable} until successful or until the configured {@link RetryPolicy} is exceeded.
   *
   * @throws NullPointerException if the {@code runnable} is null
   * @throws FailsafeException if the {@code supplier} fails with a checked Exception or if interrupted while
   *     waiting to perform a retry.
   * @throws CircuitBreakerOpenException if a configured circuit is open.
   */
  public void run(CheckedRunnable runnable) {
    call(execution -> Functions.supplierOf(runnable));
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
    call(execution -> Functions.supplierOf(runnable, execution));
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
  public CompletableFuture<Void> runAsync(CheckedRunnable runnable) {
    return callAsync(execution -> Functions.promiseOf(runnable, execution));
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
  public CompletableFuture<Void> runAsync(ContextualRunnable runnable) {
    return callAsync(execution -> Functions.promiseOf(runnable, execution));
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
  public CompletableFuture<Void> runAsyncExecution(AsyncRunnable runnable) {
    return callAsyncExecution(execution -> Functions.asyncOfExecution(runnable, execution));
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
   * Configures the {@code fallback} result to be returned if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   * @throws IllegalStateException if {@code withFallback} method has already been called or if ordered policies
   *     have been configured
   */
  public FailsafeExecutor<R> with(Fallback<R> fallback) {
    Assert.state(this.fallback == null, "A fallback has already been configured");
    Assert.state(policies == null || policies.isEmpty(), "Policies have already been configured");
    this.fallback = Assert.notNull(fallback, "fallback");
    return this;
  }

  /**
   * Calls the {@code supplier} synchronously, performing retries according to the {@code retryPolicy}.
   *
   * @throws FailsafeException if the {@code supplier} fails with a checked Exception or if interrupted while
   *     waiting to perform a retry.
   * @throws CircuitBreakerOpenException if a configured circuit breaker is open
   */
  @SuppressWarnings("unchecked")
  private <T> T call(Function<Execution, CheckedSupplier<?>> supplierFn) {
    Execution execution = new Execution(this);
    Supplier<ExecutionResult> supplier = Functions.resultSupplierOf(supplierFn.apply(execution), execution);

    ExecutionResult result = execution.executeSync(supplier);
    if (result.failure != null)
      throw result.failure instanceof RuntimeException ?
          (RuntimeException) result.failure :
          new FailsafeException(result.failure);
    return (T) result.result;
  }

  /**
   * Calls the asynchronous {@code supplier} via the configured Scheduler, performing retries according to the
   * configured RetryPolicy.
   * <p>
   * If a configured circuit breaker is open, the resulting future is completed with {@link
   * CircuitBreakerOpenException}.
   *
   * @throws NullPointerException if the {@code supplierFn} is null
   * @throws RejectedExecutionException if the {@code supplierFn} cannot be scheduled for execution
   */
  @SuppressWarnings("unchecked")
  private <T> CompletableFuture<T> callAsync(
      Function<AsyncExecution, Supplier<CompletableFuture<ExecutionResult>>> supplierFn) {
    FailsafeFuture<T> future = new FailsafeFuture(this);
    AsyncExecution execution = new AsyncExecution(scheduler, future, this);
    future.inject(execution);
    execution.executeAsync(supplierFn.apply(execution));
    return future;
  }

  /**
   * Calls the asynchronous {@code supplier} via the configured Scheduler, performing retries according to the
   * configured RetryPolicy, until any configured RetryPolicy is exceeded or the AsyncExecution is completed.
   * <p>
   * If a configured circuit breaker is open, the resulting future is completed with {@link
   * CircuitBreakerOpenException}.
   *
   * @throws NullPointerException if the {@code supplierFn} is null
   * @throws RejectedExecutionException if the {@code supplierFn} cannot be scheduled for execution
   */
  @SuppressWarnings("unchecked")
  private <T> CompletableFuture<T> callAsyncExecution(Function<AsyncExecution, Supplier<?>> supplierFn) {
    FailsafeFuture<T> future = new FailsafeFuture(this);
    AsyncExecution execution = new AsyncExecution(scheduler, future, this);
    future.inject(execution);
    execution.executeAsyncExecution(supplierFn.apply(execution));
    return future;
  }
}