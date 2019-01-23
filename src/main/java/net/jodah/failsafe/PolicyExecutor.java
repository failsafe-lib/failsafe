package net.jodah.failsafe;

import net.jodah.failsafe.util.concurrent.Scheduler;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Handles execution and execution results according to a policy. May contain pre and post execution behaviors.
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

  /**
   * Performs a synchronous execution by first doing a pre-execute, calling the next executor, else calling the
   * executor's supplier, then finally doing a post-execute.
   */
  protected Supplier<ExecutionResult> supply(Supplier<ExecutionResult> supplier) {
    return () -> {
      ExecutionResult result = preExecute();
      if (result != null)
        return result;

      return postExecute(supplier.get());
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

  /**
   * Performs an async execution by first doing an optional pre-execute, calling the next executor, else scheduling the
   * executor's supplier, then finally doing an async post-execute.
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

  /**
   * Performs potentially asynchrononus post-execution handling for a failed {@code result}, possibly creating a new
   * result, else returning the original {@code result}.
   */
  protected CompletableFuture<ExecutionResult> onFailureAsync(ExecutionResult result, Scheduler scheduler,
      FailsafeFuture<Object> future) {
    return CompletableFuture.completedFuture(onFailure(result));
  }

  private void callSuccessListener(ExecutionResult result) {
    if (result.isComplete() && policy instanceof FailurePolicy) {
      FailurePolicy failurePolicy = (FailurePolicy) policy;
      if (failurePolicy.successListener != null)
        failurePolicy.successListener.handle(result, execution);
    }
  }

  private void callFailureListener(ExecutionResult result) {
    if (result.isComplete() && policy instanceof FailurePolicy) {
      FailurePolicy failurePolicy = (FailurePolicy) policy;
      if (failurePolicy.failureListener != null)
        failurePolicy.failureListener.handle(result, execution);
    }
  }
}
