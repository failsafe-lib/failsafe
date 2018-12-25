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
@SuppressWarnings("WeakerAccess")
public abstract class PolicyExecutor {
  protected AbstractExecution execution;
  protected EventHandler<Object> eventHandler;
  PolicyExecutor next;

  /**
   * The result of a {@link PolicyExecutor}'s handling of an execution.
   */
  public static class PolicyResult {
    public final Object result;
    public final Throwable failure;
    /** Whether the execution was completed with no result */
    public final boolean noResult;
    /** The amount of time to wait prior to the next execution, according to the policy */
    public final long waitNanos;
    /** Whether the policy has completed handling of the execution */
    public final boolean completed;
    /** Whether the policy determined the execution to be a success */
    public final boolean success;

    public PolicyResult(Object result, Throwable failure) {
      this(result, failure, false, 0, false, false);
    }

    public PolicyResult(Object result, Throwable failure, boolean noResult) {
      this(result, failure, noResult, 0, false, false);
    }

    public PolicyResult(Object result, Throwable failure, boolean completed, boolean success) {
      this(result, failure, false, 0, completed, success);
    }

    public PolicyResult(Object result, Throwable failure, boolean noResult, boolean completed, boolean success) {
      this(result, failure, noResult, 0, completed, success);
    }

    public PolicyResult(Object result, Throwable failure, boolean noResult, long waitNanos, boolean completed,
      boolean success) {
      this.result = result;
      this.failure = failure;
      this.noResult = noResult;
      this.waitNanos = waitNanos;
      this.completed = completed;
      this.success = success;
    }

    public PolicyResult with(boolean completed, boolean success) {
      return new PolicyResult(result, failure, noResult, waitNanos, completed, success);
    }

    public PolicyResult with(long waitNanos, boolean completed, boolean success) {
      return new PolicyResult(result, failure, noResult, waitNanos, completed, success);
    }

    @Override
    public String toString() {
      return "PolicyResult[" + "result=" + result + ", failure=" + failure + ", noResult=" + noResult + ", waitNanos="
        + waitNanos + ", completed=" + completed + ", success=" + success + ']';
    }
  }

  /**
   * Called before execution to return an alternative result or failure such as if execution is not allowed or needed.
   * Should return the provided {@code result} else some alternative.
   */
  public PolicyResult preExecute(PolicyResult result) {
    return result;
  }

  /**
   * Performs post-execution handling for the {@code result}, possibly creating a new result. Should return the provided
   * {@code result} else some alternative.
   */
  public PolicyResult postExecute(PolicyResult result) {
    return result;
  }

  /**
   * Performs an sync execution by first doing a pre-execute, calling the next executor, else calling the executor's
   * callable. This navigates to the end of the executor chain before calling the callable.
   */
  public PolicyResult executeSync(PolicyResult result) {
    PolicyResult preResult = preExecute(result);
    if (preResult != result)
      return preResult;

    if (next != null) {
      // Move right
      result = next.executeSync(result);
    } else {
      // End of chain
      try {
        execution.preExecute();
        result = new PolicyResult(execution.callable.call(), null);
      } catch (Throwable t) {
        result = new PolicyResult(null, t);
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
  public PolicyResult executeAsync(PolicyResult result, Scheduler scheduler, FailsafeFuture<Object> future,
    boolean shouldExecute) {

    boolean shouldExecuteNext = shouldExecute || this.equals(execution.lastExecuted);
    execution.lastExecuted = this;

    if (shouldExecute) {
      PolicyResult preResult = preExecute(result);
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
        return new PolicyResult(null, t, true, true, false);
      }
    }

    if (result != null && !result.noResult)
      result = postExecute(result);

    return result;
  }
}
