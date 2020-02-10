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

/**
 * The result of an execution. Immutable.
 * <p>
 * Part of the Failsafe SPI.
 *
 * @author Jonathan Halterman
 */
public class ExecutionResultWithException<E extends Throwable> extends ExecutionResult {
  public ExecutionResultWithException(Object result, E failure) {
    this(result, failure, false, 0, false, failure == null, failure == null);
  }

  private ExecutionResultWithException(Object result, E failure, boolean nonResult, long waitNanos, boolean complete,
    boolean success, Boolean successAll) {
	  super(result, failure, nonResult, waitNanos, complete, success, successAll);
  }

  /**
   * Returns a an ExecutionResult with the {@code result} set, {@code completed} true and {@code success} true.
   */
  public static <E extends Throwable> ExecutionResultWithException<E> successWithException(Object result) {
    return new ExecutionResultWithException<>(result, null);
  }

  /**
   * Returns a an ExecutionResult with the {@code failure} set, {@code completed} true and {@code success} false.
   */
  public static <E extends Throwable> ExecutionResultWithException<E> failureWithException(E failure) {
    return new ExecutionResultWithException<>(null, failure, false, 0, true, false, false);
  }

  @SuppressWarnings("unchecked")
  public E getFailure() {
    return (E) super.getFailure();
  }

  /**
   * Returns a copy of the ExecutionResult with a non-result, and completed and success set to true. Returns
   * {@code this} if {@link #success} and {@link #result} are unchanged.
   */
  ExecutionResultWithException<E> withNonResult() {
    return super.isSuccess() && super.getResult() == null && super.isNonResult() ?
      this :
      new ExecutionResultWithException<E>(null, null, true, super.getWaitNanos(), true, true, super.getSuccessAll());
  }

  /**
   * Returns a copy of the ExecutionResult with the {@code result} value, and completed and success set to true. Returns
   * {@code this} if {@link #success} and {@link #result} are unchanged.
   */
  public ExecutionResultWithException<E> withResult(Object result) {
    return super.isSuccess() && ((super.getResult() == null && result == null) || (super.getResult() != null && super.getResult().equals(result))) ?
      this :
      new ExecutionResultWithException<>(result, null, super.isNonResult(), super.getWaitNanos(), true, true, super.getSuccessAll());
  }

  /**
   * Returns a copy of the ExecutionResult with the value set to true, else this if nothing has changed.
   */
  @SuppressWarnings("unchecked")
  public ExecutionResultWithException<E> withComplete() {
    return super.isComplete() ? this : new ExecutionResultWithException<E>(super.getResult(), (E) super.getFailure(), super.isNonResult(), super.getWaitNanos(), true, super.isSuccess(), super.getSuccessAll());
  }

  /**
   * Returns a copy of the ExecutionResult with the {@code completed} and {@code success} values.
   */
  @SuppressWarnings("unchecked")
  ExecutionResultWithException<E> with(boolean completed, boolean success) {
    return super.isComplete() == completed && super.isSuccess() == success ?
      this :
      new ExecutionResultWithException<E>(super.getResult(), (E) super.getFailure(), super.isNonResult(), super.getWaitNanos(), completed, success,
        super.isSuccess() && super.getSuccessAll());
  }

  /**
   * Returns a copy of the ExecutionResult with the {@code waitNanos}, {@code completed} and {@code success} values.
   */
  @SuppressWarnings("unchecked")
  public ExecutionResultWithException<E> with(long waitNanos, boolean completed, boolean success) {
    return super.getWaitNanos() == waitNanos && super.isComplete() == completed && super.isSuccess() == success ?
      this :
      new ExecutionResultWithException<E>(super.getResult(), (E) super.getFailure(), super.isNonResult(), waitNanos, completed, success,
        super.isSuccess() && super.getSuccessAll());
  }

  @Override
  public String toString() {
    return "ExecutionResult[" + "result=" + super.getResult() + ", failure=" + super.getFailure() + ", nonResult=" + super.isNonResult()
      + ", waitNanos=" + super.getWaitNanos() + ", complete=" + super.isComplete() + ", success=" + super.isSuccess() + ", successAll=" + super.getSuccessAll()
      + ']';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    ExecutionResultWithException<E> that = (ExecutionResultWithException<E>) o;
    return Objects.equals(super.getResult(), that.getResult()) && Objects.equals(super.getFailure(), that.getFailure());
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.getResult(), super.getFailure());
  }
}