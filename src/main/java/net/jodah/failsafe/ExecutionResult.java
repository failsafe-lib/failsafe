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
  /**
   * Whether the execution was completed with no result via {@link Execution#complete()} or {@link
   * AsyncExecution#complete()}.
   */
  final boolean noResult;
  /** The amount of time to wait prior to the next execution, according to the policy */
  public final long waitNanos;
  /** Whether a policy has completed handling of the execution */
  public final boolean completed;
  /** Whether a policy determined the execution to be a success */
  public final boolean success;
  /** Whether all policies determined the execution to be a success. */
  private final Boolean successAll;
  /** Whether an async execution scheduling error occurred */
  final boolean schedulingError;

  /**
   * Records an initial execution result where {@code success} is set to true if {@code failure} is not null.
   */
  ExecutionResult(Object result, Throwable failure) {
    this(result, failure, false, 0, false, failure == null, null, false);
  }

  private ExecutionResult(Object result, Throwable failure, boolean noResult, long waitNanos, boolean completed,
      boolean success, Boolean successAll, boolean schedulingError) {
    this.result = result;
    this.failure = failure;
    this.noResult = noResult;
    this.waitNanos = waitNanos;
    this.completed = completed;
    this.success = success;
    this.successAll = successAll;
    this.schedulingError = schedulingError;
  }

  /**
   * Returns a copy of the ExecutionResult with the {@code result} set, {@code completed} true and {@code success}
   * true.
   */
  public ExecutionResult success(Object result) {
    return new ExecutionResult(result, null, false, waitNanos, true, true, successAll == null ? true : successAll,
        schedulingError);
  }

  /**
   * Returns a copy of the ExecutionResult with the {@code failure} set, {@code completed} true and {@code success}
   * false.
   */
  public static ExecutionResult failure(Throwable failure) {
    return new ExecutionResult(null, failure, false, 0, true, false, false, false);
  }

  /**
   * Returns a copy of the ExecutionResult with the {@code completed} value, else this if nothing has changed.
   */
  public ExecutionResult with(boolean completed) {
    return this.completed == completed ?
        this :
        new ExecutionResult(result, failure, noResult, waitNanos, completed, success, successAll, schedulingError);
  }

  /**
   * Returns a copy of the ExecutionResult with the {@code completed} and {@code success} values.
   */
  ExecutionResult with(boolean completed, boolean success) {
    return new ExecutionResult(result, failure, noResult, waitNanos, completed, success,
        successAll == null ? success : success && successAll, schedulingError);
  }

  /**
   * Returns a copy of the ExecutionResult with the {@code waitNanos}, {@code completed} and {@code success} values.
   */
  public ExecutionResult with(long waitNanos, boolean completed, boolean success) {
    return new ExecutionResult(result, failure, noResult, waitNanos, completed, success,
        successAll == null ? success : success && successAll, schedulingError);
  }

  /**
   * Returns an ExecutionResult indicating a non result.
   */
  static ExecutionResult noResult() {
    return new ExecutionResult(null, null, true, 0, true, true, true, false);
  }

  /**
   * Returns an ExecutionResulting indicating a scheduling error.
   */
  static ExecutionResult schedulingError(Throwable failure) {
    return new ExecutionResult(null, failure, true, 0, true, false, false, true);
  }

  boolean getSuccessAll() {
    return successAll != null && successAll;
  }

  @Override
  public String toString() {
    return "ExecutionResult[" + "result=" + result + ", failure=" + failure + ", waitNanos=" + waitNanos
        + ", completed=" + completed + ", success=" + success + +']';
  }
}