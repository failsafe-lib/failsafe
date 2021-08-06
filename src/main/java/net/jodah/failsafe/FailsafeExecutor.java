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
import net.jodah.failsafe.util.concurrent.Scheduler;

import java.util.List;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Supplier;

import static net.jodah.failsafe.Functions.*;

/**
 * <p>
 * An executor that handles failures according to configured {@link FailurePolicy policies}. Can be created via {@link
 * Failsafe#with(Policy[])}.
 * <p>
 * Async executions are run by default on the {@link ForkJoinPool#commonPool()}. Alternative executors can be configured
 * via {@link #with(ScheduledExecutorService)} and similar methods. All async executions are cancellable and
 * interruptable via the returned CompletableFuture, even those run by a {@link ForkJoinPool} or {@link
 * CompletionStage}.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public class FailsafeExecutor<R> extends PolicyListeners<FailsafeExecutor<R>, R> {
  private Scheduler scheduler = Scheduler.DEFAULT;
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
   * @throws FailsafeException if the {@code supplier} fails with a checked Exception. {@link
   * FailsafeException#getCause()} can be used to learn the checked exception that caused the failure.
   * @throws TimeoutExceededException if a configured {@link Timeout} is exceeded.
   * @throws CircuitBreakerOpenException if a configured {@link CircuitBreaker} is open.
   */
  public <T extends R> T get(CheckedSupplier<T> supplier) {
    return call(execution -> Assert.notNull(supplier, "supplier"));
  }

  /**
   * Executes the {@code supplier} until a successful result is returned or the configured policies are exceeded.
   *
   * @throws NullPointerException if the {@code supplier} is null
   * @throws FailsafeException if the {@code supplier} fails with a checked Exception. {@link
   * FailsafeException#getCause()} can be used to learn the checked exception that caused the failure.
   * @throws TimeoutExceededException if a configured {@link Timeout} is exceeded.
   * @throws CircuitBreakerOpenException if a configured {@link CircuitBreaker} is open.
   */
  public <T extends R> T get(ContextualSupplier<T> supplier) {
    return call(execution -> toSupplier(supplier, execution));
  }

  /**
   * Executes the {@code supplier} asynchronously until a successful result is returned or the configured policies are
   * exceeded.
   * <p>
   * If a configured {@link Timeout} is exceeded, the resulting future is completed exceptionally with {@link
   * TimeoutExceededException}.
   * </p>
   * <p>
   * If a configured {@link CircuitBreaker} is open, the resulting future is completed exceptionally with {@link
   * CircuitBreakerOpenException}.
   * </p>
   *
   * @throws NullPointerException if the {@code supplier} is null
   * @throws RejectedExecutionException if the {@code supplier} cannot be scheduled for execution
   */
  public <T extends R> CompletableFuture<T> getAsync(CheckedSupplier<T> supplier) {
    return callAsync(execution -> getPromise(toCtxSupplier(supplier), execution), false);
  }

  /**
   * Executes the {@code supplier} asynchronously until a successful result is returned or the configured policies are
   * exceeded.
   * <p>
   * If a configured {@link Timeout} is exceeded, the resulting future is completed exceptionally with {@link
   * TimeoutExceededException}.
   * </p>
   * <p>
   * If a configured {@link CircuitBreaker} is open, the resulting future is completed exceptionally with {@link
   * CircuitBreakerOpenException}.
   * </p>
   *
   * @throws NullPointerException if the {@code supplier} is null
   * @throws RejectedExecutionException if the {@code supplier} cannot be scheduled for execution
   */
  public <T extends R> CompletableFuture<T> getAsync(ContextualSupplier<T> supplier) {
    return callAsync(execution -> getPromise(supplier, execution), false);
  }

  /**
   * Executes the {@code supplier} asynchronously until a successful result is returned or the configured policies are
   * exceeded. This method is intended for integration with asynchronous code. Retries must be manually scheduled via
   * one of the {@code AsyncExecution.retry} methods.
   * <p>
   * If a configured {@link Timeout} is exceeded, the resulting future is completed exceptionally with {@link
   * TimeoutExceededException}.
   * </p>
   * <p>
   * If a configured {@link CircuitBreaker} is open, the resulting future is completed exceptionally with {@link
   * CircuitBreakerOpenException}.
   * </p>
   *
   * @throws NullPointerException if the {@code supplier} is null
   * @throws RejectedExecutionException if the {@code supplier} cannot be scheduled for execution
   */
  public <T extends R> CompletableFuture<T> getAsyncExecution(AsyncSupplier<T> supplier) {
    return callAsync(execution -> getPromiseExecution(supplier, execution), true);
  }

  /**
   * Executes the {@code supplier} asynchronously until the resulting future is successfully completed or the configured
   * policies are exceeded.
   * <p>
   * If a configured {@link Timeout} is exceeded, the resulting future is completed exceptionally with {@link
   * TimeoutExceededException}.
   * </p>
   * <p>
   * If a configured {@link CircuitBreaker} is open, the resulting future is completed exceptionally with {@link
   * CircuitBreakerOpenException}.
   * </p>
   *
   * @throws NullPointerException if the {@code supplier} is null
   * @throws RejectedExecutionException if the {@code supplier} cannot be scheduled for execution
   */
  public <T extends R> CompletableFuture<T> getStageAsync(CheckedSupplier<? extends CompletionStage<T>> supplier) {
    return callAsync(execution -> getPromiseOfStage(toCtxSupplier(supplier), execution), false);
  }

  /**
   * Executes the {@code supplier} asynchronously until the resulting future is successfully completed or the configured
   * policies are exceeded.
   * <p>
   * If a configured {@link Timeout} is exceeded, the resulting future is completed exceptionally with {@link
   * TimeoutExceededException}.
   * </p>
   * <p>
   * If a configured {@link CircuitBreaker} is open, the resulting future is completed exceptionally with {@link
   * CircuitBreakerOpenException}.
   * </p>
   *
   * @throws NullPointerException if the {@code supplier} is null
   * @throws RejectedExecutionException if the {@code supplier} cannot be scheduled for execution
   */
  public <T extends R> CompletableFuture<T> getStageAsync(ContextualSupplier<? extends CompletionStage<T>> supplier) {
    return callAsync(execution -> getPromiseOfStage(supplier, execution), false);
  }

  /**
   * Executes the {@code supplier} asynchronously until the resulting future is successfully completed or the configured
   * policies are exceeded. This method is intended for integration with asynchronous code. Retries must be manually
   * scheduled via one of the {@code AsyncExecution.retry} methods.
   * <p>
   * If a configured {@link Timeout} is exceeded, the resulting future is completed exceptionally with {@link
   * TimeoutExceededException}.
   * </p>
   * <p>
   * If a configured {@link CircuitBreaker} is open, the resulting future is completed exceptionally with {@link
   * CircuitBreakerOpenException}.
   * </p>
   *
   * @throws NullPointerException if the {@code supplier} is null
   * @throws RejectedExecutionException if the {@code supplier} cannot be scheduled for execution
   */
  public <T extends R> CompletableFuture<T> getStageAsyncExecution(
    AsyncSupplier<? extends CompletionStage<T>> supplier) {
    return callAsync(execution -> getPromiseOfStageExecution(supplier, execution), true);
  }

  /**
   * Executes the {@code runnable} until successful or until the configured policies are exceeded.
   *
   * @throws NullPointerException if the {@code runnable} is null
   * @throws FailsafeException if the {@code runnable} fails with a checked Exception. {@link
   * FailsafeException#getCause()} can be used to learn the checked exception that caused the failure.
   * @throws TimeoutExceededException if a configured {@link Timeout} is exceeded.
   * @throws CircuitBreakerOpenException if a configured {@link CircuitBreaker} is open.
   */
  public void run(CheckedRunnable runnable) {
    call(execution -> toSupplier(runnable));
  }

  /**
   * Executes the {@code runnable} until successful or until the configured policies are exceeded.
   *
   * @throws NullPointerException if the {@code runnable} is null
   * @throws FailsafeException if the {@code runnable} fails with a checked Exception. {@link
   * FailsafeException#getCause()} can be used to learn the checked exception that caused the failure.
   * @throws TimeoutExceededException if a configured {@link Timeout} is exceeded.
   * @throws CircuitBreakerOpenException if a configured {@link CircuitBreaker} is open.
   */
  public void run(ContextualRunnable runnable) {
    call(execution -> toSupplier(runnable, execution));
  }

  /**
   * Executes the {@code runnable} asynchronously until successful or until the configured policies are exceeded.
   * <p>
   * If a configured {@link Timeout} is exceeded, the resulting future is completed exceptionally with {@link
   * TimeoutExceededException}.
   * </p>
   * <p>
   * If a configured {@link CircuitBreaker} is open, the resulting future is completed exceptionally with {@link
   * CircuitBreakerOpenException}.
   * </p>
   *
   * @throws NullPointerException if the {@code runnable} is null
   * @throws RejectedExecutionException if the {@code runnable} cannot be scheduled for execution
   */
  public CompletableFuture<Void> runAsync(CheckedRunnable runnable) {
    return callAsync(execution -> getPromise(toCtxSupplier(runnable), execution), false);
  }

  /**
   * Executes the {@code runnable} asynchronously until successful or until the configured policies are exceeded.
   * <p>
   * If a configured {@link Timeout} is exceeded, the resulting future is completed exceptionally with {@link
   * TimeoutExceededException}.
   * </p>
   * <p>
   * If a configured {@link CircuitBreaker} is open, the resulting future is completed exceptionally with {@link
   * CircuitBreakerOpenException}.
   * </p>
   *
   * @throws NullPointerException if the {@code runnable} is null
   * @throws RejectedExecutionException if the {@code runnable} cannot be scheduled for execution
   */
  public CompletableFuture<Void> runAsync(ContextualRunnable runnable) {
    return callAsync(execution -> getPromise(toCtxSupplier(runnable), execution), false);
  }

  /**
   * Executes the {@code runnable} asynchronously until successful or until the configured policies are exceeded. This
   * method is intended for integration with asynchronous code. Retries must be manually scheduled via one of the {@code
   * AsyncExecution.retry} methods.
   * <p>
   * If a configured {@link Timeout} is exceeded, the resulting future is completed exceptionally with {@link
   * TimeoutExceededException}.
   * </p>
   * <p>
   * If a configured {@link CircuitBreaker} is open, the resulting future is completed exceptionally with {@link
   * CircuitBreakerOpenException}.
   * </p>
   *
   * @throws NullPointerException if the {@code runnable} is null
   * @throws RejectedExecutionException if the {@code runnable} cannot be scheduled for execution
   */
  public CompletableFuture<Void> runAsyncExecution(AsyncRunnable runnable) {
    return callAsync(execution -> getPromiseExecution(toAsyncSupplier(runnable), execution), true);
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
   * <p>Note: Any exceptions that are thrown from within the {@code listener} are ignored. To provide an alternative
   * result for a failed execution, use a {@link Fallback}.</p>
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

  void handleComplete(ExecutionResult result, AbstractExecution execution) {
    if (successListener != null && result.getSuccessAll())
      successListener.handle(result, execution);
    else if (failureListener != null && !result.getSuccessAll())
      failureListener.handle(result, execution);
    if (completeListener != null)
      completeListener.handle(result, execution);
  }

  /**
   * Configures the {@code executor} to use for performing asynchronous executions and listener callbacks.
   * <p>
   * Note: The {@code executor} should have a core pool size of at least 2 in order for {@link Timeout timeouts} to
   * work.
   * </p>
   *
   * @throws NullPointerException if {@code executor} is null
   * @throws IllegalArgumentException if the {@code executor} has a core pool size of less than 2
   */
  public FailsafeExecutor<R> with(ScheduledExecutorService executor) {
    this.scheduler = Scheduler.of(executor);
    return this;
  }

  /**
   * Configures the {@code executor} to use for performing asynchronous executions and listener callbacks. For
   * executions that require a delay, an internal ScheduledExecutorService will be used for the delay, then the {@code
   * executor} will be used for actual execution.
   * <p>
   * Note: The {@code executor} should have a core pool size or parallelism of at least 2 in order for {@link Timeout
   * timeouts} to work.
   * </p>
   *
   * @throws NullPointerException if {@code executor} is null
   */
  public FailsafeExecutor<R> with(Executor executor) {
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
   * @throws FailsafeException if the {@code supplierFn} fails with a checked Exception or if interrupted while waiting
   * to perform a retry.
   * @throws TimeoutExceededException if a configured {@link Timeout} is exceeded.
   * @throws CircuitBreakerOpenException if a configured {@link CircuitBreaker} is open.
   */
  @SuppressWarnings("unchecked")
  private <T> T call(Function<Execution, CheckedSupplier<?>> supplierFn) {
    Execution execution = new Execution(this);
    Supplier<ExecutionResult> supplier = Functions.get(supplierFn.apply(execution), execution);

    ExecutionResult result = execution.executeSync(supplier);
    Throwable failure = result.getFailure();
    if (failure != null) {
      if (failure instanceof RuntimeException)
        throw (RuntimeException) failure;
      if (failure instanceof Error)
        throw (Error) failure;
      throw new FailsafeException(failure);
    }
    return (T) result.getResult();
  }

  /**
   * Calls the asynchronous {@code supplier} via the configured Scheduler, handling results according to the configured
   * policies.
   * <p>
   * If a configured {@link Timeout} is exceeded, the resulting future is completed exceptionally with {@link
   * TimeoutExceededException}.
   * </p>
   * <p>
   * If a configured {@link CircuitBreaker} is open, the resulting future is completed exceptionally with {@link
   * CircuitBreakerOpenException}.
   * </p>
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