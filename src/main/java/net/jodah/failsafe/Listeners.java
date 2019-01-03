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

import net.jodah.failsafe.event.EventHandler;
import net.jodah.failsafe.event.FailsafeEvent;
import net.jodah.failsafe.function.CheckedConsumer;

/**
 * Utilities for creating listeners.
 *
 * @author Jonathan Halterman
 */
final class Listeners {
  static class ListenerRegistry<T> implements EventHandler {
    CheckedConsumer<? extends FailsafeEvent> abortListener;
    CheckedConsumer<? extends FailsafeEvent> completeListener;
    CheckedConsumer<? extends FailsafeEvent> failedAttemptListener;
    CheckedConsumer<? extends FailsafeEvent> failureListener;
    CheckedConsumer<? extends FailsafeEvent> retriesExceededListener;
    CheckedConsumer<? extends FailsafeEvent> retryListener;
    CheckedConsumer<? extends FailsafeEvent> successListener;

    @SuppressWarnings("unchecked")
    void call(CheckedConsumer<? extends FailsafeEvent> listener, ExecutionResult result, ExecutionContext context) {
      try {
        FailsafeEvent<T> event = new FailsafeEvent(result.result, result.failure, context);
        ((CheckedConsumer<FailsafeEvent>) listener).accept(event);
      } catch (Exception ignore) {
      }
    }

    @Override
    public void handleAbort(ExecutionResult result, ExecutionContext context) {
      if (abortListener != null)
        call(abortListener, result, context.copy());
    }

    @Override
    public void handleComplete(ExecutionResult result, ExecutionContext context) {
      if (result.success)
        handleSuccess(result, context);
      else
        handleFailure(result, context);

      if (completeListener != null)
        call(completeListener, result, context.copy());
    }

    @Override
    public void handleFailedAttempt(ExecutionResult result, ExecutionContext context) {
      if (failedAttemptListener != null)
        call(failedAttemptListener, result, context.copy());
    }

    @Override
    public void handleRetriesExceeded(ExecutionResult result, ExecutionContext context) {
      if (retriesExceededListener != null)
        call(retriesExceededListener, result, context.copy());
    }

    @Override
    public void handleRetry(ExecutionResult result, ExecutionContext context) {
      if (retryListener != null)
        call(retryListener, result, context.copy());
    }

    private void handleFailure(ExecutionResult result, ExecutionContext context) {
      if (failureListener != null)
        call(failureListener, result, context.copy());
    }

    private void handleSuccess(ExecutionResult result, ExecutionContext context) {
      if (successListener != null)
        call(successListener, result, context.copy());
    }
  }
}
