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

import dev.failsafe.event.EventListener;
import dev.failsafe.event.ExecutionCompletedEvent;
import dev.failsafe.function.*;
import dev.failsafe.internal.EventHandler;
import dev.failsafe.internal.util.Assert;
import dev.failsafe.spi.AsyncExecutionInternal;
import dev.failsafe.spi.ExecutionResult;
import dev.failsafe.spi.FailsafeFuture;
import dev.failsafe.spi.Scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static dev.failsafe.Functions.*;

/**
 * <p>
 * An executor that handles failures according to configured {@link FailurePolicyBuilder policies}. Can be created via
 * {@link Failsafe#with(Policy, Policy[])} to support policy based execution failure handling, or {@link
 * Failsafe#none()} to support execution with no failure handling.
 * <p>
 * Async executions are run by default on the {@link ForkJoinPool#commonPool()}. Alternative executors can be configured
 * via {@link #with(ScheduledExecutorService)} and similar methods. All async executions are cancellable and
 * interruptable via the returned CompletableFuture, even those run by a {@link ForkJoinPool} or {@link
 * CompletionStage}.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public class FailsafeExecutor<R> {
  private Scheduler scheduler = Scheduler.DEFAULT;
  private Executor executor;
  /** Policies sorted outer-most first */
  final List<? extends Policy<R>> policies;
  private EventHandler<R> completeHandler;
  private volatile EventHandler<R> failureHandler;
  private volatile EventHandler<R> successHandler;

  /**
   * @throws IllegalArgumentException if {@code policies} is empty
   */
  FailsafeExecutor(List<? extends Policy<R>> policies) {
    this.policies = policies;
  }

  /**
   * Returns the currently configured policies.
   *
   * @see #compose(Policy)
   */
  public List<? extends Policy<R>> getPolicies() {
    return policies;
  }

  /**
   * Returns a new {@code FailsafeExecutor} that composes the currently configured policies around the given {@code
   * innerPolicy}. For example, consider:
   * <p>
   * <pre>
   *   Failsafe.with(fallback).compose(retryPolicy).compose(circuitBreaker);
   * </pre>
   * </p>
   * This results in the following internal composition when executing a {@code runnable} or {@code supplier} and
   * handling its result:
   * <p>
   * <pre>
   *   Fallback(RetryPolicy(CircuitBreaker(Supplier)))
   * </pre>
   * </p>
   * This means the {@code CircuitBreaker} is first to evaluate the {@code Supplier}'s result, then the {@code
   * RetryPolicy}, then the {@code Fallback}. Each policy makes its own determination as to whether the result
   * represents a failure. This allows different policies to be used for handling different types of failures.
   *
   * @throws NullPointerException if {@code innerPolicy} is null
   * @see #getPolicies()
   */
  public <P extends Policy<R>> FailsafeExecutor<R> compose(P innerPolicy) {
    Assert.notNull(innerPolicy, "innerPolicy");
    List<Policy<R>> composed = new ArrayList<>(policies);
    composed.add(innerPolicy);
    return new FailsafeExecutor<>(composed);
  }

  /**
   * Executes the {@code supplier} until a successful result is returned or the configured policies are exceeded.
   *
   * @throws NullPointerException if the {@code supplier} is null
   * @throws FailsafeException if the {@code supplier} fails with a checked Exception. {@link
   * FailsafeException#getCause()} can be used to learn the checked exception that caused the failure.
   * @throws TimeoutExceededException if the execution fails because {@link Timeout} is exceeded.
   * @throws CircuitBreakerOpenException if the execution fails because {@link CircuitBreaker} is open.
   */
  public <T extends R> T get(CheckedSupplier<T> supplier) {
    return call(toCtxSupplier(supplier));
  }

  /**
   * Executes the {@code supplier} until a successful result is returned or the configured policies are exceeded.
   *
   * @throws NullPointerException if the {@code supplier} is null
   * @throws FailsafeException if the {@code supplier} fails with a checked Exception. {@link
   * FailsafeException#getCause()} can be used to learn the checked exception that caused the failure.
   * @throws TimeoutExceededException if the execution fails because {@link Timeout} is exceeded.
   * @throws CircuitBreakerOpenException if the execution fails because {@link CircuitBreaker} is open.
   */
  public <T extends R> T get(ContextualSupplier<T, T> supplier) {
    return call(Assert.notNull(supplier, "supplier"));
  }

  /**
   * Executes the {@code supplier} asynchronously until a successful result is returned or the configured policies are
   * exceeded.
   * <p>
   * If the execution fails because a {@link Timeout} is exceeded, the resulting future is completed exceptionally with
   * {@link TimeoutExceededException}.
   * </p>
   * <p>
   * If the execution fails because a {@link CircuitBreaker} is open, the resulting future is completed exceptionally
   * with {@link CircuitBreakerOpenException}.
   * </p>
   *
   * @throws NullPointerException if the {@code supplier} is null
   * @throws RejectedExecutionException if the {@code supplier} cannot be scheduled for execution
   */
  public <T extends R> CompletableFuture<T> getAsync(CheckedSupplier<T> supplier) {
    return callAsync(future -> getPromise(toCtxSupplier(supplier), executor), false);
  }

  /**
   * Executes the {@code supplier} asynchronously until a successful result is returned or the configured policies are
   * exceeded.
   * <p>
   * If the execution fails because a {@link Timeout} is exceeded, the resulting future is completed exceptionally with
   * {@link TimeoutExceededException}.
   * </p>
   * <p>
   * If the execution fails because a {@link CircuitBreaker} is open, the resulting future is completed exceptionally
   * with {@link CircuitBreakerOpenException}.
   * </p>
   *
   * @throws NullPointerException if the {@code supplier} is null
   * @throws RejectedExecutionException if the {@code supplier} cannot be scheduled for execution
   */
  public <T extends R> CompletableFuture<T> getAsync(ContextualSupplier<T, T> supplier) {
    return callAsync(future -> getPromise(supplier, executor), false);
  }

  /**
   * This method is intended for integration with asynchronous code.
   * <p>
   * Executes the {@code runnable} asynchronously until a successful result is recorded or the configured policies are
   * exceeded. Executions must be recorded via one of the {@code AsyncExecution.record} methods which will trigger
   * failure handling, if needed, by the configured policies, else the resulting {@link CompletableFuture} will be
   * completed. Any exception that is thrown from the {@code runnable} will automatically be recorded via {@code
   * AsyncExecution.recordFailure}.
   * </p>
   * <p>
   * If the execution fails because a {@link Timeout} is exceeded, the resulting future is completed exceptionally with
   * {@link TimeoutExceededException}.
   * </p>
   * <p>
   * If the execution fails because a {@link CircuitBreaker} is open, the resulting future is completed exceptionally
   * with {@link CircuitBreakerOpenException}.
   * </p>
   *
   * @throws NullPointerException if the {@code supplier} is null
   * @throws RejectedExecutionException if the {@code supplier} cannot be scheduled for execution
   */
  public <T extends R> CompletableFuture<T> getAsyncExecution(AsyncRunnable<T> runnable) {
    return callAsync(future -> getPromiseExecution(runnable, executor), true);
  }

  /**
   * Executes the {@code supplier} asynchronously until the resulting future is successfully completed or the configured
   * policies are exceeded.
   * <p>Cancelling the resulting {@link CompletableFuture} will automatically cancels the supplied {@link
   * CompletionStage} if it's a {@link Future}.</p>
   * <p>
   * If the execution fails because a {@link Timeout} is exceeded, the resulting future is completed exceptionally with
   * {@link TimeoutExceededException}.
   * </p>
   * <p>
   * If the execution fails because a {@link CircuitBreaker} is open, the resulting future is completed exceptionally
   * with {@link CircuitBreakerOpenException}.
   * </p>
   *
   * @throws NullPointerException if the {@code supplier} is null
   * @throws RejectedExecutionException if the {@code supplier} cannot be scheduled for execution
   */
  public <T extends R> CompletableFuture<T> getStageAsync(CheckedSupplier<? extends CompletionStage<T>> supplier) {
    return callAsync(future -> getPromiseOfStage(toCtxSupplier(supplier), future), false);
  }

  /**
   * Executes the {@code supplier} asynchronously until the resulting future is successfully completed or the configured
   * policies are exceeded.
   * <p>Cancelling the resulting {@link CompletableFuture} will automatically cancels the supplied {@link
   * CompletionStage} if it's a {@link Future}.</p>
   * <p>
   * If the execution fails because a {@link Timeout} is exceeded, the resulting future is completed exceptionally with
   * {@link TimeoutExceededException}.
   * </p>
   * <p>
   * If the execution fails because a {@link CircuitBreaker} is open, the resulting future is completed exceptionally
   * with {@link CircuitBreakerOpenException}.
   * </p>
   *
   * @throws NullPointerException if the {@code supplier} is null
   * @throws RejectedExecutionException if the {@code supplier} cannot be scheduled for execution
   */
  public <T extends R> CompletableFuture<T> getStageAsync(
    ContextualSupplier<T, ? extends CompletionStage<T>> supplier) {
    return callAsync(future -> getPromiseOfStage(supplier, future), false);
  }

  /**
   * Executes the {@code runnable} until successful or until the configured policies are exceeded.
   *
   * @throws NullPointerException if the {@code runnable} is null
   * @throws FailsafeException if the {@code runnable} fails with a checked Exception. {@link
   * FailsafeException#getCause()} can be used to learn the checked exception that caused the failure.
   * @throws TimeoutExceededException if the execution fails because {@link Timeout} is exceeded.
   * @throws CircuitBreakerOpenException if the execution fails because {@link CircuitBreaker} is open.
   */
  public void run(CheckedRunnable runnable) {
    call(toCtxSupplier(runnable));
  }

  /**
   * Executes the {@code runnable} until successful or until the configured policies are exceeded.
   *
   * @throws NullPointerException if the {@code runnable} is null
   * @throws FailsafeException if the {@code runnable} fails with a checked Exception. {@link
   * FailsafeException#getCause()} can be used to learn the checked exception that caused the failure.
   * @throws TimeoutExceededException if the execution fails because {@link Timeout} is exceeded.
   * @throws CircuitBreakerOpenException if the execution fails because {@link CircuitBreaker} is open.
   */
  public void run(ContextualRunnable<Void> runnable) {
    call(toCtxSupplier(runnable));
  }

  /**
   * Executes the {@code runnable} asynchronously until successful or until the configured policies are exceeded.
   * <p>
   * If the execution fails because a {@link Timeout} is exceeded, the resulting future is completed exceptionally with
   * {@link TimeoutExceededException}.
   * </p>
   * <p>
   * If the execution fails because a {@link CircuitBreaker} is open, the resulting future is completed exceptionally
   * with {@link CircuitBreakerOpenException}.
   * </p>
   *
   * @throws NullPointerException if the {@code runnable} is null
   * @throws RejectedExecutionException if the {@code runnable} cannot be scheduled for execution
   */
  public CompletableFuture<Void> runAsync(CheckedRunnable runnable) {
    return callAsync(future -> getPromise(toCtxSupplier(runnable), executor), false);
  }

  /**
   * Executes the {@code runnable} asynchronously until successful or until the configured policies are exceeded.
   * <p>
   * If the execution fails because a {@link Timeout} is exceeded, the resulting future is completed exceptionally with
   * {@link TimeoutExceededException}.
   * </p>
   * <p>
   * If the execution fails because a {@link CircuitBreaker} is open, the resulting future is completed exceptionally
   * with {@link CircuitBreakerOpenException}.
   * </p>
   *
   * @throws NullPointerException if the {@code runnable} is null
   * @throws RejectedExecutionException if the {@code runnable} cannot be scheduled for execution
   */
  public CompletableFuture<Void> runAsync(ContextualRunnable<Void> runnable) {
    return callAsync(future -> getPromise(toCtxSupplier(runnable), executor), false);
  }

  /**
   * This method is intended for integration with asynchronous code.
   * <p>
   * Executes the {@code runnable} asynchronously until a successful result is recorded or the configured policies are
   * exceeded. Executions must be recorded via one of the {@code AsyncExecution.record} methods which will trigger
   * failure handling, if needed, by the configured policies, else the resulting {@link CompletableFuture} will be
   * completed. Any exception that is thrown from the {@code runnable} will automatically be recorded via {@code
   * AsyncExecution.recordFailure}.
   * </p>
   * <p>
   * If the execution fails because a {@link Timeout} is exceeded, the resulting future is completed exceptionally with
   * {@link TimeoutExceededException}.
   * </p>
   * <p>
   * If the execution fails because a {@link CircuitBreaker} is open, the resulting future is completed exceptionally
   * with {@link CircuitBreakerOpenException}.
   * </p>
   *
   * @throws NullPointerException if the {@code runnable} is null
   * @throws RejectedExecutionException if the {@code runnable} cannot be scheduled for execution
   */
  public CompletableFuture<Void> runAsyncExecution(AsyncRunnable<Void> runnable) {
    return callAsync(future -> getPromiseExecution(runnable, executor), true);
  }

  /**
   * Registers the {@code listener} to be called when an execution is complete. This occurs when an execution is
   * successful according to all policies, or all policies have been exceeded.
   * <p>Note: Any exceptions that are thrown from within the {@code listener} are ignored.</p>
   */
  public FailsafeExecutor<R> onComplete(EventListener<ExecutionCompletedEvent<R>> listener) {
    completeHandler = EventHandler.ofExecutionCompleted(Assert.notNull(listener, "listener"));
    return this;
  }

  /**
   * Registers the {@code listener} to be called when an execution fails. This occurs when the execution fails according
   * to some policy, and all policies have been exceeded.
   * <p>Note: Any exceptions that are thrown from within the {@code listener} are ignored. To provide an alternative
   * result for a failed execution, use a {@link Fallback}.</p>
   */
  public FailsafeExecutor<R> onFailure(EventListener<ExecutionCompletedEvent<R>> listener) {
    failureHandler = EventHandler.ofExecutionCompleted(Assert.notNull(listener, "listener"));
    return this;
  }

  /**
   * Registers the {@code listener} to be called when an execution is successful. If multiple policies, are configured,
   * this handler is called when execution is complete and <i>all</i> policies succeed. If <i>all</i> policies do not
   * succeed, then the {@link #onFailure(EventListener)} registered listener is called instead.
   * <p>Note: Any exceptions that are thrown from within the {@code listener} are ignored.</p>
   */
  public FailsafeExecutor<R> onSuccess(EventListener<ExecutionCompletedEvent<R>> listener) {
    successHandler = EventHandler.ofExecutionCompleted(Assert.notNull(listener, "listener"));
    return this;
  }

  /**
   * Configures the {@code scheduledExecutorService} to use for performing asynchronous executions and listener
   * callbacks.
   * <p>
   * Note: The {@code scheduledExecutorService} should have a core pool size of at least 2 in order for {@link Timeout
   * timeouts} to work.
   * </p>
   *
   * @throws NullPointerException if {@code scheduledExecutorService} is null
   * @throws IllegalArgumentException if the {@code scheduledExecutorService} has a core pool size of less than 2
   */
  public FailsafeExecutor<R> with(ScheduledExecutorService scheduledExecutorService) {
    this.scheduler = Scheduler.of(Assert.notNull(scheduledExecutorService, "scheduledExecutorService"));
    return this;
  }

  /**
   * Configures the {@code executorService} to use for performing asynchronous executions and listener callbacks. For
   * async executions that require a delay, an internal ScheduledExecutorService will be used for the delay, then the
   * {@code executorService} will be used for actual execution.
   * <p>
   * Note: The {@code executorService} should have a core pool size or parallelism of at least 2 in order for {@link
   * Timeout timeouts} to work.
   * </p>
   *
   * @throws NullPointerException if {@code executorService} is null
   */
  public FailsafeExecutor<R> with(ExecutorService executorService) {
    this.scheduler = Scheduler.of(Assert.notNull(executorService, "executorService"));
    return this;
  }

  /**
   * Configures the {@code executor} to use as a wrapper around executions. If the {@code executor} is actually an
   * instance of {@link ExecutorService}, then the {@code executor} will be configured via {@link
   * #with(ExecutorService)} instead.
   * <p>
   * The {@code executor} is responsible for propagating executions. Executions that normally return a result, such as
   * {@link #get(CheckedSupplier)} will return {@code null} since the {@link Executor} interface does not support
   * results.
   * </p>
   * <p>The {@code executor} will not be used for {@link #getStageAsync(CheckedSupplier) getStageAsync} calls since
   * those require a returned result.
   * </p>
   *
   * @throws NullPointerException if {@code executor} is null
   */
  public FailsafeExecutor<R> with(Executor executor) {
    Assert.notNull(executor, "executor");
    if (executor instanceof ExecutorService)
      with((ExecutorService) executor);
    else
      this.executor = executor;
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
   * Calls the {@code innerSupplier} synchronously, handling results according to the configured policies.
   *
   * @throws FailsafeException if the {@code innerSupplier} fails with a checked Exception or if interrupted while
   * waiting to perform a retry.
   * @throws TimeoutExceededException if the execution fails because {@link Timeout} is exceeded.
   * @throws CircuitBreakerOpenException if the execution fails because {@link CircuitBreaker} is open.
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  private <T> T call(ContextualSupplier<T, T> innerSupplier) {
    SyncExecutionImpl<T> execution = new SyncExecutionImpl(this, scheduler, Functions.get(innerSupplier, executor));
    ExecutionResult<T> result = execution.executeSync();
    Throwable failure = result.getFailure();
    if (failure != null) {
      if (failure instanceof RuntimeException)
        throw (RuntimeException) failure;
      if (failure instanceof Error)
        throw (Error) failure;
      throw new FailsafeException(failure);
    }
    return result.getResult();
  }

  /**
   * Calls the asynchronous {@code innerFn} via the configured Scheduler, handling results according to the configured
   * policies.
   * <p>
   * If the execution fails because a {@link Timeout} is exceeded, the resulting future is completed exceptionally with
   * {@link TimeoutExceededException}.
   * </p>
   * <p>
   * If the execution fails because a {@link CircuitBreaker} is open, the resulting future is completed exceptionally
   * with {@link CircuitBreakerOpenException}.
   * </p>
   *
   * @param asyncExecution whether this is a detached, async execution that must be manually completed
   * @throws NullPointerException if the {@code innerFn} is null
   * @throws RejectedExecutionException if the {@code innerFn} cannot be scheduled for execution
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  private <T> CompletableFuture<T> callAsync(
    Function<FailsafeFuture<T>, Function<AsyncExecutionInternal<T>, CompletableFuture<ExecutionResult<T>>>> innerFn,
    boolean asyncExecution) {

    FailsafeFuture<T> future = new FailsafeFuture(completionHandler);
    AsyncExecutionImpl<T> execution = new AsyncExecutionImpl(policies, scheduler, future, asyncExecution,
      innerFn.apply(future));
    future.setExecution(execution);
    execution.executeAsync();
    return future;
  }

  final BiConsumer<ExecutionResult<R>, ExecutionContext<R>> completionHandler = (result, context) -> {
    if (successHandler != null && result.getSuccessAll())
      successHandler.handle(result, context);
    else if (failureHandler != null && !result.getSuccessAll())
      failureHandler.handle(result, context);
    if (completeHandler != null)
      completeHandler.handle(result, context);
  };
}