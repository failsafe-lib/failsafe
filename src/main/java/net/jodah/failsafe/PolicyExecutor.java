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
package net.jodah.failsafe;

import net.jodah.failsafe.internal.util.Assert;
import net.jodah.failsafe.util.concurrent.Scheduler;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Handles execution and execution results according to a policy. May contain pre and post execution behaviors. Each
 * PolicyExecutor makes its own determination about whether an execution result is a success or failure.
 * <p>
 * Part of the Failsafe SPI.
 *
 * @param <R> result type
 * @param <P> policy type
 */
public abstract class PolicyExecutor<R, P extends Policy<R>> {
  protected final P policy;
  // Index of the policy relative to other policies in a composition, inner-most first
  final int policyIndex;

  protected PolicyExecutor(P policy, int policyIndex) {
    this.policy = policy;
    this.policyIndex = policyIndex;
  }

  /**
   * Called before execution to return an alternative result or failure such as if execution is not allowed or needed.
   */
  protected ExecutionResult preExecute() {
    return null;
  }

  /**
   * Performs an execution by calling pre-execute else calling the supplier and doing a post-execute.
   */
  protected Function<Execution<R>, ExecutionResult> apply(Function<Execution<R>, ExecutionResult> innerFn,
    Scheduler scheduler) {
    return execution -> {
      ExecutionResult result = preExecute();
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
  protected ExecutionResult postExecute(AbstractExecution<R> execution, ExecutionResult result) {
    Assert.log(this, "Post executing for result=%s, exec=%s", result.toSummary(), execution.hashCode());
    execution.recordAttempt();
    if (isFailure(result)) {
      result = onFailure(execution, result.withFailure());
      callFailureListener(execution, result);
    } else {
      result = result.withSuccess();
      onSuccess(result);
      callSuccessListener(execution, result);
    }

    return result;
  }

  /**
   * Performs an async execution by calling pre-execute else calling the supplier and doing a post-execute. Implementors
   * must handle a null result from a supplier, which indicates that an async execution has occurred, that a result will
   * be recorded separately, and that postExecute handling should not be performed.
   */
  protected Function<AsyncExecution<R>, CompletableFuture<ExecutionResult>> applyAsync(
    Function<AsyncExecution<R>, CompletableFuture<ExecutionResult>> innerFn, Scheduler scheduler,
    FailsafeFuture<R> future) {

    return execution -> {
      ExecutionResult result = preExecute();
      if (result != null) {
        // Still need to preExecute when returning an alternative result before making it to the terminal Supplier
        execution.preExecute();
        return CompletableFuture.completedFuture(result);
      }

      return innerFn.apply(execution).thenCompose(r -> {
        return r == null ? ExecutionResult.NULL_FUTURE : postExecuteAsync(execution, r, scheduler, future);
      });
    };
  }

  /**
   * Performs potentially asynchronous post-execution handling for a {@code result}.
   */
  protected synchronized CompletableFuture<ExecutionResult> postExecuteAsync(AsyncExecution<R> execution,
    ExecutionResult result, Scheduler scheduler, FailsafeFuture<R> future) {

    // Guard against post executing twice for the same execution. This will happen if one async execution result
    // is recorded by a timeout and another asynchronously.

    CompletableFuture<ExecutionResult> postFuture = null;
    if (!execution.isAsyncExecution() || !execution.isPostExecuted(policyIndex)) {
      Assert.log(this, "Actually post executing for exec=%s", execution.hashCode());
      execution.recordAttempt();
      if (isFailure(result)) {
        postFuture = onFailureAsync(execution, result.withFailure(), scheduler, future).whenComplete(
          (postResult, error) -> callFailureListener(execution, postResult));
      } else {
        result = result.withSuccess();
        onSuccess(result);
        callSuccessListener(execution, result);
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
  @SuppressWarnings("rawtypes")
  protected boolean isFailure(ExecutionResult result) {
    if (result.isNonResult())
      return false;
    else if (policy instanceof FailurePolicy)
      return ((FailurePolicy) policy).isFailure(result);
    else
      return result.getFailure() != null;
  }

  /**
   * Performs post-execution handling for a {@code result} that is considered a success according to {@link
   * #isFailure(ExecutionResult)}.
   */
  protected void onSuccess(ExecutionResult result) {
  }

  /**
   * Performs post-execution handling for a {@code result} that is considered a failure according to {@link
   * #isFailure(ExecutionResult)}, possibly creating a new result, else returning the original {@code result}.
   */
  protected ExecutionResult onFailure(AbstractExecution<R> execution, ExecutionResult result) {
    return result;
  }

  /**
   * Performs potentially asynchrononus post-execution handling for a failed {@code result}, possibly creating a new
   * result, else returning the original {@code result}.
   */
  protected CompletableFuture<ExecutionResult> onFailureAsync(AbstractExecution<R> execution, ExecutionResult result,
    Scheduler scheduler, FailsafeFuture<R> future) {
    return CompletableFuture.completedFuture(onFailure(execution, result));
  }

  @SuppressWarnings("rawtypes")
  private void callSuccessListener(AbstractExecution<R> execution, ExecutionResult result) {
    if (result.isComplete() && policy instanceof PolicyListeners) {
      PolicyListeners policyListeners = (PolicyListeners) policy;
      if (policyListeners.successListener != null)
        policyListeners.successListener.handle(result, execution);
    }
  }

  @SuppressWarnings("rawtypes")
  private void callFailureListener(AbstractExecution<R> execution, ExecutionResult result) {
    if (result.isComplete() && policy instanceof PolicyListeners) {
      PolicyListeners policyListeners = (PolicyListeners) policy;
      if (policyListeners.failureListener != null)
        policyListeners.failureListener.handle(result, execution);
    }
  }
}
