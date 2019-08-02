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
 * @author Jonathan Halterman
 */
public class ExecutionContext {
  private final Duration startTime;
  /** Number of execution attempts */
  AtomicInteger attempts = new AtomicInteger();

  // Internally mutable state
  volatile Object lastResult;
  volatile Throwable lastFailure;

  ExecutionContext(Duration startTime) {
    this.startTime = startTime;
  }

  private ExecutionContext(ExecutionContext context) {
    this.startTime = context.startTime;
    this.attempts = context.attempts;
    this.lastResult = context.lastResult;
    this.lastFailure = context.lastFailure;
  }

  /**
   * Returns the elapsed time since initial execution began.
   */
  public Duration getElapsedTime() {
    return Duration.ofNanos(System.nanoTime() - startTime.toNanos());
  }

  /**
   * Gets the number of execution attempts so far.
   */
  public int getAttemptCount() {
    return attempts.get();
  }

  /**
   * Returns the last failure that was recorded else {@code null}.
   */
  @SuppressWarnings("unchecked")
  public <T extends Throwable> T getLastFailure() {
    return (T) lastFailure;
  }

  /**
   * Returns the last result that was recorded else {@code null}.
   */
  @SuppressWarnings("unchecked")
  public <T> T getLastResult() {
    return (T) lastResult;
  }

  /**
   * Returns the last result that was recorded else the {@code defaultValue}.
   */
  @SuppressWarnings("unchecked")
  public <T> T getLastResult(T defaultValue) {
    return lastResult != null ? (T) lastResult : defaultValue;
  }

  /**
   * Returns the time that the initial execution started.
   */
  public Duration getStartTime() {
    return startTime;
  }

  public ExecutionContext copy() {
    return new ExecutionContext(this);
  }

  static ExecutionContext ofResult(Object result) {
    ExecutionContext context = new ExecutionContext((Duration)null);
    context.lastResult = result;
    return context;
  }

  static ExecutionContext ofFailure(Throwable failure) {
    ExecutionContext context = new ExecutionContext((Duration)null);
    context.lastFailure = failure;
    return context;
  }
}
