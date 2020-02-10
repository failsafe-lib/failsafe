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

import net.jodah.failsafe.util.concurrent.Scheduler;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Handles execution and execution results according to a policy. May contain pre and post execution behaviors. Each
 * PolicyExecutor makes its own determination about whether an execution result is a success or failure.
 * <p>
 * Part of the Failsafe SPI.
 *
 * @param <P> policy type
 */
public abstract class PolicyExecutor<P extends Policy> {
  protected final P policy;
  protected final AbstractExecution execution;

  protected PolicyExecutor(P policy, AbstractExecution execution) {
    this.policy = policy;
    this.execution = execution;
  }

  /**
   * Called before execution to return an alternative result or failure such as if execution is not allowed or needed.
   * Should return the provided {@code result} else some alternative.
   */
  protected ExecutionResult preExecute() {
    return null;
  }

  protected <E extends Throwable>ExecutionResultWithException<E> preExecuteWithException() {
    return null;
  }

  /**
   * Performs an execution by calling pre-execute else calling the supplier and doing a post-execute.
   */
  protected Supplier<ExecutionResult> supply(Supplier<ExecutionResult> supplier, Scheduler scheduler) {
    return () -> {
      ExecutionResult result = preExecute();
      if (result != null)
        return result;

      return postExecute(supplier.get());
    };
  }

  protected <E extends Throwable> Supplier<ExecutionResultWithException<E>> supplyWithException(Supplier<ExecutionResultWithException<E>> supplier, Scheduler scheduler) {
	    return () -> {
	    	ExecutionResultWithException<E> result = preExecuteWithException();
	      if (result != null)
	        return result;

	      return postExecuteWithException(supplier.get());
	    };
	  }

  /**
   * Performs synchronous post-execution handling for a {@code result}.
   */
  protected ExecutionResult postExecute(ExecutionResult result) {
    if (isFailure(result)) {
      result = onFailure(result.with(false, false));
      callFailureListener(result);
    } else {
      result = result.with(true, true);
      onSuccess(result);
      callSuccessListener(result);
    }

    return result;
  }

  protected <E extends Throwable> ExecutionResultWithException<E> postExecuteWithException(ExecutionResultWithException<E> result) {
	    if (isFailure(result)) {
	      result = onFailureWithException(result.with(false, false));
	      callFailureListener(result);
	    } else {
	      result = result.with(true, true);
	      onSuccess(result);
	      callSuccessListener(result);
	    }

	    return result;
	  }

  /**
   * Performs an async execution by calling pre-execute else calling the supplier and doing a post-execute.
   */
  protected Supplier<CompletableFuture<ExecutionResult>> supplyAsync(
    Supplier<CompletableFuture<ExecutionResult>> supplier, Scheduler scheduler, FailsafeFuture<Object> future) {
    return () -> {
      ExecutionResult result = preExecute();
      if (result != null)
        return CompletableFuture.completedFuture(result);

      return supplier.get().thenCompose(s -> postExecuteAsync(s, scheduler, future));
    };
  }

  /**
   * Performs potentially asynchronous post-execution handling for a {@code result}.
   */
  protected CompletableFuture<ExecutionResult> postExecuteAsync(ExecutionResult result, Scheduler scheduler,
    FailsafeFuture<Object> future) {
    if (isFailure(result)) {
      result = result.with(false, false);
      return onFailureAsync(result, scheduler, future).whenComplete((postResult, error) -> {
        callFailureListener(postResult);
      });
    } else {
      result = result.with(true, true);
      onSuccess(result);
      callSuccessListener(result);
      return CompletableFuture.completedFuture(result);
    }
  }

  /**
   * Returns whether the {@code result} is a success according to the policy. If the {code result} has no result, it is
   * not a failure.
   */
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
  protected ExecutionResult onFailure(ExecutionResult result) {
    return result;
  }

  protected <E extends Throwable> ExecutionResultWithException<E> onFailureWithException(ExecutionResultWithException<E> result) {
    return result;
  }


  /**
   * Performs potentially asynchrononus post-execution handling for a failed {@code result}, possibly creating a new
   * result, else returning the original {@code result}.
   */
  protected CompletableFuture<ExecutionResult> onFailureAsync(ExecutionResult result, Scheduler scheduler,
    FailsafeFuture<Object> future) {
    return CompletableFuture.completedFuture(execution.resultHandled ? result : onFailure(result));
  }

  private void callSuccessListener(ExecutionResult result) {
    if (result.isComplete() && policy instanceof PolicyListeners) {
      PolicyListeners policyListeners = (PolicyListeners) policy;
      if (policyListeners.successListener != null)
        policyListeners.successListener.handle(result, execution);
    }
  }

  private void callFailureListener(ExecutionResult result) {
    if (result.isComplete() && policy instanceof PolicyListeners) {
      PolicyListeners policyListeners = (PolicyListeners) policy;
      if (policyListeners.failureListener != null)
        policyListeners.failureListener.handle(result, execution);
    }
  }
}
