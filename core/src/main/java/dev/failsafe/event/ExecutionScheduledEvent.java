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
import dev.failsafe.Timeout;
import dev.failsafe.spi.Scheduler;

import java.time.Duration;

/**
 * Indicates an execution was scheduled. A scheduled execution will be executed after the {@link #getDelay() delay}
 * unless it is cancelled, either explicitly or via {@link java.util.concurrent.Future#cancel(boolean)
 * Future.cancel(boolean)}, a {@link Timeout Timeout}, or if the underlying {@link Scheduler Scheduler} or {@link
 * java.util.concurrent.ExecutorService ExecutorService} is shutdown.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public class ExecutionScheduledEvent<R> extends ExecutionEvent {
  private final R result;
  private final Throwable exception;
  private final Duration delay;

  public ExecutionScheduledEvent(R result, Throwable exception, Duration delay, ExecutionContext<R> context) {
    super(context);
    this.result = result;
    this.exception = exception;
    this.delay = delay;
  }

  /**
   * Returns the failure that preceded the event, else {@code null} if there was none.
   */
  public Throwable getLastException() {
    return exception;
  }

  /**
   * @deprecated Use {@link #getLastException()} instead
   */
  @Deprecated
  public Throwable getLastFailure() {
    return exception;
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
    return delay;
  }

  @Override
  public String toString() {
    return "ExecutionScheduledEvent[" + "result=" + result + ", exception=" + exception + ", delay=" + delay + ']';
  }
}
