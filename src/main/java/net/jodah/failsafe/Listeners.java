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

import net.jodah.failsafe.event.ContextualResultListener;
import net.jodah.failsafe.event.EventHandler;
import net.jodah.failsafe.function.CheckedBiConsumer;
import net.jodah.failsafe.function.CheckedConsumer;
import net.jodah.failsafe.internal.util.Assert;
import net.jodah.failsafe.util.concurrent.Scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Utilities for creating listeners.
 *
 * @author Jonathan Halterman
 */
final class Listeners {
  static class ListenerRegistry<T> implements EventHandler {
    private List<ContextualResultListener<T, Throwable>> abortListeners;
    private List<ContextualResultListener<T, Throwable>> completeListeners;
    private List<ContextualResultListener<T, Throwable>> failedAttemptListeners;
    private List<ContextualResultListener<T, Throwable>> failureListeners;
    private List<ContextualResultListener<T, Throwable>> retriesExceededListeners;
    private List<ContextualResultListener<T, Throwable>> retryListeners;
    private List<ContextualResultListener<T, Throwable>> successListeners;

    List<ContextualResultListener<T, Throwable>> abort() {
      return abortListeners != null ? abortListeners : (abortListeners = new ArrayList<>(2));
    }

    List<ContextualResultListener<T, Throwable>> complete() {
      return completeListeners != null ? completeListeners : (completeListeners = new ArrayList<>(2));
    }

    List<ContextualResultListener<T, Throwable>> failedAttempt() {
      return failedAttemptListeners != null ? failedAttemptListeners : (failedAttemptListeners = new ArrayList<>(2));
    }

    List<ContextualResultListener<T, Throwable>> failure() {
      return failureListeners != null ? failureListeners : (failureListeners = new ArrayList<>(2));
    }

    List<ContextualResultListener<T, Throwable>> retriesExceeded() {
      return retriesExceededListeners != null ?
          retriesExceededListeners :
          (retriesExceededListeners = new ArrayList<>(2));
    }

    List<ContextualResultListener<T, Throwable>> retry() {
      return retryListeners != null ? retryListeners : (retryListeners = new ArrayList<>(2));
    }

    List<ContextualResultListener<T, Throwable>> success() {
      return successListeners != null ? successListeners : (successListeners = new ArrayList<>(2));
    }

    @SuppressWarnings("unchecked")
    void call(List<ContextualResultListener<T, Throwable>> listeners, ExecutionResult result,
        ExecutionContext context) {
      for (ContextualResultListener<T, Throwable> listener : listeners) {
        try {
          listener.onResult((T) result.result, result.failure, context);
        } catch (Exception ignore) {
        }
      }
    }

    @Override
    public void handleAbort(ExecutionResult result, ExecutionContext context) {
      if (abortListeners != null)
        call(abortListeners, result, context.copy());
    }

    @Override
    public void handleComplete(ExecutionResult result, ExecutionContext context) {
      if (result.success)
        handleSuccess(result, context);
      else
        handleFailure(result, context);

      if (completeListeners != null)
        call(completeListeners, result, context.copy());
    }

    @Override
    public void handleFailedAttempt(ExecutionResult result, ExecutionContext context) {
      if (failedAttemptListeners != null)
        call(failedAttemptListeners, result, context.copy());
    }

    @Override
    public void handleRetriesExceeded(ExecutionResult result, ExecutionContext context) {
      if (retriesExceededListeners != null)
        call(retriesExceededListeners, result, context.copy());
    }

    @Override
    public void handleRetry(ExecutionResult result, ExecutionContext context) {
      if (retryListeners != null)
        call(retryListeners, result, context.copy());
    }

    private void handleFailure(ExecutionResult result, ExecutionContext context) {
      if (failureListeners != null)
        call(failureListeners, result, context.copy());
    }

    private void handleSuccess(ExecutionResult result, ExecutionContext context) {
      if (successListeners != null)
        call(successListeners, result, context.copy());
    }
  }

  @SuppressWarnings("unchecked")
  static <T> ContextualResultListener<T, Throwable> of(
      final ContextualResultListener<? extends T, ? extends Throwable> listener, final ExecutorService executor,
      final Scheduler scheduler) {
    return (result, failure, context) -> {
      Callable<T> callable = () -> {
        ((ContextualResultListener<T, Throwable>) listener).onResult(result, failure, context);
        return null;
      };

      try {
        if (executor != null)
          executor.submit(callable);
        else
          scheduler.schedule(callable, 0, TimeUnit.MILLISECONDS);
      } catch (Exception ignore) {
      }
    };
  }

  @SuppressWarnings("unchecked")
  static <T> ContextualResultListener<T, Throwable> of(final CheckedConsumer<? extends Throwable> listener) {
    Assert.notNull(listener, "listener");
    return (result, failure, context) -> ((CheckedConsumer<Throwable>) listener).accept(failure);
  }

  @SuppressWarnings("unchecked")
  static <T> ContextualResultListener<T, Throwable> of(
      final CheckedBiConsumer<? extends T, ? extends Throwable> listener) {
    Assert.notNull(listener, "listener");
    return (result, failure, context) -> ((CheckedBiConsumer<T, Throwable>) listener).accept(result, failure);
  }

  @SuppressWarnings("unchecked")
  static <T> ContextualResultListener<T, Throwable> ofResult(final CheckedConsumer<? extends T> listener) {
    Assert.notNull(listener, "listener");
    return (result, failure, context) -> ((CheckedConsumer<T>) listener).accept(result);
  }

  @SuppressWarnings("unchecked")
  static <T> ContextualResultListener<T, Throwable> ofResult(
      final CheckedBiConsumer<? extends T, ExecutionContext> listener) {
    Assert.notNull(listener, "listener");
    return (result, failure, context) -> ((CheckedBiConsumer<T, ExecutionContext>) listener).accept(result, context);
  }
}
