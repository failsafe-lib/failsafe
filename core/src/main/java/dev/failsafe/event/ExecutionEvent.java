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
package dev.failsafe.event;

import dev.failsafe.CircuitBreaker;
import dev.failsafe.ExecutionContext;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Encapsulates information about a Failsafe execution.
 *
 * @author Jonathan Halterman
 */
public abstract class ExecutionEvent {
  private final ExecutionContext<?> context;

  ExecutionEvent(ExecutionContext<?> context) {
    this.context = context;
  }

  /**
   * Returns the elapsed time since initial execution began.
   */
  public Duration getElapsedTime() {
    return context.getElapsedTime();
  }

  /**
   * Gets the number of execution attempts so far, including attempts that are blocked before being executed, such as
   * when a {@link CircuitBreaker CircuitBreaker} is open. Will return {@code 0} when the first
   * attempt is in progress or has yet to begin.
   */
  public int getAttemptCount() {
    return context.getAttemptCount();
  }

  /**
   * Gets the number of completed executions so far. Executions that are blocked, such as when a {@link
   * CircuitBreaker CircuitBreaker} is open, are not counted. Will return {@code 0} when the first
   * attempt is in progress or has yet to begin.
   */
  public int getExecutionCount() {
    return context.getExecutionCount();
  }

  /**
   * Returns the time that the initial execution started, else {code null} if an execution has not started yet.
   */
  public Optional<Instant> getStartTime() {
    return Optional.ofNullable(context.getStartTime());
  }

  /**
   * Returns the elapsed time since the last execution attempt began.
   */
  public Duration getElapsedAttemptTime() {
    return context.getElapsedAttemptTime();
  }

  /**
   * Returns {@code true} when {@link #getAttemptCount()} is {@code 0} meaning this is the first execution attempt.
   */
  public boolean isFirstAttempt() {
    return context.isFirstAttempt();
  }

  /**
   * Returns {@code true} when {@link #getAttemptCount()} is {@code > 0} meaning the execution is being retried.
   */
  public boolean isRetry() {
    return context.isRetry();
  }
}
