/*
 * Copyright 2018 the original author or authors.
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
package net.jodah.failsafe.spi;

import net.jodah.failsafe.ExecutionContext;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Handles execution and execution results according to a policy. May contain pre-execution and post-execution
 * behaviors. Each PolicyExecutor makes its own determination about whether an execution result is a success or
 * failure.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public abstract class PolicyExecutor<R> {
  /** Index of the policy relative to other policies in a composition, inner-most first */
  private final int policyIndex;

  /** Optional APIs for policies that support them */
  private final FailurePolicy<R> failurePolicy;
  private final EventHandler<R> successHandler;
  private final EventHandler<R> failureHandler;

  protected PolicyExecutor(int policyIndex, FailurePolicy<R> failurePolicy, EventHandler<R> successHandler,
    EventHandler<R> failureHandler) {
    this.policyIndex = policyIndex;
    this.failurePolicy = failurePolicy;
    this.successHandler = successHandler;
    this.failureHandler = failureHandler;
  }

  /**
   * Returns the index of the policy relative to other policies in a composition, where the inner-most policy in a
   * composition has an index of {@code 0}.
   */
  public int getPolicyIndex() {
    return policyIndex;
  }

  /**
   * Called before execution to return an alternative result or failure such as if execution is not allowed or needed.
   */
  protected ExecutionResult<R> preExecute() {
    return null;
  }

  /**
   * Performs an execution by calling pre-execute else calling the supplier and doing a post-execute.
   */
  public Function<SyncExecutionInternal<R>, ExecutionResult<R>> apply(
    Function<SyncExecutionInternal<R>, ExecutionResult<R>> innerFn, Scheduler scheduler) {
    return execution -> {
      ExecutionResult<R> result = preExecute();
      if (result != null) {
        // Still need to preExecute when returning an alternative result before making it to the terminal Supplier
        execution.preExecute();
        return result;
      }

      return postExecute(execution, innerFn.apply(execution));
    };
  }

  /**
   * Performs synchronous post-execution handling for a {@code result}.
   */
  public ExecutionResult<R> postExecute(ExecutionInternal<R> execution, ExecutionResult<R> result) {
    execution.recordAttempt();
    if (isFailure(result)) {
      result = onFailure(execution, result.withFailure());
      handleFailure(result, execution);
    } else {
      result = result.withSuccess();
      onSuccess(result);
      handleSuccess(result, execution);
    }

    return result;
  }

  /**
   * Performs an async execution by calling pre-execute else calling the supplier and doing a post-execute. Implementors
   * must handle a null result from a supplier, which indicates that an async execution has occurred, that a result will
   * be recorded separately, and that postExecute handling should not be performed.
   */
  public Function<AsyncExecutionInternal<R>, CompletableFuture<ExecutionResult<R>>> applyAsync(
    Function<AsyncExecutionInternal<R>, CompletableFuture<ExecutionResult<R>>> innerFn, Scheduler scheduler,
    FailsafeFuture<R> future) {

    return execution -> {
      ExecutionResult<R> result = preExecute();
      if (result != null) {
        // Still need to preExecute when returning an alternative result before making it to the terminal Supplier
        execution.preExecute();
        return CompletableFuture.completedFuture(result);
      }

      return innerFn.apply(execution).thenCompose(r -> {
        return r == null ? ExecutionResult.nullFuture() : postExecuteAsync(execution, r, scheduler, future);
      });
    };
  }

  /**
   * Performs potentially asynchronous post-execution handling for a {@code result}.
   */
  protected synchronized CompletableFuture<ExecutionResult<R>> postExecuteAsync(AsyncExecutionInternal<R> execution,
    ExecutionResult<R> result, Scheduler scheduler, FailsafeFuture<R> future) {
    CompletableFuture<ExecutionResult<R>> postFuture = null;

    /* Guard against post executing twice for the same execution. This will happen if one async execution result is
     * recorded by a timeout and another via AsyncExecution.record. */
    if (!execution.isAsyncExecution() || !execution.isPostExecuted(policyIndex)) {
      execution.recordAttempt();
      if (isFailure(result)) {
        postFuture = onFailureAsync(execution, result.withFailure(), scheduler, future).whenComplete(
          (postResult, error) -> handleFailure(postResult, execution));
      } else {
        result = result.withSuccess();
        onSuccess(result);
        handleSuccess(result, execution);
        postFuture = CompletableFuture.completedFuture(result);
      }

      if (execution.isAsyncExecution())
        execution.setPostExecuted(policyIndex);
    }
    return postFuture;
  }

  /**
   * Returns whether the {@code result} is a success according to the policy. If the {code result} has no result, it is
   * not a failure.
   */
  protected boolean isFailure(ExecutionResult<R> result) {
    if (result.isNonResult())
      return false;
    else if (failurePolicy != null)
      return failurePolicy.isFailure(result.getResult(), result.getFailure());
    else
      return result.getFailure() != null;
  }

  /**
   * Performs post-execution handling for a {@code result} that is considered a success according to {@link
   * #isFailure(ExecutionResult)}.
   */
  protected void onSuccess(ExecutionResult<R> result) {
  }

  /**
   * Performs post-execution handling for a {@code result} that is considered a failure according to {@link
   * #isFailure(ExecutionResult)}, possibly creating a new result, else returning the original {@code result}.
   */
  protected ExecutionResult<R> onFailure(ExecutionContext<R> context, ExecutionResult<R> result) {
    return result;
  }

  /**
   * Performs potentially asynchrononus post-execution handling for a failed {@code result}, possibly creating a new
   * result, else returning the original {@code result}.
   */
  protected CompletableFuture<ExecutionResult<R>> onFailureAsync(ExecutionContext<R> context, ExecutionResult<R> result,
    Scheduler scheduler, FailsafeFuture<R> future) {
    return CompletableFuture.completedFuture(onFailure(context, result));
  }

  private void handleSuccess(ExecutionResult<R> result, ExecutionContext<R> context) {
    if (successHandler != null && result.isComplete())
      successHandler.handle(result, context);
  }

  private void handleFailure(ExecutionResult<R> result, ExecutionContext<R> context) {
    if (failureHandler != null && result.isComplete())
      failureHandler.handle(result, context);
  }
}
