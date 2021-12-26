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
package dev.failsafe;

import dev.failsafe.function.*;
import dev.failsafe.internal.util.Assert;
import dev.failsafe.spi.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Utilities for creating and applying Failsafe executable functions.
 *
 * @author Jonathan Halterman
 */
final class Functions {
  /**
   * Returns a Supplier that for synchronous executions, that pre-executes the {@code execution}, applies the {@code
   * supplier}, records the result and returns the result. This implementation also handles Thread interrupts.
   *
   * @param <R> result type
   */
  static <R> Function<SyncExecutionInternal<R>, ExecutionResult<R>> get(ContextualSupplier<R, R> supplier,
    Executor executor) {

    return execution -> {
      ExecutionResult<R> result;
      Throwable throwable = null;
      try {
        execution.preExecute();
        result = ExecutionResult.success(withExecutor(supplier, executor).get(execution));
      } catch (Throwable t) {
        throwable = t;
        result = ExecutionResult.failure(t);
      }
      execution.record(result);

      // Guard against race with Timeout interruption
      synchronized (execution.getInitial()) {
        execution.setInterruptable(false);
        if (execution.isInterrupted()) {
          // Clear interrupt flag if interruption was performed by Failsafe
          Thread.interrupted();
          return execution.getResult();
        } else if (throwable instanceof InterruptedException)
          // Set interrupt flag if interruption was not performed by Failsafe
          Thread.currentThread().interrupt();
      }

      return result;
    };
  }

  /**
   * Returns a Function for asynchronous executions that pre-executes the {@code execution}, applies the {@code
   * supplier}, records the result and returns a promise containing the result.
   *
   * @param <R> result type
   */
  static <R> Function<AsyncExecutionInternal<R>, CompletableFuture<ExecutionResult<R>>> getPromise(
    ContextualSupplier<R, R> supplier, Executor executor) {

    Assert.notNull(supplier, "supplier");
    return execution -> {
      ExecutionResult<R> result;
      try {
        execution.preExecute();
        result = ExecutionResult.success(withExecutor(supplier, executor).get(execution));
      } catch (Throwable t) {
        result = ExecutionResult.failure(t);
      }
      execution.record(result);
      return CompletableFuture.completedFuture(result);
    };
  }

  /**
   * Returns a Function for asynchronous executions, that pre-executes the {@code execution}, runs the {@code runnable},
   * and attempts to complete the {@code execution} if a failure occurs. Locks to ensure the resulting supplier cannot
   * be applied multiple times concurrently.
   *
   * @param <R> result type
   */
  static <R> Function<AsyncExecutionInternal<R>, CompletableFuture<ExecutionResult<R>>> getPromiseExecution(
    AsyncRunnable<R> runnable, Executor executor) {

    Assert.notNull(runnable, "runnable");
    return new Function<AsyncExecutionInternal<R>, CompletableFuture<ExecutionResult<R>>>() {
      @Override
      public synchronized CompletableFuture<ExecutionResult<R>> apply(AsyncExecutionInternal<R> execution) {
        try {
          execution.preExecute();
          withExecutor(runnable, executor).run(execution);
        } catch (Throwable e) {
          execution.record(null, e);
        }

        // Result will be provided later via AsyncExecution.record
        return ExecutionResult.nullFuture();
      }
    };
  }

  /**
   * Returns a Function that for asynchronous executions, that pre-executes the {@code execution}, applies the {@code
   * supplier}, records the result and returns a promise containing the result.
   *
   * @param <R> result type
   * @throws UnsupportedOperationException when using
   */
  @SuppressWarnings("unchecked")
  static <R> Function<AsyncExecutionInternal<R>, CompletableFuture<ExecutionResult<R>>> getPromiseOfStage(
    ContextualSupplier<R, ? extends CompletionStage<? extends R>> supplier, FailsafeFuture<R> future,
    Executor executor) {

    Assert.notNull(supplier, "supplier");
    return execution -> {
      CompletableFuture<ExecutionResult<R>> promise = new CompletableFuture<>();
      try {
        execution.preExecute();
        CompletionStage<? extends R> stage = withExecutor(supplier, executor).get(execution);

        if (stage == null) {
          ExecutionResult<R> r = ExecutionResult.success(null);
          execution.record(r);
          promise.complete(r);
        } else {
          // Propagate outer cancellations to the stage
          if (stage instanceof Future)
            future.propagateCancellation((Future<R>) stage);

          stage.whenComplete((result, failure) -> {
            if (failure instanceof CompletionException)
              failure = failure.getCause();
            ExecutionResult<R> r = failure == null ? ExecutionResult.success(result) : ExecutionResult.failure(failure);
            execution.record(r);
            promise.complete(r);
          });
        }
      } catch (Throwable t) {
        ExecutionResult<R> result = ExecutionResult.failure(t);
        execution.record(result);
        promise.complete(result);
      }
      return promise;
    };
  }

  /**
   * Returns a Function that returns an execution result if one was previously recorded, else applies the {@code
   * innerFn}.
   *
   * @param <R> result type
   */
  static <R> Function<AsyncExecutionInternal<R>, CompletableFuture<ExecutionResult<R>>> toExecutionAware(
    Function<AsyncExecutionInternal<R>, CompletableFuture<ExecutionResult<R>>> innerFn) {
    return execution -> {
      ExecutionResult<R> result = execution.getResult();
      if (result == null) {
        return innerFn.apply(execution);
      } else {
        return CompletableFuture.completedFuture(result);
      }
    };
  }

  /**
   * Returns a Function that asynchronously applies the {@code innerFn} on the first call, synchronously on subsequent
   * calls, and returns a promise containing the result.
   *
   * @param <R> result type
   */
  static <R> Function<AsyncExecutionInternal<R>, CompletableFuture<ExecutionResult<R>>> toAsync(
    Function<AsyncExecutionInternal<R>, CompletableFuture<ExecutionResult<R>>> innerFn, Scheduler scheduler,
    FailsafeFuture<R> future) {

    AtomicBoolean scheduled = new AtomicBoolean();
    return execution -> {
      if (scheduled.get()) {
        return innerFn.apply(execution);
      } else {
        CompletableFuture<ExecutionResult<R>> promise = new CompletableFuture<>();
        Callable<Object> callable = () -> innerFn.apply(execution).whenComplete((result, error) -> {
          if (error != null)
            promise.completeExceptionally(error);
          else
            promise.complete(result);
        });

        try {
          scheduled.set(true);
          Future<?> scheduledFuture = scheduler.schedule(callable, 0, TimeUnit.NANOSECONDS);

          // Propagate outer cancellations to the scheduled innerFn and its promise
          future.setCancelFn(-1, (mayInterrupt, cancelResult) -> {
            scheduledFuture.cancel(mayInterrupt);

            // Cancel a pending promise if the execution attempt has not started
            if (!execution.isPreExecuted())
              promise.complete(cancelResult);
          });
        } catch (Throwable t) {
          promise.completeExceptionally(t);
        }
        return promise;
      }
    };
  }

  static ContextualSupplier<Void, Void> toCtxSupplier(CheckedRunnable runnable) {
    Assert.notNull(runnable, "runnable");
    return ctx -> {
      runnable.run();
      return null;
    };
  }

  static ContextualSupplier<Void, Void> toCtxSupplier(ContextualRunnable<Void> runnable) {
    Assert.notNull(runnable, "runnable");
    return ctx -> {
      runnable.run(ctx);
      return null;
    };
  }

  static <R, T> ContextualSupplier<R, T> toCtxSupplier(CheckedSupplier<T> supplier) {
    Assert.notNull(supplier, "supplier");
    return ctx -> supplier.get();
  }

  static <R, T> ContextualSupplier<R, T> withExecutor(ContextualSupplier<R, T> supplier, Executor executor) {
    return executor == null ? supplier : ctx -> {
      executor.execute(() -> {
        try {
          supplier.get(ctx);
        } catch (Throwable e) {
          handleExecutorThrowable(e);
        }
      });
      return null;
    };
  }

  static <R> AsyncRunnable<R> withExecutor(AsyncRunnable<R> runnable, Executor executor) {
    return executor == null ? runnable : exec -> {
      executor.execute(() -> {
        try {
          runnable.run(exec);
        } catch (Throwable e) {
          handleExecutorThrowable(e);
        }
      });
    };
  }

  private static void handleExecutorThrowable(Throwable e) {
    if (e instanceof RuntimeException)
      throw (RuntimeException) e;
    if (e instanceof Error)
      throw (Error) e;
    throw new FailsafeException(e);
  }

  static <T, R> CheckedFunction<T, R> toFn(CheckedConsumer<T> consumer) {
    return t -> {
      consumer.accept(t);
      return null;
    };
  }

  static <T, R> CheckedFunction<T, R> toFn(CheckedRunnable runnable) {
    return t -> {
      runnable.run();
      return null;
    };
  }

  static <T, R> CheckedFunction<T, R> toFn(CheckedSupplier<? extends R> supplier) {
    return t -> supplier.get();
  }

  static <T, R> CheckedFunction<T, R> toFn(R result) {
    return t -> result;
  }
}
