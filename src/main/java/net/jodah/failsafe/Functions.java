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
import net.jodah.failsafe.util.concurrent.Scheduler;

import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Utilities for creating functions.
 *
 * @author Jonathan Halterman
 */
final class Functions {
  /** Returns a supplier that supplies the {@code result} once then uses the {@code supplier} for subsequent calls. */
  static <T> Supplier<CompletableFuture<T>> supplyOnce(CompletableFuture<T> result,
      Supplier<CompletableFuture<T>> supplier) {
    return new Supplier<CompletableFuture<T>>() {
      volatile boolean called;

      @Override
      public CompletableFuture<T> get() {
        if (!called) {
          called = true;
          return result;
        } else
          return supplier.get();
      }
    };
  }

  /**
   * Returns a Supplier that supplies a promise that is completed with the result of calling the {@code supplier} on the
   * {@code scheduler}.
   */
  @SuppressWarnings("unchecked")
  static Supplier<CompletableFuture<ExecutionResult>> makeAsync(Supplier<CompletableFuture<ExecutionResult>> supplier,
      Scheduler scheduler, FailsafeFuture<Object> future) {
    return () -> {
      CompletableFuture<ExecutionResult> promise = new CompletableFuture<>();
      Callable<Object> callable = () -> supplier.get().handle((result, error) -> {
        // Propagate result
        if (result != null)
          promise.complete(result);
        else
          promise.completeExceptionally(error);
        return result;
      });

      try {
        future.inject((Future) scheduler.schedule(callable, 0, TimeUnit.NANOSECONDS));
      } catch (Exception e) {
        promise.completeExceptionally(e);
      }
      return promise;
    };
  }

  /**
   * Returns a Supplier that supplies a promose that is completed exceptionally with AsyncExecution.SCHEDULED by calling
   * the {@code supplier} on the {code scheduler}.
   */
  @SuppressWarnings("unchecked")
  static <T> Supplier<CompletableFuture<ExecutionResult>> makeAsyncExecution(Supplier<T> supplier, Scheduler scheduler,
      FailsafeFuture<Object> future) {
    return () -> {
      CompletableFuture<ExecutionResult> promise = new CompletableFuture<>();
      try {
        future.inject((Future) scheduler.schedule(supplier::get, 0, TimeUnit.NANOSECONDS));
        promise.completeExceptionally(AsyncExecution.SCHEDULED);
      } catch (Exception e) {
        promise.completeExceptionally(e);
      }
      return promise;
    };
  }

  static <T> Supplier<CompletableFuture<ExecutionResult>> promiseOf(CheckedSupplier<T> supplier,
      AbstractExecution execution) {
    Assert.notNull(supplier, "supplier");
    return () -> {
      ExecutionResult result;
      try {
        execution.preExecute();
        result = ExecutionResult.success(supplier.get());
      } catch (Throwable e) {
        result = ExecutionResult.failure(e);
      }
      execution.record(result);
      return CompletableFuture.completedFuture(result);
    };
  }

  static Supplier<CompletableFuture<ExecutionResult>> promiseOf(CheckedRunnable runnable, AbstractExecution execution) {
    Assert.notNull(runnable, "runnable");
    return () -> {
      ExecutionResult result;
      try {
        execution.preExecute();
        runnable.run();
        result = ExecutionResult.NONE;
      } catch (Throwable e) {
        result = ExecutionResult.failure(e);
      }
      execution.record(result);
      return CompletableFuture.completedFuture(result);
    };
  }

  static <T> Supplier<CompletableFuture<ExecutionResult>> promiseOf(ContextualSupplier<T> supplier,
      AbstractExecution execution) {
    Assert.notNull(supplier, "supplier");
    return () -> {
      ExecutionResult result;
      try {
        execution.preExecute();
        result = ExecutionResult.success(supplier.get(execution));
      } catch (Throwable e) {
        result = ExecutionResult.failure(e);
      }
      execution.record(result);
      return CompletableFuture.completedFuture(result);
    };
  }

  static Supplier<CompletableFuture<ExecutionResult>> promiseOf(ContextualRunnable runnable, AbstractExecution execution) {
    Assert.notNull(runnable, "runnable");
    return () -> {
      ExecutionResult result;
      try {
        execution.preExecute();
        runnable.run(execution);
        result = ExecutionResult.NONE;
      } catch (Throwable e) {
        result = ExecutionResult.failure(e);
      }
      execution.record(result);
      return CompletableFuture.completedFuture(result);
    };
  }

  static <T> Supplier<T> asyncOfExecution(AsyncSupplier<T> supplier, AsyncExecution execution) {
    Assert.notNull(supplier, "supplier");
    return new Supplier<T>() {
      @Override
      public synchronized T get() {
        try {
          execution.preExecute();
          return supplier.get(execution);
        } catch (Throwable e) {
          execution.completeOrHandle(null, e);
          return null;
        }
      }
    };
  }

  static <T> Supplier<T> asyncOfExecution(AsyncRunnable runnable, AsyncExecution execution) {
    Assert.notNull(runnable, "runnable");
    return new Supplier<T>() {
      @Override
      public synchronized T get() {
        try {
          execution.preExecute();
          runnable.run(execution);
        } catch (Throwable e) {
          execution.completeOrHandle(null, e);
        }
        return null;
      }
    };
  }

  static <T> Supplier<CompletableFuture<ExecutionResult>> promiseOfStage(
      CheckedSupplier<? extends CompletionStage<? extends T>> supplier, AbstractExecution execution) {
    Assert.notNull(supplier, "supplier");
    return () -> {
      CompletableFuture<ExecutionResult> promise = new CompletableFuture<>();
      try {
        execution.preExecute();
        supplier.get().whenComplete((innerResult, failure) -> {
          // Unwrap CompletionException cause
          ExecutionResult result = failure instanceof CompletionException ?
              ExecutionResult.failure(failure.getCause()) :
              ExecutionResult.success(innerResult);
          execution.record(result);
          promise.complete(result);
        });
      } catch (Throwable e) {
        ExecutionResult result = ExecutionResult.failure(e);
        execution.record(result);
        promise.complete(result);
      }
      return promise;
    };
  }

  static <T> Supplier<CompletableFuture<ExecutionResult>> promiseOfStage(
      ContextualSupplier<? extends CompletionStage<? extends T>> supplier, AbstractExecution execution) {
    Assert.notNull(supplier, "supplier");
    return () -> {
      CompletableFuture<ExecutionResult> promise = new CompletableFuture<>();
      try {
        execution.preExecute();
        supplier.get(execution).whenComplete((innerResult, failure) -> {
          // Unwrap CompletionException cause
          ExecutionResult result = failure instanceof CompletionException ?
              ExecutionResult.failure(failure.getCause()) :
              ExecutionResult.success(innerResult);
          execution.record(result);
          promise.complete(result);
        });
      } catch (Throwable e) {
        ExecutionResult result = ExecutionResult.failure(e);
        execution.record(result);
        promise.complete(result);
      }
      return promise;
    };
  }

  static <T> Supplier<CompletableFuture<T>> asyncOfFutureExecution(
      AsyncSupplier<? extends CompletionStage<? extends T>> supplier, AsyncExecution execution) {
    Assert.notNull(supplier, "supplier");
    return new Supplier<CompletableFuture<T>>() {
      Semaphore asyncFutureLock = new Semaphore(1);

      @Override
      public CompletableFuture<T> get() {
        try {
          execution.preExecute();
          asyncFutureLock.acquire();
          supplier.get(execution).whenComplete((innerResult, failure) -> {
            try {
              if (failure != null)
                execution.completeOrHandle(innerResult, failure instanceof CompletionException ? failure.getCause() : failure);
            } finally {
              asyncFutureLock.release();
            }
          });
        } catch (Throwable e) {
          try {
            execution.completeOrHandle(null, e);
          } finally {
            asyncFutureLock.release();
          }
        }

        return null;
      }
    };
  }

  static <T> Supplier<ExecutionResult> resultSupplierOf(CheckedSupplier<T> supplier, AbstractExecution execution) {
    return () -> {
      ExecutionResult result = null;
      try {
        result = ExecutionResult.success(supplier.get());
      } catch (Throwable t) {
        result = ExecutionResult.failure(t);
      } finally {
        execution.record(result);
      }
      return result;
    };
  }

  static <T> CheckedSupplier<T> supplierOf(CheckedRunnable runnable) {
    Assert.notNull(runnable, "runnable");
    return () -> {
      runnable.run();
      return null;
    };
  }

  static <T> CheckedSupplier<T> supplierOf(ContextualSupplier<T> supplier, ExecutionContext context) {
    Assert.notNull(supplier, "supplier");
    return () -> supplier.get(context);
  }

  static <T> CheckedSupplier<T> supplierOf(ContextualRunnable runnable, ExecutionContext context) {
    Assert.notNull(runnable, "runnable");
    return () -> {
      runnable.run(context);
      return null;
    };
  }

  static <T, U, R> CheckedBiFunction<T, U, R> fnOf(CheckedSupplier<R> supplier) {
    return (t, u) -> supplier.get();
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
