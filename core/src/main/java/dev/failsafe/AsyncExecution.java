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
package dev.failsafe;

import java.util.concurrent.CompletableFuture;

/**
 * Allows asynchronous executions to record their results or complete an execution.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public interface AsyncExecution<R> extends ExecutionContext<R> {
  /**
   * Completes the execution and the associated {@code CompletableFuture}.
   *
   * @throws IllegalStateException if the execution is already recorded or complete
   */
  void complete();

  /**
   * Returns whether the execution is complete or if it can be retried. An execution is considered complete only when
   * all configured policies consider the execution complete.
   */
  boolean isComplete();

  /**
   * Records an execution {@code result} or {@code exception} which triggers failure handling, if needed, by the
   * configured policies. If policy handling is not possible or already complete, the resulting {@link
   * CompletableFuture} is completed.
   *
   * @throws IllegalStateException if the most recent execution was already recorded or the execution is complete
   */
  void record(R result, Throwable exception);

  /**
   * Records an execution {@code result} which triggers failure handling, if needed, by the configured policies. If
   * policy handling is not possible or already complete, the resulting {@link CompletableFuture} is completed.
   *
   * @throws IllegalStateException if the most recent execution was already recorded or the execution is complete
   */
  void recordResult(R result);

  /**
   * Records an {@code exception} which triggers failure handling, if needed, by the configured policies. If policy
   * handling is not possible or already complete, the resulting {@link CompletableFuture} is completed.
   *
   * @throws IllegalStateException if the most recent execution was already recorded or the execution is complete
   */
  void recordException(Throwable exception);

  /**
   * @deprecated Use {@link #recordException(Throwable)} instead
   */
  @Deprecated
  void recordFailure(Throwable failure);
}