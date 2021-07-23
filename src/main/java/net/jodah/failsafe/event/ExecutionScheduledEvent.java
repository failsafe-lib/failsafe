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
package net.jodah.failsafe.event;

import net.jodah.failsafe.AbstractExecution;
import net.jodah.failsafe.ExecutionContext;

import java.time.Duration;

/**
 * Indicates an execution was scheduled.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public class ExecutionScheduledEvent<R> extends ExecutionEvent {
  private final R result;
  private final Throwable failure;
  private final AbstractExecution execution;

  public ExecutionScheduledEvent(R result, Throwable failure, AbstractExecution execution, ExecutionContext context) {
    super(context);
    this.result = result;
    this.failure = failure;
    this.execution = execution;
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

  /**
   * Returns the delay before the next execution attempt.
   */
  public Duration getDelay() {
    return execution.getWaitTime();
  }

  @Override
  public String toString() {
    return "ExecutionScheduledEvent[" + "result=" + result + ", failure=" + failure + ']';
  }
}
