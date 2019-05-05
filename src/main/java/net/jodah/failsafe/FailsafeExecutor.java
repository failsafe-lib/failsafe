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

import net.jodah.failsafe.event.ExecutionCompletedEvent;
import net.jodah.failsafe.function.*;
import net.jodah.failsafe.internal.EventListener;
import net.jodah.failsafe.internal.util.Assert;
import net.jodah.failsafe.internal.util.DelegatingScheduler;
import net.jodah.failsafe.util.concurrent.Scheduler;

import java.util.List;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * <p>
 * An executor that handles failures according to configured {@link FailurePolicy policies}. Can be created via {@link
 * Failsafe#with(Policy[])}.
 * <p>
 * Async executions are run by default on the {@link ForkJoinPool#commonPool()}. Alternative executors can be
 * configured via {@link #with(ScheduledExecutorService)} and similar methods. All async executions are cancellable via the returned
 * CompletableFuture, even those run by a {@link ForkJoinPool} implementation.
 * <p>
 * Executions that are cancelled or timed out while blocked or waiting will be interrupted with an {@link
 * InterruptedException}. Executions that do not block can cooperate with cancellation by periodiically checking for
 * {@code Thread.currentThread().isInterrupted()} and exit if {@code true}.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public class FailsafeExecutor<R> extends PolicyListeners<FailsafeExecutor<R>, R> {
  private Scheduler scheduler = DelegatingScheduler.INSTANCE;
  /** Policies sorted outer-most first */
  final List<Policy<R>> policies;
  private EventListener completeListener;

  /**
   * @throws IllegalArgumentException if {@code policies} is empty
   */
  FailsafeExecutor(List<Policy<R>> policies) {
    Assert.isTrue(!policies.isEmpty(), "At least one policy must be supplied");
    this.policies = policies;
  }

  /**
   * Executes the {@code supplier} until a successful result is returned or the configured policies are exceeded.
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
   * Executes the {@code supplier} until a successful result is returned or the configured policies are exceeded.
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
   * Executes the {@code supplier} asynchronously until a successful result is returned or the configured policies are
   * exceeded.
   * <p>
   * If a configured circuit breaker is open, the resulting future is completed with {@link
   * CircuitBreakerOpenException}.
   *
   * @throws NullPointerException if the {@code supplier} is null
   * @throws RejectedExecutionException if the {@code supplier} cannot be scheduled for execution
   */
  public <T extends R> CompletableFuture<T> getAsync(CheckedSupplier<T> supplier) {
    return callAsync(execution -> Functions.promiseOf(supplier, execution), false);
  }

  /**
   * Executes the {@code supplier} asynchronously until a successful result is returned or the configured policies are
   * exceeded.
   * <p>
   * If a configured circuit breaker is open, the resulting future is completed with {@link
   * CircuitBreakerOpenException}.
   *
   * @throws NullPointerException if the {@code supplier} is null
   * @throws RejectedExecutionException if the {@code supplier} cannot be scheduled for execution
   */
  public <T extends R> CompletableFuture<T> getAsync(ContextualSupplier<T> supplier) {
    return callAsync(execution -> Functions.promiseOf(supplier, execution), false);
  }

  /**
   * Executes the {@code supplier} asynchronously until a successful result is returned or the configured policies are
   * exceeded. This method is intended for integration with asynchronous code. Retries must be manually scheduled via
   * one of the {@code AsyncExecution.retry} methods.
   * <p>
   * If a configured circuit breaker is open, the resulting future is completed with {@link
   * CircuitBreakerOpenException}.
   *
   * @throws NullPointerException if the {@code supplier} is null
   * @throws RejectedExecutionException if the {@code supplier} cannot be scheduled for execution
   */
  public <T extends R> CompletableFuture<T> getAsyncExecution(AsyncSupplier<T> supplier) {
    return callAsync(execution -> Functions.asyncOfExecution(supplier, execution), true);
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
   * Registers the {@code listener} to be called when an execution is complete for all of the configured policies are
   * exceeded.
   * <p>Note: Any exceptions that are thrown from within the {@code listener} are ignored.</p>
   */
  public FailsafeExecutor<R> onComplete(CheckedConsumer<? extends ExecutionCompletedEvent<R>> listener) {
    completeListener = EventListener.of(Assert.notNull(listener, "listener"));
    return this;
  }

  /**
   * Registers the {@code listener} to be called when an execution fails. If multiple policies, are configured, this
   * handler is called when execution is complete and <i>any</i> policy fails.
   * <p>Note: Any exceptions that are thrown from within the {@code listener} are ignored.</p>
   */
  @Override
  public FailsafeExecutor<R> onFailure(CheckedConsumer<? extends ExecutionCompletedEvent<R>> listener) {
    return super.onFailure(listener);
  }

  /**
   * Registers the {@code listener} to be called when an execution is successful. If multiple policies, are configured,
   * this handler is called when execution is complete and <i>all</i> policies succeed. If <i>all</i> policies do not
   * succeed, then the {@link #onFailure(CheckedConsumer)} registered listener is called instead.
   * <p>Note: Any exceptions that are thrown from within the {@code listener} are ignored.</p>
   */
  @Override
  public FailsafeExecutor<R> onSuccess(CheckedConsumer<? extends ExecutionCompletedEvent<R>> listener) {
    return super.onSuccess(listener);
  }

  /**
   * Executes the {@code supplier} asynchronously until the resulting future is successfully completed or the configured
   * policies are exceeded.
   * <p>
   * If a configured circuit breaker is open, the resulting future is completed exceptionally with {@link
   * CircuitBreakerOpenException}.
   *
   * @throws NullPointerException if the {@code supplier} is null
   * @throws RejectedExecutionException if the {@code supplier} cannot be scheduled for execution
   */
  public <T extends R> CompletableFuture<T> getStageAsync(CheckedSupplier<? extends CompletionStage<T>> supplier) {
    return callAsync(execution -> Functions.promiseOfStage(supplier, execution), false);
  }

  /**
   * Executes the {@code supplier} asynchronously until the resulting future is successfully completed or the configured
   * policies are exceeded.
   * <p>
   * If a configured circuit breaker is open, the resulting future is completed exceptionally with {@link
   * CircuitBreakerOpenException}.
   *
   * @throws NullPointerException if the {@code supplier} is null
   * @throws RejectedExecutionException if the {@code supplier} cannot be scheduled for execution
   */
  public <T extends R> CompletableFuture<T> getStageAsync(ContextualSupplier<? extends CompletionStage<T>> supplier) {
    return callAsync(execution -> Functions.promiseOfStage(supplier, execution), false);
  }

  /**
   * Executes the {@code supplier} asynchronously until the resulting future is successfully completed or the configured
   * policies are exceeded. This method is intended for integration with asynchronous code. Retries must be manually
   * scheduled via one of the {@code AsyncExecution.retry} methods.
   * <p>
   * If a configured circuit breaker is open, the resulting future is completed exceptionally with {@link
   * CircuitBreakerOpenException}.
   *
   * @throws NullPointerException if the {@code supplier} is null
   * @throws RejectedExecutionException if the {@code supplier} cannot be scheduled for execution
   */
  public <T extends R> CompletableFuture<T> getStageAsyncExecution(
      AsyncSupplier<? extends CompletionStage<T>> supplier) {
    return callAsync(execution -> Functions.asyncOfFutureExecution(supplier, execution), true);
  }

  /**
   * Executes the {@code runnable} until successful or until the configured policies are exceeded.
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
   * Executes the {@code runnable} until successful or until the configured policies are exceeded.
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
   * Executes the {@code runnable} asynchronously until successful or until the configured policies are exceeded.
   * <p>
   * If a configured circuit breaker is open, the resulting future is completed with {@link
   * CircuitBreakerOpenException}.
   *
   * @throws NullPointerException if the {@code runnable} is null
   * @throws RejectedExecutionException if the {@code runnable} cannot be scheduled for execution
   */
  public CompletableFuture<Void> runAsync(CheckedRunnable runnable) {
    return callAsync(execution -> Functions.promiseOf(runnable, execution), false);
  }

  /**
   * Executes the {@code runnable} asynchronously until successful or until the configured policies are exceeded.
   * <p>
   * If a configured circuit breaker is open, the resulting future is completed with {@link
   * CircuitBreakerOpenException}.
   *
   * @throws NullPointerException if the {@code runnable} is null
   * @throws RejectedExecutionException if the {@code runnable} cannot be scheduled for execution
   */
  public CompletableFuture<Void> runAsync(ContextualRunnable runnable) {
    return callAsync(execution -> Functions.promiseOf(runnable, execution), false);
  }

  /**
   * Executes the {@code runnable} asynchronously until successful or until the configured policies are exceeded. This
   * method is intended for integration with asynchronous code. Retries must be manually scheduled via one of the {@code
   * AsyncExecution.retry} methods.
   * <p>
   * If a configured circuit breaker is open, the resulting future is completed with {@link
   * CircuitBreakerOpenException}.
   *
   * @throws NullPointerException if the {@code runnable} is null
   * @throws RejectedExecutionException if the {@code runnable} cannot be scheduled for execution
   */
  public CompletableFuture<Void> runAsyncExecution(AsyncRunnable runnable) {
    return callAsync(execution -> Functions.asyncOfExecution(runnable, execution), true);
  }

  /**
   * Configures the {@code executor} to use for performing asynchronous executions and listener callbacks.
   *
   * @throws NullPointerException if {@code executor} is null
   */
  public FailsafeExecutor<R> with(ScheduledExecutorService executor) {
    this.scheduler = Scheduler.of(executor);
    return this;
  }

  /**
   * Configures the {@code executor} to use for performing asynchronous executions and listener callbacks. For
   * executions that require a delay, an internal ScheduledExecutorService will be used for the delay, then the {@code
   * executor} will be used for actual execution.
   *
   * @throws NullPointerException if {@code executor} is null
   */
  public FailsafeExecutor<R> with(ExecutorService executor) {
    this.scheduler = Scheduler.of(executor);
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
   * Calls the {@code supplier} synchronously, handling results according to the configured policies.
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
    if (result.getFailure() != null)
      throw result.getFailure() instanceof RuntimeException ?
          (RuntimeException) result.getFailure() :
          new FailsafeException(result.getFailure());
    return (T) result.getResult();
  }

  /**
   * Calls the asynchronous {@code supplier} via the configured Scheduler, handling results according to the configured
   * policies.
   * <p>
   * If a configured circuit breaker is open, the resulting future is completed with {@link
   * CircuitBreakerOpenException}.
   *
   * @param asyncExecution whether this is a detached, async execution that must be manually completed
   * @throws NullPointerException if the {@code supplierFn} is null
   * @throws RejectedExecutionException if the {@code supplierFn} cannot be scheduled for execution
   */
  @SuppressWarnings("unchecked")
  private <T> CompletableFuture<T> callAsync(
      Function<AsyncExecution, Supplier<CompletableFuture<ExecutionResult>>> supplierFn, boolean asyncExecution) {
    FailsafeFuture<T> future = new FailsafeFuture(this);
    AsyncExecution execution = new AsyncExecution(scheduler, future, this);
    future.inject(execution);
    execution.inject(supplierFn.apply(execution), asyncExecution);
    execution.executeAsync(asyncExecution);
    return future;
  }
}