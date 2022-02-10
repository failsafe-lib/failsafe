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
package dev.failsafe.spi;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * This class represents the internal result of an execution attempt for zero or more policies, before or after the
 * policy has handled the result. If a policy is done handling a result or is no longer able to handle a result, such as
 * when retries are exceeded, the ExecutionResult should be marked as complete.
 * <p>This class is immutable.</p>
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public final class ExecutionResult<R> {
  private static final CompletableFuture<?> NULL_FUTURE = CompletableFuture.completedFuture(null);

  private static final ExecutionResult<?> NONE = new ExecutionResult<>(null, null, true, 0, true, true, true);

  /** The execution result, if any */
  private final R result;
  /** The execution exception, if any */
  private final Throwable exception;
  /** Whether the result represents a non result rather than a {@code null} result */
  private final boolean nonResult;
  /** The amount of time to wait prior to the next execution, according to the policy */
  // Do we need this here? perhaps we can set delay nanos right against the Execution internals?
  private final long delayNanos;
  /** Whether a policy has completed handling of the execution */
  private final boolean complete;
  /** Whether a policy determined the execution to be a success */
  private final boolean success;
  /** Whether all policies determined the execution to be a success */
  private final Boolean successAll;

  /**
   * Records an initial execution result with {@code complete} true and {@code success} set to true if {@code exception}
   * is not null.
   */
  public ExecutionResult(R result, Throwable exception) {
    this(result, exception, false, 0, true, exception == null, exception == null);
  }

  private ExecutionResult(R result, Throwable exception, boolean nonResult, long delayNanos, boolean complete,
    boolean success, Boolean successAll) {
    this.nonResult = nonResult;
    this.result = result;
    this.exception = exception;
    this.delayNanos = delayNanos;
    this.complete = complete;
    this.success = success;
    this.successAll = successAll;
  }

  /**
   * Returns a CompletableFuture that is completed with {@code null}. Uses an intern'ed value to avoid new object
   * creation.
   */
  @SuppressWarnings("unchecked")
  public static <R> CompletableFuture<ExecutionResult<R>> nullFuture() {
    return (CompletableFuture<ExecutionResult<R>>) NULL_FUTURE;
  }

  /**
   * Returns an ExecutionResult with the {@code result} set, {@code complete} true and {@code success} true.
   */
  public static <R> ExecutionResult<R> success(R result) {
    return new ExecutionResult<>(result, null, false, 0, true, true, true);
  }

  /**
   * Returns an ExecutionResult with the {@code exception} set, {@code complete} true and {@code success} false.
   */
  public static <R> ExecutionResult<R> exception(Throwable exception) {
    return new ExecutionResult<>(null, exception, false, 0, true, false, false);
  }

  /**
   * Returns an execution that was completed with a non-result. Uses an intern'ed value to avoid new object creation.
   */
  @SuppressWarnings("unchecked")
  public static <R> ExecutionResult<R> none() {
    return (ExecutionResult<R>) NONE;
  }

  public R getResult() {
    return result;
  }

  public Throwable getException() {
    return exception;
  }

  public long getDelay() {
    return delayNanos;
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
  public ExecutionResult<R> withNonResult() {
    return success && this.result == null && nonResult ?
      this :
      new ExecutionResult<>(null, null, true, delayNanos, true, true, successAll);
  }

  /**
   * Returns a copy of the ExecutionResult with the {@code result} value, and complete and success set to true. Returns
   * {@code this} if {@link #success} and {@link #result} are unchanged.
   */
  public ExecutionResult<R> withResult(R result) {
    boolean unchangedNull = this.result == null && result == null && exception == null;
    boolean unchangedNotNull = this.result != null && this.result.equals(result);
    return success && (unchangedNull || unchangedNotNull) ?
      this :
      new ExecutionResult<>(result, null, nonResult, delayNanos, true, true, successAll);
  }

  /**
   * Returns a copy of the ExecutionResult with {@code complete} set to false, else this if nothing has changed.
   */
  public ExecutionResult<R> withNotComplete() {
    return !this.complete ?
      this :
      new ExecutionResult<>(result, exception, nonResult, delayNanos, false, success, successAll);
  }

  /**
   * Returns a copy of the ExecutionResult with success value of {code false}.
   */
  public ExecutionResult<R> withException() {
    return !this.success ?
      this :
      new ExecutionResult<>(result, exception, nonResult, delayNanos, complete, false, false);
  }

  /**
   * Returns a copy of the ExecutionResult with the {@code complete} and {@code success} values of {@code true}.
   */
  public ExecutionResult<R> withSuccess() {
    return this.complete && this.success ?
      this :
      new ExecutionResult<>(result, exception, nonResult, delayNanos, true, true, successAll);
  }

  /**
   * Returns a copy of the ExecutionResult with the {@code delayNanos} value.
   */
  public ExecutionResult<R> withDelay(long delayNanos) {
    return this.delayNanos == delayNanos ?
      this :
      new ExecutionResult<>(result, exception, nonResult, delayNanos, complete, success, successAll);
  }

  /**
   * Returns a copy of the ExecutionResult with the {@code delayNanos}, {@code complete} and {@code success} values.
   */
  public ExecutionResult<R> with(long delayNanos, boolean complete, boolean success) {
    return this.delayNanos == delayNanos && this.complete == complete && this.success == success ?
      this :
      new ExecutionResult<>(result, exception, nonResult, delayNanos, complete, success,
        successAll == null ? success : success && successAll);
  }

  /**
   * Returns whether the execution was successful for all policies.
   */
  public boolean getSuccessAll() {
    return successAll != null && successAll;
  }

  @Override
  public String toString() {
    return "[" + "result=" + result + ", exception=" + exception + ", nonResult=" + nonResult + ", delayNanos="
      + delayNanos + ", complete=" + complete + ", success=" + success + ", successAll=" + successAll + ']';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    ExecutionResult<?> that = (ExecutionResult<?>) o;
    return Objects.equals(result, that.result) && Objects.equals(exception, that.exception);
  }

  @Override
  public int hashCode() {
    return Objects.hash(result, exception);
  }
}