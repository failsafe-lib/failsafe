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

import net.jodah.failsafe.Functions.SettableSupplier;
import net.jodah.failsafe.internal.util.Assert;
import net.jodah.failsafe.util.concurrent.Scheduler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Tracks asynchronous executions and allows retries to be scheduled according to a {@link RetryPolicy}. May be
 * explicitly completed or made to retry.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public final class AsyncExecution<R> extends AbstractExecution<R> {
  private SettableSupplier<CompletableFuture<ExecutionResult>> innerExecutionSupplier;
  private Supplier<CompletableFuture<ExecutionResult>> outerExecutionSupplier;
  final FailsafeFuture<R> future;
  private volatile boolean completeCalled;
  private volatile boolean retryCalled;

  AsyncExecution(Scheduler scheduler, FailsafeFuture<R> future, FailsafeExecutor<R> executor) {
    super(scheduler, executor);
    this.future = future;
  }

  void inject(Supplier<CompletableFuture<ExecutionResult>> syncSupplier, boolean asyncExecution) {
    if (!asyncExecution) {
      outerExecutionSupplier = Functions.getPromiseAsync(syncSupplier, scheduler, this);
    } else {
      outerExecutionSupplier = innerExecutionSupplier = Functions.toSettableSupplier(syncSupplier);
    }

    for (PolicyExecutor<R, Policy<R>> policyExecutor : policyExecutors)
      outerExecutionSupplier = policyExecutor.supplyAsync(outerExecutionSupplier, scheduler, this.future);
  }

  /**
   * Completes the execution and the associated {@code CompletableFuture}.
   *
   * @throws IllegalStateException if the execution is already complete
   */
  public void complete() {
    postExecute(ExecutionResult.NONE);
  }

  /**
   * Attempts to complete the execution and the associated {@code CompletableFuture} with the {@code result}. Returns
   * true on success, else false if completion failed and the execution should be retried via {@link #retry()}.
   *
   * @throws IllegalStateException if the execution is already complete
   * @deprecated Use {@link #recordResult(Object)} instead
   */
  public boolean complete(R result) {
    postExecute(new ExecutionResult(result, null));
    return completed;
  }

  /**
   * Attempts to complete the execution and the associated {@code CompletableFuture} with the {@code result} and {@code
   * failure}. Returns true on success, else false if completion failed and the execution should be retried via {@link
   * #retry()}.
   * <p>
   * Note: the execution may be completed even when the {@code failure} is not {@code null}, such as when the
   * RetryPolicy does not allow retries for the {@code failure}.
   *
   * @throws IllegalStateException if the execution is already complete
   * @deprecated Use {@link #record(Object, Throwable)} instead
   */
  public boolean complete(R result, Throwable failure) {
    postExecute(new ExecutionResult(result, failure));
    return completed;
  }

  /**
   * Records an execution {@code result} or {@code failure} which triggers failure handling, if needed, by the
   * configured policies. If policy handling is not possible or completed, the resulting {@link CompletableFuture} is
   * completed.
   *
   * @throws IllegalStateException if the most recent execution was already recorded or the execution is complete
   */
  public void record(R result, Throwable failure) {
    Assert.state(!retryCalled, "The most recent execution has already been recorded");
    retryCalled = true;
    completeOrHandle(result, failure);
  }

  /**
   * Records an execution {@code result} which triggers failure handling, if needed, by the configured policies. If
   * policy handling is not possible or completed, the resulting {@link CompletableFuture} is completed.
   *
   * @throws IllegalStateException if the most recent execution was already recorded or the execution is complete
   */
  public void recordResult(R result) {
    record(result, null);
  }

  /**
   * Records an execution {@code failure} which triggers failure handling, if needed, by the configured policies. If
   * policy handling is not possible or completed, the resulting {@link CompletableFuture} is completed.
   *
   * @throws IllegalStateException if the most recent execution was already recorded or the execution is complete
   */
  public void recordFailure(Throwable failure) {
    record(null, failure);
  }

  /**
   * Records an execution if one has not been recorded yet, attempts to schedule a retry if necessary, and returns
   * {@code true} if a retry has been scheduled else returns {@code false} and completes the execution and associated
   * {@code CompletableFuture}.
   *
   * @throws IllegalStateException if a retry method has already been called or the execution is already complete
   * @deprecated Retries will be performed automatically, if possible, when a result or failure is recorded
   */
  public boolean retry() {
    return retryFor(lastResult, lastFailure);
  }

  /**
   * Records an execution if one has not been recorded yet for the {@code result}, attempts to schedule a retry if
   * necessary, and returns {@code true} if a retry has been scheduled else returns {@code false} and completes the
   * execution and associated {@code CompletableFuture}.
   *
   * @throws IllegalStateException if a retry method has already been called or the execution is already complete
   * @deprecated Retries will be performed automatically, if possible, when a result or failure is recorded. Use {@link
   * #recordResult(Object)} instead
   */
  public boolean retryFor(R result) {
    return retryFor(result, null);
  }

  /**
   * Records an execution if one has not been recorded yet for the {@code result} or {@code failure}, attempts to
   * schedule a retry if necessary, and returns {@code true} if a retry has been scheduled else returns {@code false}
   * and completes the execution and associated {@code CompletableFuture}.
   *
   * @throws IllegalStateException if a retry method has already been called or the execution is already complete
   * @deprecated Retries will be performed automatically, if possible, when a result or failure is recorded. Use {@link
   * #record(Object, Throwable)} instead
   */
  public boolean retryFor(R result, Throwable failure) {
    Assert.state(!retryCalled, "Retry has already been called");
    retryCalled = true;
    return !completeOrHandle(result, failure);
  }

  /**
   * Records an execution and returns true if a retry has been scheduled for the {@code failure}, else returns false and
   * marks the execution and associated {@code CompletableFuture} as complete.
   *
   * @throws NullPointerException if {@code failure} is null
   * @throws IllegalStateException if a retry method has already been called or the execution is already complete
   * @deprecated Retries will be performed automatically, if possible, when a result or failure is recorded. Use {@link
   * #recordFailure(Throwable)} instead
   */
  public boolean retryOn(Throwable failure) {
    Assert.notNull(failure, "failure");
    return retryFor(null, failure);
  }

  /**
   * Prepares for an execution by resetting internal flags.
   */
  @Override
  void preExecute() {
    super.preExecute();
    completeCalled = false;
    retryCalled = false;
  }

  @Override
  boolean isAsyncExecution() {
    return innerExecutionSupplier != null;
  }

  /**
   * Externally called. Records an execution and performs post-execution handling for the {@code result} against all
   * configured policy executors. Attempts to complete the execution and returns the policy post execution result.
   *
   * @throws IllegalStateException if the execution is already complete
   */
  @Override
  ExecutionResult postExecute(ExecutionResult result) {
    synchronized (future) {
      if (!completeCalled) {
        result = super.postExecute(result);
        if (completed)
          complete(result, null);
        completeCalled = true;
        resultHandled = true;
      }

      return result;
    }
  }

  /**
   * Performs an asynchronous execution.
   *
   * @param asyncExecution whether this is a detached, async execution that must be manually completed
   */
  void executeAsync(boolean asyncExecution) {
    if (!asyncExecution)
      outerExecutionSupplier.get().whenComplete(this::complete);
    else {
      Future<?> scheduledSupply = scheduler.schedule(innerExecutionSupplier::get, 0, TimeUnit.NANOSECONDS);
      future.injectCancelFn((mayInterrupt, result) -> scheduledSupply.cancel(mayInterrupt));
    }
  }

  /**
   * Attempts to complete the execution else handle according to the configured policies. Returns {@code true} if the
   * execution was completed, else false which indicates the result was handled asynchronously and may have triggered a
   * retry.
   * <p>
   * Async executions begin by calling the user-provided Supplier. When this method is called, the result is set in the
   * inner-most supplier. Then the outer-most supplier is called to trigger policy execution.
   *
   * @throws IllegalStateException if the execution is already complete
   */
  boolean completeOrHandle(R result, Throwable failure) {
    synchronized (future) {
      ExecutionResult er = new ExecutionResult(result, failure).withWaitNanos(waitNanos);
      if (!completeCalled)
        record(er);
      completeCalled = true;
      innerExecutionSupplier.set(CompletableFuture.completedFuture(er));
      outerExecutionSupplier.get().whenComplete(this::complete);
      return completed;
    }
  }

  private void complete(ExecutionResult result, Throwable error) {
    if (result == null && error == null)
      return;

    completed = true;
    if (!future.isDone()) {
      if (result != null)
        future.completeResult(result);
      else {
        if (error instanceof CompletionException)
          error = error.getCause();
        future.completeResult(ExecutionResult.failure(error));
      }
    }
  }
}
