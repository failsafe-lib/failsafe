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

import net.jodah.failsafe.internal.util.Assert;
import net.jodah.failsafe.util.concurrent.Scheduler;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Future;
import java.util.function.Function;

/**
 * Tracks asynchronous executions and handles failures according to one or more {@link Policy policies}. Execution
 * results must be explicitly recorded via one of the {@code record} methods.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public final class AsyncExecution<R> extends AbstractExecution<R> {
  // Cross-attempt state --
  // The outer-most function that executions begin with
  private Function<AsyncExecution<R>, CompletableFuture<ExecutionResult>> outerFn;
  private final boolean asyncExecution;
  // Whether a policy executor completed post execution
  private final boolean[] policyPostExecuted;
  final FailsafeFuture<R> future;

  // Per-attempt state --
  // Whether a result has been explicitly recorded
  volatile boolean recordCalled;
  // The future for the thread that the innerFn is running in
  volatile Future<?> innerFuture;

  AsyncExecution(List<Policy<R>> policies, Scheduler scheduler, FailsafeFuture<R> future, boolean asyncExecution,
    Function<AsyncExecution<R>, CompletableFuture<ExecutionResult>> innerFn) {
    super(policies, scheduler);
    this.future = future;
    this.asyncExecution = asyncExecution;
    this.policyPostExecuted = new boolean[policyExecutors.size()];

    outerFn = asyncExecution ? Functions.toExecutionAware(innerFn) : innerFn;
    outerFn = Functions.toAsync(outerFn, scheduler);

    for (PolicyExecutor<R, ? extends Policy<R>> policyExecutor : policyExecutors)
      outerFn = policyExecutor.applyAsync(outerFn, scheduler, this.future);
  }

  private AsyncExecution(AsyncExecution<R> execution) {
    super(execution);
    outerFn = execution.outerFn;
    future = execution.future;
    asyncExecution = execution.asyncExecution;
    policyPostExecuted = new boolean[policyExecutors.size()];
  }

  /**
   * Completes the execution and the associated {@code CompletableFuture}.
   *
   * @throws IllegalStateException if the execution is already complete
   */
  public void complete() {
    Assert.state(!recordCalled, "The most recent execution has already been recorded");
    recordCalled = true;

    // Guard against race with a timeout expiring
    synchronized (future) {
      ExecutionResult result = this.result != null ? this.result : ExecutionResult.NONE;
      complete(postExecute(result), null);
    }
  }

  /**
   * Records an execution {@code result} or {@code failure} which triggers failure handling, if needed, by the
   * configured policies. If policy handling is not possible or already complete, the resulting {@link
   * CompletableFuture} is completed.
   *
   * @throws IllegalStateException if the most recent execution was already recorded or the execution is complete
   */
  public void record(R result, Throwable failure) {
    Assert.state(!recordCalled, "The most recent execution has already been recorded");
    recordCalled = true;

    // Guard against race with a timeout expiring
    synchronized (future) {
      if (!attemptRecorded) {
        ExecutionResult er = new ExecutionResult(result, failure).withWaitNanos(waitNanos);
        record(er);
      }

      // Proceed with handling the recorded result
      executeAsync();
    }
  }

  /**
   * Records an execution {@code result} which triggers failure handling, if needed, by the configured policies. If
   * policy handling is not possible or already complete, the resulting {@link CompletableFuture} is completed.
   *
   * @throws IllegalStateException if the most recent execution was already recorded or the execution is complete
   */
  public void recordResult(R result) {
    record(result, null);
  }

  /**
   * Records an execution {@code failure} which triggers failure handling, if needed, by the configured policies. If
   * policy handling is not possible or already complete, the resulting {@link CompletableFuture} is completed.
   *
   * @throws IllegalStateException if the most recent execution was already recorded or the execution is complete
   */
  public void recordFailure(Throwable failure) {
    record(null, failure);
  }

  /**
   * Performs an asynchronous execution.
   */
  void executeAsync() {
    outerFn.apply(this).whenComplete(this::complete);
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

  synchronized void setPostExecuted(int policyIndex) {
    policyPostExecuted[policyIndex] = true;
  }

  synchronized boolean isPostExecuted(int policyIndex) {
    return policyPostExecuted[policyIndex];
  }

  boolean isAsyncExecution() {
    return asyncExecution;
  }

  AsyncExecution<R> copy() {
    return new AsyncExecution<>(this);
  }
}
