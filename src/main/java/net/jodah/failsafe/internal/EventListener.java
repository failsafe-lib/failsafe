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
package net.jodah.failsafe.internal;

import net.jodah.failsafe.ExecutionContext;
import net.jodah.failsafe.ExecutionResult;
import net.jodah.failsafe.event.ExecutionAttemptedEvent;
import net.jodah.failsafe.event.ExecutionCompletedEvent;
import net.jodah.failsafe.function.CheckedConsumer;

/**
 * Handles an execution event.
 */
public interface EventListener {
  void handle(Object result, Throwable failure, ExecutionContext context);

  @SuppressWarnings("unchecked")
  static <R> EventListener of(CheckedConsumer<? extends ExecutionCompletedEvent<R>> handler) {
    return (Object result, Throwable failure, ExecutionContext context) -> {
      try {
        ((CheckedConsumer<ExecutionCompletedEvent<R>>) handler).accept(
            new ExecutionCompletedEvent<>((R) result, failure, context));
      } catch (Throwable ignore) {
      }
    };
  }

  @SuppressWarnings("unchecked")
  static <R> EventListener ofAttempt(CheckedConsumer<? extends ExecutionAttemptedEvent<R>> handler) {
    return (Object result, Throwable failure, ExecutionContext context) -> {
      try {
        ((CheckedConsumer<ExecutionAttemptedEvent<R>>) handler).accept(
            new ExecutionAttemptedEvent<>((R) result, failure, context));
      } catch (Throwable ignore) {
      }
    };
  }

  default void handle(ExecutionResult result, ExecutionContext context) {
    handle(result.getResult(), result.getFailure(), context);
  }
}