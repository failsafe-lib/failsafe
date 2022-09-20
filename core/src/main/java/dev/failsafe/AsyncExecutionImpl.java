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

import dev.failsafe.internal.util.Assert;
import dev.failsafe.spi.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

/**
 * AsyncExecution and AsyncExecutionInternal implementation.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
final class AsyncExecutionImpl<R> extends ExecutionImpl<R> implements AsyncExecutionInternal<R> {
  // -- Cross-attempt state --

  private final FailsafeFuture<R> future;
  private final boolean asyncExecution;
  // The outermost function that executions begin with
  private Function<AsyncExecutionInternal<R>, CompletableFuture<ExecutionResult<R>>> outerFn;

  // -- Per-attempt state --

  // Whether a policy executor completed post execution
  private final boolean[] policyPostExecuted = new boolean[policyExecutors.size()];
  // Whether a result has been recorded
  private volatile boolean recorded;

  AsyncExecutionImpl(List<Policy<R>> policies, Scheduler scheduler, FailsafeFuture<R> future, boolean asyncExecution,
    Function<AsyncExecutionInternal<R>, CompletableFuture<ExecutionResult<R>>> innerFn) {
    super(policies);
    this.future = future;
    this.asyncExecution = asyncExecution;

    outerFn = asyncExecution ? Functions.toExecutionAware(innerFn) : innerFn;
    outerFn = Functions.toAsync(outerFn, scheduler, future);

    for (PolicyExecutor<R> policyExecutor : policyExecutors)
      outerFn = policyExecutor.applyAsync(outerFn, scheduler, future);
  }

  /**
   * Create an async execution for a new attempt.
   */
  private AsyncExecutionImpl(AsyncExecutionImpl<R> execution) {
    super(execution);
    outerFn = execution.outerFn;
    future = execution.future;
    asyncExecution = execution.asyncExecution;
  }

  @Override
  public void complete() {
    Assert.state(!recorded, "The most recent execution has already been recorded or completed");
    recorded = true;

    // Guard against race with a timeout being set
    synchronized (future) {
      ExecutionResult<R> result = this.result != null ? this.result : ExecutionResult.none();
      complete(postExecute(result), null);
    }
  }

  @Override
  public boolean isComplete() {
    return completed;
  }

  @Override
  public void record(R result, Throwable exception) {
    Assert.state(!recorded, "The most recent execution has already been recorded or completed");
    recorded = true;

    // Guard against race with a timeout being set
    synchronized (future) {
      if (!attemptRecorded) {
        Assert.state(!completed, "Execution has already been completed");
        record(new ExecutionResult<>(result, exception));
      }

      // Proceed with handling the recorded result
      executeAsync();
    }
  }

  @Override
  public void recordResult(R result) {
    record(result, null);
  }

  @Override
  public void recordException(Throwable exception) {
    record(null, exception);
  }

  @Override
  public boolean isAsyncExecution() {
    return asyncExecution;
  }

  @Override
  public boolean isRecorded() {
    return recorded;
  }

  @Override
  public synchronized void setPostExecuted(int policyIndex) {
    policyPostExecuted[policyIndex] = true;
  }

  @Override
  public synchronized boolean isPostExecuted(int policyIndex) {
    return policyPostExecuted[policyIndex];
  }

  @Override
  public AsyncExecutionInternal<R> copy() {
    return new AsyncExecutionImpl<>(this);
  }

  /**
   * Performs an asynchronous execution.
   */
  void executeAsync() {
    outerFn.apply(this).whenComplete(this::complete);
  }

  private void complete(ExecutionResult<R> result, Throwable error) {
    if (result == null && error == null)
      return;

    completed = true;
    if (!future.isDone()) {
      if (result != null)
        future.completeResult(result);
      else {
        if (error instanceof CompletionException)
          error = error.getCause();
        future.completeResult(ExecutionResult.exception(error));
      }
    }
  }
}
