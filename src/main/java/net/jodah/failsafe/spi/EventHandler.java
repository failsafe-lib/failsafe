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
package net.jodah.failsafe.spi;

import net.jodah.failsafe.ExecutionContext;
import net.jodah.failsafe.event.ExecutionAttemptedEvent;
import net.jodah.failsafe.event.ExecutionCompletedEvent;
import net.jodah.failsafe.event.ExecutionScheduledEvent;
import net.jodah.failsafe.function.CheckedConsumer;

import java.time.Duration;

/**
 * Handling for execution events.
 *
 * @param <R> result type
 */
public interface EventHandler<R> {
  void handle(ExecutionResult<R> result, ExecutionContext<R> context);

  static <R> EventHandler<R> ofCompleted(CheckedConsumer<ExecutionCompletedEvent<R>> handler) {
    return (result, context) -> {
      try {
        handler.accept(new ExecutionCompletedEvent<>(result.getResult(), result.getFailure(), context));
      } catch (Throwable ignore) {
      }
    };
  }

  static <R> EventHandler<R> ofAttempted(CheckedConsumer<ExecutionAttemptedEvent<R>> handler) {
    return (result, context) -> {
      try {
        handler.accept(new ExecutionAttemptedEvent<>(result.getResult(), result.getFailure(), context));
      } catch (Throwable ignore) {
      }
    };
  }

  static <R> EventHandler<R> ofScheduled(CheckedConsumer<ExecutionScheduledEvent<R>> handler) {
    return (result, context) -> {
      try {
        handler.accept(
          new ExecutionScheduledEvent<>(result.getResult(), result.getFailure(), Duration.ofNanos(result.getDelay()),
            context));
      } catch (Throwable ignore) {
      }
    };
  }
}