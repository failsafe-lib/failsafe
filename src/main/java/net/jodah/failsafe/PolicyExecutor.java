package net.jodah.failsafe;

import net.jodah.failsafe.event.EventHandler;
import net.jodah.failsafe.util.concurrent.Scheduler;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Executes a policy. May contain pre or post execution behaviors.
 * <p>
 * Part of the Failsafe SPI.
 */
public abstract class PolicyExecutor {
  protected AbstractExecution execution;
  protected EventHandler eventHandler;
  PolicyExecutor next;

  /**
   * Called before execution to return an alternative result or failure such as if execution is not allowed or needed.
   * Should return the provided {@code result} else some alternative.
   */
  public ExecutionResult preExecute(ExecutionResult result) {
    return result;
  }

  /**
   * Performs post-execution handling for the {@code result}, possibly creating a new result. Should return the provided
   * {@code result} else some alternative.
   */
  public ExecutionResult postExecute(ExecutionResult result) {
    return result;
  }

  /**
   * Performs an sync execution by first doing a pre-execute, calling the next executor, else calling the executor's
   * callable. This navigates to the end of the executor chain before calling the callable.
   */
  public ExecutionResult executeSync(ExecutionResult result) {
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
  public ExecutionResult executeAsync(ExecutionResult result, Scheduler scheduler, FailsafeFuture<Object> future,
    boolean shouldExecute) {

    boolean shouldExecuteNext = shouldExecute || this.equals(execution.lastExecuted);
    execution.lastExecuted = this;

    if (shouldExecute) {
      ExecutionResult preResult = preExecute(result);
      if (preResult != result)
        return preResult;
    }

    if (next != null) {
      // Move right
      result = next.executeAsync(result, scheduler, future, shouldExecuteNext);
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

    if (result != null && !result.schedulingError)
      result = postExecute(result);

    return result;
  }
}
