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
  private static final CompletableFuture<ExecutionResult> NULL_FUTURE = CompletableFuture.completedFuture(null);

  interface SettableSupplier<T> extends Supplier<T> {
    void set(T value);
  }

  /** Returns a SettableSupplier that supplies the set value once then uses the {@code supplier} for subsequent calls. */
  static <T> SettableSupplier<CompletableFuture<T>> settableSupplierOf(Supplier<CompletableFuture<T>> supplier) {
    return new SettableSupplier<CompletableFuture<T>>() {
      volatile boolean called;
      volatile CompletableFuture<T> value;

      @Override
      public CompletableFuture<T> get() {
        if (!called && value != null) {
          called = true;
          return value;
        } else
          return supplier.get();
      }

      @Override
      public void set(CompletableFuture<T> value) {
        called = false;
        this.value = value;
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

  static Supplier<CompletableFuture<ExecutionResult>> promiseOf(ContextualRunnable runnable,
    AbstractExecution execution) {
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

  static <T> Supplier<CompletableFuture<ExecutionResult>> asyncOfExecution(AsyncSupplier<T> supplier,
    AsyncExecution execution) {
    Assert.notNull(supplier, "supplier");
    return new Supplier<CompletableFuture<ExecutionResult>>() {
      @Override
      public synchronized CompletableFuture<ExecutionResult> get() {
        try {
          execution.preExecute();
          supplier.get(execution);
        } catch (Throwable e) {
          execution.completeOrHandle(null, e);
        }
        return NULL_FUTURE;
      }
    };
  }

  static Supplier<CompletableFuture<ExecutionResult>> asyncOfExecution(AsyncRunnable runnable,
    AsyncExecution execution) {
    Assert.notNull(runnable, "runnable");
    return new Supplier<CompletableFuture<ExecutionResult>>() {
      @Override
      public synchronized CompletableFuture<ExecutionResult> get() {
        try {
          execution.preExecute();
          runnable.run(execution);
        } catch (Throwable e) {
          execution.completeOrHandle(null, e);
        }
        return NULL_FUTURE;
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
          ExecutionResult result;
          if (failure != null) {
            if (failure instanceof CompletionException) {
              failure = failure.getCause();
            }
            result = ExecutionResult.failure(failure);
          } else {
            result = ExecutionResult.success(innerResult);
          }
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
          ExecutionResult result;
          if (failure != null) {
            if (failure instanceof CompletionException) {
              failure = failure.getCause();
            }
            result = ExecutionResult.failure(failure);
          } else {
            result = ExecutionResult.success(innerResult);
          }
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

  static <T> Supplier<CompletableFuture<ExecutionResult>> asyncOfFutureExecution(
    AsyncSupplier<? extends CompletionStage<? extends T>> supplier, AsyncExecution execution) {
    Assert.notNull(supplier, "supplier");
    return new Supplier<CompletableFuture<ExecutionResult>>() {
      Semaphore asyncFutureLock = new Semaphore(1);

      @Override
      public CompletableFuture<ExecutionResult> get() {
        try {
          execution.preExecute();
          asyncFutureLock.acquire();
          supplier.get(execution).whenComplete((innerResult, failure) -> {
            try {
              if (failure != null)
                execution.completeOrHandle(innerResult,
                  failure instanceof CompletionException ? failure.getCause() : failure);
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

        return NULL_FUTURE;
      }
    };
  }

  static <T> Supplier<ExecutionResult> resultSupplierOf(CheckedSupplier<T> supplier, AbstractExecution execution) {
    return () -> {
      ExecutionResult result = null;
      try {
        result = ExecutionResult.success(supplier.get());
      } catch (Throwable t) {
        if (t instanceof InterruptedException)
          Thread.currentThread().interrupt();
        result = ExecutionResult.failure(t);
      } finally {
        if (result != null)
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

  static <T, R> CheckedFunction<T, R> fnOf(CheckedSupplier<R> supplier) {
    return t -> supplier.get();
  }

  static <T, R> CheckedFunction<T, R> fnOf(CheckedConsumer<T> consumer) {
    return t -> {
      consumer.accept(t);
      return null;
    };
  }

  static <T, R> CheckedFunction<T, R> fnOf(CheckedRunnable runnable) {
    return t -> {
      runnable.run();
      return null;
    };
  }

  static <T, R> CheckedFunction<T, R> fnOf(R result) {
    return t -> result;
  }
}
