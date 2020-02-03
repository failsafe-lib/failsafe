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
 * @author Jonathan Halterman
 */
@SuppressWarnings("WeakerAccess")
public final class AsyncExecution extends AbstractExecution {
  private SettableSupplier<CompletableFuture<ExecutionResult>> innerExecutionSupplier;
  private Supplier<CompletableFuture<ExecutionResult>> outerExecutionSupplier;
  final FailsafeFuture<Object> future;
  private volatile boolean completeCalled;
  private volatile boolean retryCalled;

  @SuppressWarnings("unchecked")
  <T> AsyncExecution(Scheduler scheduler, FailsafeFuture<T> future, FailsafeExecutor<?> executor) {
    super(scheduler, (FailsafeExecutor<Object>) executor);
    this.future = (FailsafeFuture<Object>) future;
  }

  void inject(Supplier<CompletableFuture<ExecutionResult>> syncSupplier, boolean asyncExecution) {
    if (!asyncExecution) {
      outerExecutionSupplier = Functions.getPromiseAsync(syncSupplier, scheduler, future);
    } else {
      outerExecutionSupplier = innerExecutionSupplier = Functions.toSettableSupplier(syncSupplier);
    }

    for (PolicyExecutor<Policy<Object>> policyExecutor : policyExecutors)
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
   */
  public boolean complete(Object result) {
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
   */
  public boolean complete(Object result, Throwable failure) {
    postExecute(new ExecutionResult(result, failure));
    return completed;
  }

  /**
   * Records an execution if one has not been recorded yet, attempts to schedule a retry if necessary, and returns
   * {@code true} if a retry has been scheduled else returns {@code false} and completes the execution and associated
   * {@code CompletableFuture}.
   *
   * @throws IllegalStateException if a retry method has already been called or the execution is already complete
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
   */
  public boolean retryFor(Object result) {
    return retryFor(result, null);
  }

  /**
   * Records an execution if one has not been recorded yet for the {@code result} or {@code failure}, attempts to
   * schedule a retry if necessary, and returns {@code true} if a retry has been scheduled else returns {@code false}
   * and completes the execution and associated {@code CompletableFuture}.
   *
   * @throws IllegalStateException if a retry method has already been called or the execution is already complete
   */
  public boolean retryFor(Object result, Throwable failure) {
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
   * Attempts to complete the parent execution, calls failure handlers, and completes the future if needed. Runs
   * synchrnously since a concrete result is needed.
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
  @SuppressWarnings("unchecked")
  void executeAsync(boolean asyncExecution) {
    if (!asyncExecution)
      outerExecutionSupplier.get().whenComplete(this::complete);
    else
      future.inject((Future) scheduler.schedule(innerExecutionSupplier::get, 0, TimeUnit.NANOSECONDS));
  }

  /**
   * Attempts to complete the execution else handle according to the configured policies. Returns {@code true} if the
   * execution was completed, else false which indicates the result was handled asynchronously and may have triggered a
   * retry.
   *
   * @throws IllegalStateException if the execution is already complete
   */
  boolean completeOrHandle(Object result, Throwable failure) {
    synchronized (future) {
      ExecutionResult er = new ExecutionResult(result, failure);
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
