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

import net.jodah.failsafe.function.*;
import net.jodah.failsafe.internal.util.Assert;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Semaphore;

/**
 * Utilities for creating functions.
 *
 * @author Jonathan Halterman
 */
final class Functions {
  static <T> Callable<T> asyncOf(AsyncCallable<T> callable, AsyncExecution execution) {
    Assert.notNull(callable, "callable");
    return new Callable<T>() {
      @Override
      public synchronized T call() {
        try {
          execution.preExecute();
          return callable.call(execution);
        } catch (Throwable e) {
          execution.completeOrRetry(null, e);
          return null;
        }
      }
    };
  }

  static <T> Callable<T> asyncOf(AsyncRunnable runnable, AsyncExecution execution) {
    Assert.notNull(runnable, "runnable");
    return new Callable<T>() {
      @Override
      public synchronized T call() {
        try {
          execution.preExecute();
          runnable.run(execution);
        } catch (Throwable e) {
          execution.completeOrRetry(null, e);
        }

        return null;
      }
    };
  }

  static <T> Callable<T> asyncOf(Callable<T> callable, AsyncExecution execution) {
    Assert.notNull(callable, "callable");
    return () -> {
      try {
        execution.preExecute();
        T result = callable.call();
        execution.completeOrRetry(result, null);
        return result;
      } catch (Throwable e) {
        execution.completeOrRetry(null, e);
        return null;
      }
    };
  }

  static <T> Callable<T> asyncOf(CheckedRunnable runnable, AsyncExecution execution) {
    Assert.notNull(runnable, "runnable");
    return () -> {
      try {
        execution.preExecute();
        runnable.run();
        execution.completeOrRetry(null, null);
      } catch (Throwable e) {
        execution.completeOrRetry(null, e);
      }

      return null;
    };
  }

  static <T> Callable<T> asyncOf(ContextualCallable<T> callable, AsyncExecution execution) {
    Assert.notNull(callable, "callable");
    return () -> {
      try {
        execution.preExecute();
        T result = callable.call(execution);
        execution.completeOrRetry(result, null);
        return result;
      } catch (Throwable e) {
        execution.completeOrRetry(null, e);
        return null;
      }
    };
  }

  static <T> Callable<T> asyncOf(ContextualRunnable runnable, AsyncExecution execution) {
    Assert.notNull(runnable, "runnable");
    return () -> {
      try {
        execution.preExecute();
        runnable.run(execution);
        execution.completeOrRetry(null, null);
      } catch (Throwable e) {
        execution.completeOrRetry(null, e);
      }

      return null;
    };
  }

  static <T> Callable<T> asyncOfFuture(AsyncCallable<? extends CompletionStage<T>> callable, AsyncExecution execution) {
    Assert.notNull(callable, "callable");
    return new Callable<T>() {
      Semaphore asyncFutureLock = new Semaphore(1);

      @Override
      public T call() {
        try {
          execution.preExecute();
          asyncFutureLock.acquire();
          callable.call(execution).whenComplete((innerResult, failure) -> {
            try {
              if (failure != null)
                execution.completeOrRetry(innerResult, failure instanceof CompletionException ? failure.getCause() : failure);
            } finally {
              asyncFutureLock.release();
            }
          });
        } catch (Throwable e) {
          try {
            execution.completeOrRetry(null, e);
          } finally {
            asyncFutureLock.release();
          }
        }

        return null;
      }
    };
  }

  static <T> Callable<T> asyncOfFuture(Callable<? extends CompletionStage<T>> callable, AsyncExecution execution) {
    Assert.notNull(callable, "callable");
    return () -> {
      try {
        execution.preExecute();
        callable.call().whenComplete((innerResult, failure) -> {
          // Unwrap CompletionException cause
          if (failure instanceof CompletionException)
            failure = failure.getCause();
          execution.completeOrRetry(innerResult, failure);
        });
      } catch (Throwable e) {
        execution.completeOrRetry(null, e);
      }

      return null;
    };
  }

  static <T> Callable<T> asyncOfFuture(ContextualCallable<? extends CompletionStage<T>> callable, AsyncExecution execution) {
    Assert.notNull(callable, "callable");
    return () -> {
      try {
        execution.preExecute();
        callable.call(execution).whenComplete((innerResult, failure) -> {
          // Unwrap CompletionException cause
          if (failure instanceof CompletionException)
            failure = failure.getCause();
          execution.completeOrRetry(innerResult, failure);
        });
      } catch (Throwable e) {
        execution.completeOrRetry(null, e);
      }

      return null;
    };
  }

  static <T> Callable<T> callableOf(CheckedRunnable runnable) {
    Assert.notNull(runnable, "runnable");
    return () -> {
      runnable.run();
      return null;
    };
  }

  static <T> Callable<T> callableOf(ContextualCallable<T> callable, ExecutionContext context) {
    Assert.notNull(callable, "callable");
    return () -> callable.call(context);
  }

  static <T> Callable<T> callableOf(ContextualRunnable runnable, ExecutionContext context) {
    Assert.notNull(runnable, "runnable");
    return () -> {
      runnable.run(context);
      return null;
    };
  }

  static <T, U, R> CheckedBiFunction<T, U, R> fnOf(Callable<R> callable) {
    return (t, u) -> callable.call();
  }

  static <T, U, R> CheckedBiFunction<T, U, R> fnOf(CheckedBiConsumer<T, U> consumer) {
    return (t, u) -> {
      consumer.accept(t, u);
      return null;
    };
  }

  static <T, U, R> CheckedBiFunction<T, U, R> fnOf(CheckedConsumer<U> consumer) {
    return (t, u) -> {
      consumer.accept(u);
      return null;
    };
  }

  static <T, U, R> CheckedBiFunction<T, U, R> fnOf(CheckedFunction<U, R> function) {
    return (t, u) -> function.apply(u);
  }

  static <T, U, R> CheckedBiFunction<T, U, R> fnOf(CheckedRunnable runnable) {
    return (t, u) -> {
      runnable.run();
      return null;
    };
  }

  static <T, U, R> CheckedBiFunction<T, U, R> fnOf(R result) {
    return (t, u) -> result;
  }
}
