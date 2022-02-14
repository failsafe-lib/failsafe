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

import dev.failsafe.ExecutionContext;

/**
 * Internal execution APIs.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public interface ExecutionInternal<R> extends ExecutionContext<R> {
  /**
   * Returns the recorded result for an execution attempt.
   */
  ExecutionResult<R> getResult();

  /**
   * Called when execution of the user's supplier is about to begin.
   */
  void preExecute();

  /**
   * Returns whether the execution has been pre-executed, indicating the attempt has started.
   */
  boolean isPreExecuted();

  /**
   * Records an execution attempt which may correspond with an execution result. Async executions will have results
   * recorded separately.
   */
  void recordAttempt();

  /**
   * Records the {@code result} if the execution has been {@link #preExecute() pre-executed} and a result has not
   * already been recorded.
   */
  void record(ExecutionResult<R> result);

  /**
   * Marks the execution as having been cancelled externally, which will cancel pending executions of all policies.
   *
   * @return whether cancellation was successful or not. Returns {@code false} if the execution was already cancelled or
   * completed.
   */
  boolean cancel();

  /**
   * Marks the execution as having been cancelled by the {@code policyExecutor}, which will also cancel pending
   * executions of any inner policies of the {@code policyExecutor}. Outer policies of the {@code policyExecutor} will
   * be unaffected.
   */
  void cancel(PolicyExecutor<R> policyExecutor);

  /**
   * Returns whether the execution is considered cancelled for the {@code policyExecutor}.
   */
  boolean isCancelled(PolicyExecutor<R> policyExecutor);
}
