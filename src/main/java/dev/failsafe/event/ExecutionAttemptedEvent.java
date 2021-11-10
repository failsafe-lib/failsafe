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

import dev.failsafe.ExecutionContext;

/**
 * Indicates an execution was attempted.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public class ExecutionAttemptedEvent<R> extends ExecutionEvent {
  private final R result;
  private final Throwable failure;

  public ExecutionAttemptedEvent(R result, Throwable failure, ExecutionContext<R> context) {
    super(context);
    this.result = result;
    this.failure = failure;
  }

  /**
   * Returns the failure that preceded the event, else {@code null} if there was none.
   */
  public Throwable getLastFailure() {
    return failure;
  }

  /**
   * Returns the result that preceded the event, else {@code null} if there was none.
   */
  public R getLastResult() {
    return result;
  }

  @Override
  public String toString() {
    return "ExecutionAttemptedEvent[" + "result=" + result + ", failure=" + failure + ']';
  }
}
