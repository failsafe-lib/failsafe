package net.jodah.failsafe;

/**
 * The result of an execution.
 * <p>
 * Part of the Failsafe SPI.
 *
 * @author Jonathan Halterman
 */
@SuppressWarnings("WeakerAccess")
public class ExecutionResult {
  public final Object result;
  public final Throwable failure;
  /** Whether the execution was completed with no result via {@link Execution#complete()} */
  public final boolean noResult;
  /** The amount of time to wait prior to the next execution, according to the policy */
  public final long waitNanos;
  /** Whether the policy has completed handling of the execution */
  public final boolean completed;
  /** Whether the policy determined the execution to be a success */
  public final boolean success;
  /** Whether an async execution scheduling error occurred */
  public final boolean schedulingError;

  public ExecutionResult(Object result, Throwable failure) {
    this(result, failure, false, 0, false, false, false);
  }

  public ExecutionResult(Object result, Throwable failure, boolean completed, boolean success) {
    this(result, failure, false, 0, completed, success, false);
  }

  public ExecutionResult(Object result, Throwable failure, boolean noResult, long waitNanos, boolean completed,
      boolean success, boolean schedulingError) {
    this.result = result;
    this.failure = failure;
    this.noResult = noResult;
    this.waitNanos = waitNanos;
    this.completed = completed;
    this.success = success;
    this.schedulingError = schedulingError;
  }

  /**
   * Returns a copy of the ExecutionResult with the {@code completed} and {@code success} values.
   */
  public ExecutionResult with(boolean completed, boolean success) {
    return new ExecutionResult(result, failure, noResult, waitNanos, completed, success, schedulingError);
  }

  /**
   * Returns a copy of the ExecutionResult with the {@code waitNanos}, {@code completed} and {@code success} values.
   */
  public ExecutionResult with(long waitNanos, boolean completed, boolean success) {
    return new ExecutionResult(result, failure, noResult, waitNanos, completed, success, schedulingError);
  }

  static ExecutionResult noResult() {
    return new ExecutionResult(null, null, true, 0, true, true, false);
  }

  @Override
  public String toString() {
    return "ExecutionResult[" + "result=" + result + ", failure=" + failure + ", noResult=" + noResult + ", waitNanos="
        + waitNanos + ", completed=" + completed + ", success=" + success + ", schedulingError=" + schedulingError
        + ']';
  }
}