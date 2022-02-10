/*
 * Copyright 2016 the original author or authors.
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

import java.time.Duration;

/**
 * Contextual execution information.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public interface ExecutionContext<R> {
  /**
   * Returns the elapsed time since initial execution began.
   */
  Duration getElapsedTime();

  /**
   * Returns the elapsed time since the last execution attempt began.
   */
  Duration getElapsedAttemptTime();

  /**
   * Gets the number of execution attempts so far, including attempts that are blocked before being executed, such as
   * when a {@link CircuitBreaker} is open. Will return {@code 0} when the first attempt is in progress or has yet to
   * begin.
   */
  int getAttemptCount();

  /**
   * Gets the number of completed executions so far. Executions that are blocked, such as when a {@link CircuitBreaker}
   * is open, are not counted. Will return {@code 0} when the first attempt is in progress or has yet to begin.
   */
  int getExecutionCount();

  /**
   * Returns the last exception that was recorded else {@code null}.
   */
  <T extends Throwable> T getLastException();

  /**
   * @deprecated Use {@link #getLastException()} instead
   */
  @Deprecated
  <T extends Throwable> T getLastFailure();

  /**
   * Returns the last result that was recorded else {@code null}.
   */
  R getLastResult();

  /**
   * Returns the last result that was recorded else the {@code defaultValue}.
   */
  R getLastResult(R defaultValue);

  /**
   * Returns the time that the initial execution started.
   */
  Duration getStartTime();

  /**
   * Returns whether the execution has been cancelled. In this case the implementor should attempt to stop execution.
   */
  boolean isCancelled();

  /**
   * Returns {@code true} when an execution result has not yet been recorded, meaning this is the first execution
   * attempt.
   */
  boolean isFirstAttempt();

  /**
   * Returns {@code true} when an execution result has already been recorded, meaning the execution is being retried.
   */
  boolean isRetry();
}
