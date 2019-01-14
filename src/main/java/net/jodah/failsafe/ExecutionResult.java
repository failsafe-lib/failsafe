package net.jodah.failsafe;

/**
 * The result of an execution. Immutable.
 * <p>
 * Part of the Failsafe SPI.
 *
 * @author Jonathan Halterman
 */
@SuppressWarnings("WeakerAccess")
public class ExecutionResult {
  /** An execution that was completed with a non-result */
  static final ExecutionResult NONE = new ExecutionResult(null, null, true, 0, true, true, true);

  /** The execution result, if any */
  public final Object result;
  /** The execution failure, if any */
  public final Throwable failure;
  /** Whether the result represents a non result rather than a {@code null} result */
  public final boolean nonResult;
  /** The amount of time to wait prior to the next execution, according to the policy */
  public final long waitNanos;
  /** Whether a policy has completed handling of the execution */
  public final boolean completed;
  /** Whether a policy determined the execution to be a success */
  public final boolean success;
  /** Whether all policies determined the execution to be a success */
  private final Boolean successAll;

  /**
   * Records an initial execution result where {@code success} is set to true if {@code failure} is not null.
   */
  public ExecutionResult(Object result, Throwable failure) {
    this(result, failure, false, 0, false, failure == null, null);
  }

  private ExecutionResult(Object result, Throwable failure, boolean nonResult, long waitNanos, boolean completed,
      boolean success, Boolean successAll) {
    this.nonResult = nonResult;
    this.result = result;
    this.failure = failure;
    this.waitNanos = waitNanos;
    this.completed = completed;
    this.success = success;
    this.successAll = successAll;
  }

  public static ExecutionResult success(Object result) {
    return new ExecutionResult(result, null);
  }

  /**
   * Returns a copy of the ExecutionResult with the {@code failure} set, {@code completed} true and {@code success}
   * false.
   */
  public static ExecutionResult failure(Throwable failure) {
    return new ExecutionResult(null, failure, false, 0, true, false, false);
  }

  /**
   * Returns a copy of the ExecutionResult with the {@code result} value, and completed and success set to true.
   */
  public ExecutionResult withResult(Object result) {
    return new ExecutionResult(result, null, nonResult, waitNanos, true, true, successAll);
  }

  /**
   * Returns a copy of the ExecutionResult with the value set to true, else this if nothing has changed.
   */
  public ExecutionResult withCompleted() {
    return this.completed ?
        this :
        new ExecutionResult(result, failure, nonResult, waitNanos, true, success, successAll);
  }

  /**
   * Returns a copy of the ExecutionResult with the {@code completed} and {@code success} values.
   */
  ExecutionResult with(boolean completed, boolean success) {
    return new ExecutionResult(result, failure, nonResult, waitNanos, completed, success,
        successAll == null ? success : success && successAll);
  }

  /**
   * Returns a copy of the ExecutionResult with the {@code waitNanos}, {@code completed} and {@code success} values.
   */
  public ExecutionResult with(long waitNanos, boolean completed, boolean success) {
    return new ExecutionResult(result, failure, nonResult, waitNanos, completed, success,
        successAll == null ? success : success && successAll);
  }

  boolean getSuccessAll() {
    return successAll != null && successAll;
  }

  @Override
  public String toString() {
    return "ExecutionResult[" + "result=" + result + ", failure=" + failure + ", nonResult=" + nonResult
        + ", waitNanos=" + waitNanos + ", completed=" + completed + ", success=" + success + ", successAll="
        + successAll + ']';
  }
}