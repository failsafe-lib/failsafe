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

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * The result of an execution. Immutable.
 * <p>
 * Part of the Failsafe SPI.
 *
 * @author Jonathan Halterman
 */
public class ExecutionResult {
  static final CompletableFuture<ExecutionResult> NULL_FUTURE = CompletableFuture.completedFuture(null);

  /** An execution that was completed with a non-result */
  static final ExecutionResult NONE = new ExecutionResult(null, null, true, 0, true, true, true);

  /** The execution result, if any */
  private final Object result;
  /** The execution failure, if any */
  private final Throwable failure;
  /** Whether the result represents a non result rather than a {@code null} result */
  private final boolean nonResult;
  /** The amount of time to wait prior to the next execution, according to the policy */
  private final long waitNanos;
  /** Whether a policy has completed handling of the execution */
  private final boolean complete;
  /** Whether a policy determined the execution to be a success */
  private final boolean success;
  /** Whether all policies determined the execution to be a success */
  private final Boolean successAll;

  /**
   * Records an initial execution result with {@code complete} true and {@code success} set to true if {@code failure}
   * is not null.
   */
  public ExecutionResult(Object result, Throwable failure) {
    this(result, failure, false, 0, true, failure == null, failure == null);
  }

  private ExecutionResult(Object result, Throwable failure, boolean nonResult, long waitNanos, boolean complete,
    boolean success, Boolean successAll) {
    this.nonResult = nonResult;
    this.result = result;
    this.failure = failure;
    this.waitNanos = waitNanos;
    this.complete = complete;
    this.success = success;
    this.successAll = successAll;
  }

  /**
   * Returns an ExecutionResult with the {@code result} set, {@code complete} true and {@code success} true.
   */
  public static ExecutionResult success(Object result) {
    return new ExecutionResult(result, null, false, 0, true, true, true);
  }

  /**
   * Returns an ExecutionResult with the {@code failure} set, {@code complete} true and {@code success} false.
   */
  public static ExecutionResult failure(Throwable failure) {
    return new ExecutionResult(null, failure, false, 0, true, false, false);
  }

  public Object getResult() {
    return result;
  }

  public Throwable getFailure() {
    return failure;
  }

  public long getWaitNanos() {
    return waitNanos;
  }

  public boolean isComplete() {
    return complete;
  }

  public boolean isNonResult() {
    return nonResult;
  }

  public boolean isSuccess() {
    return success;
  }

  /**
   * Returns a copy of the ExecutionResult with a non-result, and complete and success set to true. Returns {@code this}
   * if {@link #success} and {@link #result} are unchanged.
   */
  ExecutionResult withNonResult() {
    return success && this.result == null && nonResult ?
      this :
      new ExecutionResult(null, null, true, waitNanos, true, true, successAll);
  }

  /**
   * Returns a copy of the ExecutionResult with the {@code result} value, and complete and success set to true. Returns
   * {@code this} if {@link #success} and {@link #result} are unchanged.
   */
  public ExecutionResult withResult(Object result) {
    boolean unchangedNull = this.result == null && result == null && failure == null;
    boolean unchangedNotNull = this.result != null && this.result.equals(result);
    return success && (unchangedNull || unchangedNotNull) ?
      this :
      new ExecutionResult(result, null, nonResult, waitNanos, true, true, successAll);
  }

  /**
   * Returns a copy of the ExecutionResult with {@code complete} set to false, else this if nothing has changed.
   */
  ExecutionResult withNotComplete() {
    return !this.complete ?
      this :
      new ExecutionResult(result, failure, nonResult, waitNanos, false, success, successAll);
  }

  /**
   * Returns a copy of the ExecutionResult with success value of {code false}.
   */
  ExecutionResult withFailure() {
    return !this.success ? this : new ExecutionResult(result, failure, nonResult, waitNanos, complete, false, false);
  }

  /**
   * Returns a copy of the ExecutionResult with the {@code complete} and {@code success} values of {@code true}.
   */
  ExecutionResult withSuccess() {
    return this.complete && this.success ?
      this :
      new ExecutionResult(result, failure, nonResult, waitNanos, true, true, successAll);
  }

  /**
   * Returns a copy of the ExecutionResult with the {@code waitNanos} value.
   */
  public ExecutionResult withWaitNanos(long waitNanos) {
    return this.waitNanos == waitNanos ?
      this :
      new ExecutionResult(result, failure, nonResult, waitNanos, complete, success, successAll);
  }

  /**
   * Returns a copy of the ExecutionResult with the {@code waitNanos}, {@code complete} and {@code success} values.
   */
  public ExecutionResult with(long waitNanos, boolean complete, boolean success) {
    return this.waitNanos == waitNanos && this.complete == complete && this.success == success ?
      this :
      new ExecutionResult(result, failure, nonResult, waitNanos, complete, success,
        successAll == null ? success : success && successAll);
  }

  boolean getSuccessAll() {
    return successAll != null && successAll;
  }

  @Override
  public String toString() {
    return "ExecutionResult[" + "result=" + result + ", failure=" + failure + ", nonResult=" + nonResult
      + ", waitNanos=" + waitNanos + ", complete=" + complete + ", success=" + success + ", successAll=" + successAll
      + ']';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    ExecutionResult that = (ExecutionResult) o;
    return Objects.equals(result, that.result) && Objects.equals(failure, that.failure);
  }

  @Override
  public int hashCode() {
    return Objects.hash(result, failure);
  }
}