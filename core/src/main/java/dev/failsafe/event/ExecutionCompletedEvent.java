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
 * Indicates an execution was completed or cancelled.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public class ExecutionCompletedEvent<R> extends ExecutionEvent {
  private final R result;
  private final Throwable exception;

  public ExecutionCompletedEvent(R result, Throwable exception, ExecutionContext<R> context) {
    super(context);
    this.result = result;
    this.exception = exception;
  }

  /**
   * Returns the failure that preceded the event, else {@code null} if there was none.
   */
  public Throwable getException() {
    return exception;
  }

  /**
   * Returns the result that preceded the event, else {@code null} if there was none.
   */
  public R getResult() {
    return result;
  }

  @Override
  public String toString() {
    return "ExecutionCompletedEvent[" + "result=" + result + ", exception=" + exception + ']';
  }
}
