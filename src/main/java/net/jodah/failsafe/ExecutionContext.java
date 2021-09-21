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
package net.jodah.failsafe;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Contextual execution information.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public class ExecutionContext<R> {
  // -- Cross-attempt state --

  // When the first execution attempt was started
  volatile Duration startTime;
  // Number of execution attempts
  AtomicInteger attempts;
  // Number of completed executions
  AtomicInteger executions;

  // -- Per-attempt state --

  // The result of the previous execution attempt
  private ExecutionResult previousResult;
  // The result of the current execution attempt;
  volatile ExecutionResult result;
  // When the most recent execution attempt was started
  volatile Duration attemptStartTime;
  // The index of a PolicyExecutor that cancelled the execution. Integer.MIN_VALUE represents non-cancelled.
  volatile int cancelledIndex = Integer.MIN_VALUE;

  ExecutionContext() {
    startTime = Duration.ZERO;
    attemptStartTime = Duration.ZERO;
    attempts = new AtomicInteger();
    executions = new AtomicInteger();
  }

  ExecutionContext(ExecutionContext<R> context) {
    this.startTime = context.startTime;
    this.attempts = context.attempts;
    this.executions = context.executions;
    previousResult = context.result;
  }

  /**
   * Returns the elapsed time since initial execution began.
   */
  public Duration getElapsedTime() {
    return Duration.ofNanos(System.nanoTime() - startTime.toNanos());
  }

  /**
   * Returns the elapsed time since the last execution attempt began.
   */
  public Duration getElapsedAttemptTime() {
    return Duration.ofNanos(System.nanoTime() - attemptStartTime.toNanos());
  }

  /**
   * Gets the number of execution attempts so far, including attempts that are blocked before being executed, such as
   * when a {@link net.jodah.failsafe.CircuitBreaker CircuitBreaker} is open. Will return {@code 0} when the first
   * attempt is in progress or has yet to begin.
   */
  public int getAttemptCount() {
    return attempts.get();
  }

  /**
   * Gets the number of completed executions so far. Executions that are blocked, such as when a {@link
   * net.jodah.failsafe.CircuitBreaker CircuitBreaker} is open, are not counted. Will return {@code 0} when the first
   * attempt is in progress or has yet to begin.
   */
  public int getExecutionCount() {
    return executions.get();
  }

  /**
   * Returns the last failure that was recorded else {@code null}.
   */
  @SuppressWarnings("unchecked")
  public <T extends Throwable> T getLastFailure() {
    ExecutionResult r = result != null ? result : previousResult;
    return r == null ? null : (T) r.getFailure();
  }

  /**
   * Returns the last result that was recorded else {@code null}.
   */
  @SuppressWarnings("unchecked")
  public R getLastResult() {
    ExecutionResult r = result != null ? result : previousResult;
    return r == null ? null : (R) r.getResult();
  }

  /**
   * Returns the last result that was recorded else the {@code defaultValue}.
   */
  @SuppressWarnings("unchecked")
  public R getLastResult(R defaultValue) {
    ExecutionResult r = result != null ? result : previousResult;
    return r == null ? defaultValue : (R) r.getResult();
  }

  /**
   * Returns the time that the initial execution started.
   */
  public Duration getStartTime() {
    return startTime;
  }

  /**
   * Returns whether the execution has been cancelled. In this case the implementor should attempt to stop execution.
   */
  public boolean isCancelled() {
    return cancelledIndex > Integer.MIN_VALUE;
  }

  /**
   * Returns {@code true} when {@link #getAttemptCount()} is {@code 0} meaning this is the first execution attempt.
   */
  public boolean isFirstAttempt() {
    return attempts.get() == 0;
  }

  /**
   * Returns {@code true} when {@link #getAttemptCount()} is {@code > 0} meaning the execution is being retried.
   */
  public boolean isRetry() {
    return attempts.get() > 0;
  }

  static <R> ExecutionContext<R> ofResult(R result) {
    ExecutionContext<R> context = new ExecutionContext<>();
    context.previousResult = ExecutionResult.success(result);
    return context;
  }

  static <R> ExecutionContext<R> ofFailure(Throwable failure) {
    ExecutionContext<R> context = new ExecutionContext<>();
    context.previousResult = ExecutionResult.failure(failure);
    return context;
  }

  @Override
  public String toString() {
    return "ExecutionContext[" + "attempts=" + attempts + ", executions=" + executions + ", lastResult="
      + getLastResult() + ", lastFailure=" + getLastFailure() + ']';
  }
}
