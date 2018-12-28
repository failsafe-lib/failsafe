package net.jodah.failsafe;

import net.jodah.failsafe.event.EventHandler;
import net.jodah.failsafe.util.concurrent.Scheduler;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Handles execution and execution results according to a policy. May contain pre and post execution behaviors.
 * <p>
 * Part of the Failsafe SPI.
 *
 * @param <T> policy type
 */
public abstract class PolicyExecutor<T extends Policy> {
  protected final T policy;
  protected AbstractExecution execution;
  protected EventHandler eventHandler;
  PolicyExecutor next;

  protected PolicyExecutor(T policy) {
    this.policy = policy;
  }

  /**
   * Called before execution to return an alternative result or failure such as if execution is not allowed or needed.
   * Should return the provided {@code result} else some alternative.
   */
  protected ExecutionResult preExecute(ExecutionResult result) {
    return result;
  }

  /**
   * Performs an sync execution by first doing a pre-execute, calling the next executor, else calling the executor's
   * callable. This navigates to the end of the executor chain before calling the callable.
   */
  protected ExecutionResult executeSync(ExecutionResult result) {
    ExecutionResult preResult = preExecute(result);
    if (preResult != result)
      return preResult;

    if (next != null) {
      // Move right
      result = next.executeSync(result);
    } else {
      // End of chain
      try {
        long waitNanos = result == null ? 0 : result.waitNanos;
        Thread.sleep(TimeUnit.NANOSECONDS.toMillis(waitNanos));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return new ExecutionResult(null, new FailsafeException(e), true, false);
      }

      try {
        execution.preExecute();
        result = new ExecutionResult(execution.callable.call(), null);
      } catch (Throwable t) {
        result = new ExecutionResult(null, t);
      } finally {
        execution.record(result);
      }
    }

    return postExecute(result);
  }

  /**
   * Performs an async execution by first doing an optional pre-execute, calling the next executor, else scheduling the
   * executor's callable. This navigates to the end of the executor chain before calling the callable.
   *
   * @param shouldExecute Indicates whether this executor should be executed or be skipped
   * @return null if an execution has been scheduled
   */
  @SuppressWarnings("unchecked")
  protected ExecutionResult executeAsync(ExecutionResult result, boolean shouldExecute, Scheduler scheduler,
      FailsafeFuture<Object> future) {

    boolean shouldExecuteNext = shouldExecute || this.equals(execution.lastExecuted);
    execution.lastExecuted = this;

    if (shouldExecute) {
      ExecutionResult preResult = preExecute(result);
      if (preResult != result)
        return preResult;
    }

    if (next != null) {
      // Move right
      result = next.executeAsync(result, shouldExecuteNext, scheduler, future);
    } else if (shouldExecute) {
      // End of chain
      try {
        if (!future.isDone() && !future.isCancelled()) {
          long waitNanos = result == null ? 0 : result.waitNanos;
          future.inject((Future<Object>) scheduler.schedule(execution.callable, waitNanos, TimeUnit.NANOSECONDS));
        }
        return null;
      } catch (Throwable t) {
        return new ExecutionResult(null, t, true, 0, true, false, true);
      }
    }

    return result == null || result.schedulingError ? result : postExecute(result);
  }

  /**
   * Performs post-execution handling for a {@code result}.
   */
  ExecutionResult postExecute(ExecutionResult result) {
    if (isFailure(result)) {
      return onFailure(result.with(false, false));
    } else {
      result = result.with(true, true);
      onSuccess(result);
      return result;
    }
  }

  /**
   * Returns whether the {@code result} is a success according to the policy. If the {code result} has no result, it is
   * not a failure.
   */
  protected boolean isFailure(ExecutionResult result) {
    if (result.noResult)
      return false;
    else if (policy instanceof AbstractPolicy)
      return ((AbstractPolicy) policy).isFailure(result);
    else
      return !result.success;
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
