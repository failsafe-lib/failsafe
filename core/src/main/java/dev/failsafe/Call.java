/*
 * Copyright 2022 the original author or authors.
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
package dev.failsafe;

import dev.failsafe.function.CheckedRunnable;

/**
 * A call that can perform Failsafe executions and can be cancelled. Cancellations are propagated to any {@link
 * ExecutionContext#onCancel(CheckedRunnable) cancelCallback} that is registered. Useful for integrating with libraries
 * that support cancellation.
 * <p>
 * To perform cancellable async executions, use the {@link FailsafeExecutor} async methods.
 * </p>
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public interface Call<R> {
  /**
   * Executes the call until a successful result is returned or the configured policies are exceeded.
   *
   * @throws FailsafeException if the execution fails with a checked Exception. {@link FailsafeException#getCause()} can
   * be used to learn the underlying checked exception.
   */
  R execute();

  /**
   * Cancels a synchronous execution and calls the most recent {@link ExecutionContext#onCancel(CheckedRunnable)
   * cancelCallback} that was registered. The execution is still allowed to complete and return a result. In addition to
   * using a {@link ExecutionContext#onCancel(CheckedRunnable) cancelCallback}, executions can cooperate with
   * cancellation by checking {@link ExecutionContext#isCancelled()}.
   *
   * @param mayInterruptIfRunning whether the execution should be interrupted
   * @return whether cancellation was successful or not. Returns {@code false} if the execution was already cancelled or
   * completed.
   */
  boolean cancel(boolean mayInterruptIfRunning);

  /**
   * Returns whether the call has been cancelled.
   */
  boolean isCancelled();
}
