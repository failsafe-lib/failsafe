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
  static <R> Function<Execution<R>, ExecutionResult> get(ContextualSupplier<R, R> supplier) {
    return execution -> {
      ExecutionResult result;
      Throwable throwable = null;
      try {
        execution.preExecute();
        result = ExecutionResult.success(supplier.get(execution));
      } catch (Throwable t) {
        throwable = t;
        result = ExecutionResult.failure(t);
      }
      execution.record(result);

      // Guard against race with Timeout interruption
      synchronized (execution.interruptState) {
        execution.interruptState.canInterrupt = false;
        if (execution.interruptState.interrupted) {
          // Clear interrupt flag if interruption was intended
          Thread.interrupted();
          return execution.result;
        } else if (throwable instanceof InterruptedException)
          // Set interrupt flag if interrupt occurred but was not intended
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
  static <R> Function<AsyncExecution<R>, CompletableFuture<ExecutionResult>> getPromise(
    ContextualSupplier<R, R> supplier) {

    Assert.notNull(supplier, "supplier");
    return execution -> {
      ExecutionResult result;
      try {
        execution.preExecute();
        result = ExecutionResult.success(supplier.get(execution));
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
  static <R> Function<AsyncExecution<R>, CompletableFuture<ExecutionResult>> getPromiseExecution(
    AsyncRunnable<R> runnable) {

    Assert.notNull(runnable, "runnable");
    return new Function<AsyncExecution<R>, CompletableFuture<ExecutionResult>>() {
      @Override
      public synchronized CompletableFuture<ExecutionResult> apply(AsyncExecution<R> execution) {
        try {
          execution.preExecute();
          runnable.run(execution);
        } catch (Throwable e) {
          execution.record(null, e);
        }

        // Result will be provided later via AsyncExecution.record
        return ExecutionResult.NULL_FUTURE;
      }
    };
  }

  /**
   * Returns a Function that for asynchronous executions, that pre-executes the {@code execution}, applies the {@code
   * supplier}, records the result and returns a promise containing the result.
   *
   * @param <R> result type
   */
  @SuppressWarnings("unchecked")
  static <R> Function<AsyncExecution<R>, CompletableFuture<ExecutionResult>> getPromiseOfStage(
    ContextualSupplier<R, ? extends CompletionStage<? extends R>> supplier) {

    Assert.notNull(supplier, "supplier");
    return execution -> {
      CompletableFuture<ExecutionResult> promise = new CompletableFuture<>();
      try {
        execution.preExecute();
        CompletionStage<? extends R> stage = supplier.get(execution);

        // Propagate outer cancellations to the stage
        if (stage instanceof Future)
          execution.future.propagateCancellation((Future<R>) stage, -1);

        stage.whenComplete((result, failure) -> {
          if (failure instanceof CompletionException)
            failure = failure.getCause();
          ExecutionResult r = failure == null ? ExecutionResult.success(result) : ExecutionResult.failure(failure);
          execution.record(r);
          promise.complete(r);
        });
      } catch (Throwable t) {
        ExecutionResult result = ExecutionResult.failure(t);
        execution.record(result);
        promise.complete(result);
      }
      return promise;
    };
  }

  /**
   * Returns a Function for asynchronous executions, that pre-executes the {@code execution}, applies the {@code
   * supplier}, and attempts to complete the {@code execution} if a failure occurs. Locks to ensure the resulting
   * supplier cannot be applied multiple times concurrently.
   *
   * @param <R> result type
   */
  @SuppressWarnings("unchecked")
  static <R> Function<AsyncExecution<R>, CompletableFuture<ExecutionResult>> getPromiseOfStageExecution(
    AsyncSupplier<R, ? extends CompletionStage<? extends R>> supplier) {

    Assert.notNull(supplier, "supplier");
    Semaphore asyncFutureLock = new Semaphore(1);
    return execution -> {
      try {
        execution.preExecute();
        asyncFutureLock.acquire();
        CompletionStage<? extends R> stage = supplier.get(execution);

        // Propagate outer cancellations to the stage
        if (stage instanceof Future)
          execution.future.propagateCancellation((Future<R>) stage, -1);

        stage.whenComplete((innerResult, failure) -> {
          try {
            if (failure != null)
              execution.record(innerResult, failure instanceof CompletionException ? failure.getCause() : failure);
          } finally {
            asyncFutureLock.release();
          }
        });
      } catch (Throwable e) {
        try {
          execution.record(null, e);
        } finally {
          asyncFutureLock.release();
        }
      }

      // Result will be provided later via AsyncExecution.record
      return ExecutionResult.NULL_FUTURE;
    };
  }

  /**
   * Returns a Function that returns an execution result if one was previously recorded, else applies the {@code
   * innerFn}.
   *
   * @param <R> result type
   */
  static <R> Function<AsyncExecution<R>, CompletableFuture<ExecutionResult>> toExecutionAware(
    Function<AsyncExecution<R>, CompletableFuture<ExecutionResult>> innerFn) {
    return execution -> {
      if (execution.result == null) {
        return innerFn.apply(execution);
      } else {
        return CompletableFuture.completedFuture(execution.result);
      }
    };
  }

  /**
   * Returns a Function that asynchronously applies the {@code innerFn} on the first call, synchronously on subsequent
   * calls, and returns a promise containing the result.
   *
   * @param <R> result type
   */
  static <R> Function<AsyncExecution<R>, CompletableFuture<ExecutionResult>> toAsync(
    Function<AsyncExecution<R>, CompletableFuture<ExecutionResult>> innerFn, Scheduler scheduler) {

    AtomicBoolean scheduled = new AtomicBoolean();
    return execution -> {
      if (scheduled.get()) {
        // Propagate outer cancellations to the thread that the innerFn is running with
        execution.future.injectCancelFn(-1, (mayInterrupt, cancelResult) -> {
          if (execution.innerFuture != null)
            execution.innerFuture.cancel(mayInterrupt);
        });

        return innerFn.apply(execution);
      } else {
        CompletableFuture<ExecutionResult> promise = new CompletableFuture<>();
        Callable<Object> callable = () -> innerFn.apply(execution).whenComplete((result, error) -> {
          if (error != null)
            promise.completeExceptionally(error);
          else
            promise.complete(result);
        });

        try {
          scheduled.set(true);
          Future<?> future = scheduler.schedule(callable, 0, TimeUnit.NANOSECONDS);

          // Propagate outer cancellations to the scheduled innerFn and its promise
          execution.future.injectCancelFn(-1, (mayInterrupt, cancelResult) -> {
            future.cancel(mayInterrupt);

            // Cancel a pending promise if the execution attempt has not started
            if (!execution.attemptStarted)
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

  static <T, R> CheckedFunction<T, R> toFn(CheckedSupplier<R> supplier) {
    return t -> supplier.get();
  }

  static <T, R> CheckedFunction<T, R> toFn(R result) {
    return t -> result;
  }
}
