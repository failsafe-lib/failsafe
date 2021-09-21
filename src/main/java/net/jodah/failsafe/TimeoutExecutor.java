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

import net.jodah.failsafe.internal.util.Assert;
import net.jodah.failsafe.util.concurrent.Scheduler;

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
class TimeoutExecutor<R> extends PolicyExecutor<R, Timeout<R>> {
  TimeoutExecutor(Timeout<R> timeout, int policyIndex) {
    super(timeout, policyIndex);
  }

  @Override
  protected boolean isFailure(ExecutionResult result) {
    return !result.isNonResult() && result.getFailure() instanceof TimeoutExceededException;
  }

  /**
   * Schedules a separate timeout call that fails with {@link TimeoutExceededException} if the policy's timeout is
   * exceeded.
   * <p>
   * This implementation sets up a race between a timeout being triggered and the execution completing. Whichever
   * completes first will be the result that's recorded.
   */
  @Override
  protected Function<Execution<R>, ExecutionResult> apply(Function<Execution<R>, ExecutionResult> innerFn,
    Scheduler scheduler) {
    return execution -> {
      // Coordinates a result between the timeout and execution threads
      AtomicReference<ExecutionResult> result = new AtomicReference<>();
      Future<?> timeoutFuture;
      Thread executionThread = Thread.currentThread();

      try {
        // Schedule timeout check
        timeoutFuture = Scheduler.DEFAULT.schedule(() -> {
          // Guard against race with execution completion
          ExecutionResult cancelResult = ExecutionResult.failure(new TimeoutExceededException(policy));
          if (result.compareAndSet(null, cancelResult)) {
            Assert.log(TimeoutExecutor.class, "Timeout %s triggered for exec=%s", policy.hashCode(),
              execution.hashCode());
            // Cancel and interrupt
            execution.record(cancelResult);
            execution.cancelledIndex = policyIndex;
            if (policy.canInterrupt()) {
              // Guard against race with the execution completing
              synchronized (execution.interruptState) {
                if (execution.interruptState.canInterrupt) {
                  execution.interruptState.interrupted = true;
                  executionThread.interrupt();
                }
              }
            }
          }
          return null;
        }, policy.getTimeout().toNanos(), TimeUnit.NANOSECONDS);
        Assert.log(TimeoutExecutor.class, "Scheduled duration %s for timeout %s, exec=%s, future=%s",
          policy.getTimeout(), policy.hashCode(), execution.hashCode(), timeoutFuture.hashCode());
      } catch (Throwable t) {
        // Hard scheduling failure
        return postExecute(execution, ExecutionResult.failure(t));
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
  protected Function<AsyncExecution<R>, CompletableFuture<ExecutionResult>> applyAsync(
    Function<AsyncExecution<R>, CompletableFuture<ExecutionResult>> innerFn, Scheduler scheduler,
    FailsafeFuture<R> future) {

    return execution -> {
      // Coordinates a race between the timeout and execution threads
      AtomicReference<ExecutionResult> resultRef = new AtomicReference<>();
      AtomicReference<Future<R>> timeoutFutureRef = new AtomicReference<>();
      CompletableFuture<ExecutionResult> promise = new CompletableFuture<>();

      // Guard against race with AsyncExecution.record, AsyncExecution.complete, future.complete or future.cancel
      synchronized (future) {
        // Schedule timeout if we are not done and not recording a result
        if (!future.isDone() && !execution.recordCalled) {
          try {
            Future<R> timeoutFuture = (Future<R>) Scheduler.DEFAULT.schedule(() -> {
              // Guard against race with execution completion
              ExecutionResult cancelResult = ExecutionResult.failure(new TimeoutExceededException(policy));
              if (resultRef.compareAndSet(null, cancelResult)) {
                // Cancel and interrupt
                Assert.log(TimeoutExecutor.class, "Timeout %s triggered for exec=%s", policy.hashCode(),
                  execution.hashCode());
                execution.record(cancelResult);
                execution.cancelledIndex = policyIndex;
                future.cancelDependencies(policyIndex, policy.canInterrupt(), cancelResult);
              }

              return null;
            }, policy.getTimeout().toNanos(), TimeUnit.NANOSECONDS);
            timeoutFutureRef.set(timeoutFuture);
            Assert.log(TimeoutExecutor.class, "Scheduled duration %s for timeout %s, exec=%s, future=%s",
              policy.getTimeout(), policy.hashCode(), execution.hashCode(), timeoutFuture.hashCode());

            // Propagate outer cancellations to the Timeout future
            future.injectCancelFn(policyIndex, (mayInterrupt, cancelResult) -> {
              Assert.log(TimeoutExecutor.class, "Cancelling timeout %s future=%s", policy.hashCode(),
                timeoutFuture.hashCode());
              resultRef.compareAndSet(null, cancelResult);
              timeoutFuture.cancel(mayInterrupt);
            });
            Assert.log(TimeoutExecutor.class, "Finished timeout scheduling function for timeout %s, exec=%s, future=%s",
              policy.hashCode(), execution.hashCode(), timeoutFuture.hashCode());
          } catch (Throwable t) {
            // Hard scheduling failure
            promise.completeExceptionally(t);
            return promise;
          }
        }
      }

      // Propagate execution, cancel timeout future if not done, and postExecute result
      CompletableFuture<ExecutionResult> innerFuture = innerFn.apply(execution);
      Assert.log(TimeoutExecutor.class, "Applying innerFn exec=%s, promise=%s, innerFuture=%s", execution.hashCode(),
        promise.hashCode(), innerFuture.hashCode());
      innerFuture.whenComplete((result, error) -> {
        Assert.log(TimeoutExecutor.class, "InnerFn completed exec=%s, innerFuture=%s", execution.hashCode(),
          innerFuture.hashCode());
        if (error != null) {
          promise.completeExceptionally(error);
          return;
        }

        // Fetch timeout result if any
        if (!resultRef.compareAndSet(null, result))
          result = resultRef.get();

        // Cancel timeout task
        Future<R> timeoutFuture = timeoutFutureRef.get();
        if (timeoutFuture != null && !timeoutFuture.isDone())
          timeoutFuture.cancel(false);

        if (result != null) {
          Assert.log(TimeoutExecutor.class,
            "Maybe post executing for timeout=%s, result=%s, exec=%s, promise=%s, recordCalled=%s", policy.hashCode(),
            result.toSummary(), execution.hashCode(), promise.hashCode(), execution.recordCalled);
          postExecuteAsync(execution, result, scheduler, future);
        }
        Assert.log(TimeoutExecutor.class, "Completing promise=%s", promise.hashCode());
        promise.complete(result);
      });

      return promise;
    };
  }
}
