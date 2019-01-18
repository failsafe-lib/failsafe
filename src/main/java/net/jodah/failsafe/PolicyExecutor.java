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
  protected AbstractExecution execution;

  protected PolicyExecutor(P policy) {
    this.policy = policy;
  }

  /**
   * Called before execution to return an alternative result or failure such as if execution is not allowed or needed.
   * Should return the provided {@code result} else some alternative.
   */
  protected ExecutionResult preExecute() {
    return null;
  }

  /**
   * Performs a sync execution by first doing a pre-execute, calling the next executor, else calling the executor's
   * supplier, then finally doing a post-execute. This navigates to the end of the executor chain before calling the
   * supplier.
   */
  protected Supplier<ExecutionResult> supplySync(Supplier<ExecutionResult> supplier) {
    return () -> {
      ExecutionResult result = preExecute();
      if (result != null)
        return result;

      return postExecute(supplier.get());
    };
  }

  /**
   * Performs an async execution by first doing an optional pre-execute, calling the next executor, else scheduling the
   * executor's supplier, then finally doing a post-execute. This navigates to the end of the executor chain before
   * calling the supplier.
   *
   * @return null if an execution has been scheduled
   */
  protected Supplier<CompletableFuture<ExecutionResult>> supplyAsync(
      Supplier<CompletableFuture<ExecutionResult>> supplier, Scheduler scheduler, FailsafeFuture<Object> future) {
    return () -> {
      ExecutionResult result = preExecute();
      if (result != null)
        return CompletableFuture.completedFuture(result);

      return supplier.get().thenApply(this::postExecute);
    };
  }

  /**
   * Performs post-execution handling for a {@code result}.
   */
  protected ExecutionResult postExecute(ExecutionResult result) {
    if (isFailure(result)) {
      result = onFailure(result.with(false, false));

      if (result.isComplete() && policy instanceof AbstractPolicy) {
        AbstractPolicy abstractPolicy = (AbstractPolicy) policy;
        if (abstractPolicy.failureListener != null)
          abstractPolicy.failureListener.handle(result, execution);
      }
    } else {
      result = result.with(true, true);
      onSuccess(result);

      if (result.isComplete() && policy instanceof AbstractPolicy) {
        AbstractPolicy abstractPolicy = (AbstractPolicy) policy;
        if (abstractPolicy.successListener != null)
          abstractPolicy.successListener.handle(result, execution);
      }
    }

    return result;
  }

  /**
   * Returns whether the {@code result} is a success according to the policy. If the {code result} has no result, it is
   * not a failure.
   */
  protected boolean isFailure(ExecutionResult result) {
    if (result.isNonResult())
      return false;
    else if (policy instanceof AbstractPolicy)
      return ((AbstractPolicy) policy).isFailure(result);
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
}
