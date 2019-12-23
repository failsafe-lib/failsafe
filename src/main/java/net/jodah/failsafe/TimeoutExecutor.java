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
package net.jodah.failsafe;

import net.jodah.failsafe.util.concurrent.Scheduler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * A PolicyExecutor that handles failures according to a {@link Timeout}.
 */
class TimeoutExecutor extends PolicyExecutor<Timeout> {
  TimeoutExecutor(Timeout timeout, AbstractExecution execution) {
    super(timeout, execution);
  }

  @Override
  protected boolean isFailure(ExecutionResult result) {
    // Handle sync and async execution timeouts
    boolean timeoutExceeded =
      execution.isAsyncExecution() && execution.getElapsedAttemptTime().compareTo(policy.getTimeout()) >= 0;
    return timeoutExceeded || (!result.isNonResult() && result.getFailure() instanceof TimeoutExceededException);
  }

  @Override
  protected ExecutionResult onFailure(ExecutionResult result) {
    // Handle async execution timeouts
    if (!(result.getFailure() instanceof TimeoutExceededException))
      result = ExecutionResult.failure(new TimeoutExceededException(policy));
    return result.withComplete();
  }

  /**
   * Schedules a separate timeout call that fails with {@link TimeoutExceededException} if the policy's timeout is
   * exceeded.
   */
  @Override
  @SuppressWarnings("unchecked")
  protected Supplier<ExecutionResult> supply(Supplier<ExecutionResult> supplier, Scheduler scheduler) {
    return () -> {
      // Coordinates a result between the timeout and execution threads
      AtomicReference<ExecutionResult> result = new AtomicReference<>();
      Future<Object> timeoutFuture;
      Thread executionThread = Thread.currentThread();

      try {
        // Schedule timeout check
        timeoutFuture = (Future) scheduler.schedule(() -> {
          if (result.getAndUpdate(v -> v != null ? v : ExecutionResult.failure(new TimeoutExceededException(policy)))
            == null) {
            if (policy.canCancel()) {
              // Cancel and interrupt
              execution.cancelled = true;
              if (policy.canInterrupt()) {
                // Guard against race with the execution completing
                synchronized (execution) {
                  if (execution.canInterrupt) {
                    execution.record(result.get());
                    execution.interrupted = true;
                    executionThread.interrupt();
                  }
                }
              }
            }
          }
          return null;
        }, policy.getTimeout().toNanos(), TimeUnit.NANOSECONDS);
      } catch (Throwable t) {
        // Hard scheduling failure
        return postExecute(ExecutionResult.failure(t));
      }

      // Propagate execution, cancel timeout future if not done, and handle result
      if (result.compareAndSet(null, supplier.get()))
        timeoutFuture.cancel(false);
      return postExecute(result.get());
    };
  }

  /**
   * Schedules a separate timeout call that blocks and fails with {@link TimeoutExceededException} if the policy's
   * timeout is exceeded.
   */
  @Override
  @SuppressWarnings("unchecked")
  protected Supplier<CompletableFuture<ExecutionResult>> supplyAsync(
    Supplier<CompletableFuture<ExecutionResult>> supplier, Scheduler scheduler, FailsafeFuture<Object> future) {
    return () -> {
      // Coordinates a result between the timeout and execution threads
      CompletableFuture<ExecutionResult> promise = new CompletableFuture<>();
      AtomicReference<Future<Object>> timeoutFuture = new AtomicReference<>();

      // Schedule timeout if not an async execution
      if (!execution.isAsyncExecution()) {
        try {
          // Guard against race with future.complete or future.cancel
          synchronized (future) {
            if (!future.isDone()) {
              // Schedule timeout check
              timeoutFuture.set((Future) scheduler.schedule(() -> {
                if (!promise.isDone() && promise.complete(ExecutionResult.failure(new TimeoutExceededException(policy)))
                  && policy.canCancel()) {
                  boolean canInterrupt = policy.canInterrupt();
                  if (canInterrupt)
                    execution.record(promise.get());

                  // Cancel and interrupt
                  future.cancelDelegates(canInterrupt, false);
                }
                return null;
              }, policy.getTimeout().toNanos(), TimeUnit.NANOSECONDS));
              future.injectTimeout(timeoutFuture.get());
            }
          }
        } catch (Throwable t) {
          // Hard scheduling failure
          promise.completeExceptionally(t);
          return promise;
        }
      }

      // Propagate execution, cancel timeout future if not done, and handle result
      supplier.get().whenComplete((result, error) -> {
        if (error != null)
          promise.completeExceptionally(error);
        else {
          if (!promise.isDone()) {
            promise.complete(result);
            Future<Object> maybeFuture = timeoutFuture.get();
            if (maybeFuture != null)
              maybeFuture.cancel(false);
          } else {
            try {
              result = promise.get();
            } catch (Exception notPossible) {
            }
          }

          postExecuteAsync(result, scheduler, future);
        }
      });

      return promise;
    };
  }
}
