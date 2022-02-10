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
package dev.failsafe.internal;

import dev.failsafe.Timeout;
import dev.failsafe.TimeoutConfig;
import dev.failsafe.TimeoutExceededException;
import dev.failsafe.spi.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * A PolicyExecutor that handles failures according to a {@link Timeout}.
 * <p>
 * Timeouts are scheduled to occur in a separate thread. When exceeded, a {@link TimeoutExceededException} is recorded
 * as the execution result. If another result is recorded before the timeout is exceeded, any pending timeouts are
 * cancelled.
 *
 * @param <R> result type
 */
public class TimeoutExecutor<R> extends PolicyExecutor<R> {
  private final Timeout<R> policy;
  private final TimeoutConfig<R> config;

  public TimeoutExecutor(TimeoutImpl<R> timeout, int policyIndex) {
    super(timeout, policyIndex);
    policy = timeout;
    config = timeout.getConfig();
  }

  @Override
  public boolean isFailure(ExecutionResult<R> result) {
    return !result.isNonResult() && result.getException() instanceof TimeoutExceededException;
  }

  /**
   * Schedules a separate timeout call that fails with {@link TimeoutExceededException} if the policy's timeout is
   * exceeded.
   * <p>
   * This implementation sets up a race between a timeout being triggered and the execution completing. Whichever
   * completes first will be the result that's recorded.
   */
  @Override
  public Function<SyncExecutionInternal<R>, ExecutionResult<R>> apply(
    Function<SyncExecutionInternal<R>, ExecutionResult<R>> innerFn, Scheduler scheduler) {

    return execution -> {
      // Coordinates a result between the timeout and execution threads
      AtomicReference<ExecutionResult<R>> result = new AtomicReference<>();
      Future<?> timeoutFuture;
      Thread executionThread = Thread.currentThread();

      try {
        // Schedule timeout check
        timeoutFuture = Scheduler.DEFAULT.schedule(() -> {
          // Guard against race with execution completion
          ExecutionResult<R> cancelResult = ExecutionResult.exception(new TimeoutExceededException(policy));
          if (result.compareAndSet(null, cancelResult)) {
            // Cancel and interrupt
            execution.record(cancelResult);
            execution.cancel(this);
            if (config.canInterrupt()) {
              // Guard against race with the execution completing
              synchronized (execution.getInitial()) {
                if (execution.isInterruptable()) {
                  execution.setInterrupted(true);
                  executionThread.interrupt();
                }
              }
            }
          }
          return null;
        }, config.getTimeout().toNanos(), TimeUnit.NANOSECONDS);
      } catch (Throwable t) {
        // Hard scheduling failure
        return postExecute(execution, ExecutionResult.exception(t));
      }

      // Propagate execution, cancel timeout future if not done, and postExecute result
      if (result.compareAndSet(null, innerFn.apply(execution)))
        timeoutFuture.cancel(false);
      return postExecute(execution, result.get());
    };
  }

  /**
   * Schedules a separate timeout call that blocks and fails with {@link TimeoutExceededException} if the policy's
   * timeout is exceeded.
   * <p>
   * This implementation sets up a race between a timeout being triggered and the execution completing. Whichever
   * completes first will be the result that's recorded and used to complete the resulting promise.
   */
  @Override
  @SuppressWarnings("unchecked")
  public Function<AsyncExecutionInternal<R>, CompletableFuture<ExecutionResult<R>>> applyAsync(
    Function<AsyncExecutionInternal<R>, CompletableFuture<ExecutionResult<R>>> innerFn, Scheduler scheduler,
    FailsafeFuture<R> future) {

    return execution -> {
      // Coordinates a race between the timeout and execution threads
      AtomicReference<ExecutionResult<R>> resultRef = new AtomicReference<>();
      AtomicReference<Future<R>> timeoutFutureRef = new AtomicReference<>();
      CompletableFuture<ExecutionResult<R>> promise = new CompletableFuture<>();

      // Guard against race with AsyncExecution.record, AsyncExecution.complete, future.complete or future.cancel
      synchronized (future) {
        // Schedule timeout if we are not done and not recording a result
        if (!future.isDone() && !execution.isRecorded()) {
          try {
            Future<R> timeoutFuture = (Future<R>) Scheduler.DEFAULT.schedule(() -> {
              // Guard against race with execution completion
              ExecutionResult<R> cancelResult = ExecutionResult.exception(new TimeoutExceededException(policy));
              if (resultRef.compareAndSet(null, cancelResult)) {
                // Cancel and interrupt
                execution.record(cancelResult);
                execution.cancel(this);
                future.cancelDependencies(this, config.canInterrupt(), cancelResult);
              }

              return null;
            }, config.getTimeout().toNanos(), TimeUnit.NANOSECONDS);
            timeoutFutureRef.set(timeoutFuture);

            // Propagate outer cancellations to the Timeout future and its promise
            future.setCancelFn(this, (mayInterrupt, cancelResult) -> {
              timeoutFuture.cancel(mayInterrupt);
              resultRef.compareAndSet(null, cancelResult);
            });
          } catch (Throwable t) {
            // Hard scheduling failure
            promise.completeExceptionally(t);
            return promise;
          }
        }
      }

      // Propagate execution, cancel timeout future if not done, and postExecute result
      innerFn.apply(execution).whenComplete((result, error) -> {
        if (error != null) {
          promise.completeExceptionally(error);
          return;
        }

        // Fetch timeout result if any
        if (!resultRef.compareAndSet(null, result))
          result = resultRef.get();

        if (result != null) {
          // Cancel timeout task
          Future<R> timeoutFuture = timeoutFutureRef.get();
          if (timeoutFuture != null && !timeoutFuture.isDone())
            timeoutFuture.cancel(false);

          postExecuteAsync(execution, result, scheduler, future);
        }

        promise.complete(result);
      });

      return promise;
    };
  }
}
