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

import net.jodah.failsafe.internal.EventListener;
import net.jodah.failsafe.util.concurrent.Scheduler;

import java.util.concurrent.*;
import java.util.function.Function;

/**
 * A PolicyExecutor that handles failures according to a {@link Fallback}.
 *
 * @param <R> result type
 */
class FallbackExecutor<R> extends PolicyExecutor<R, Fallback<R>> {
  private final EventListener failedAttemptListener;

  FallbackExecutor(Fallback<R> fallback, int policyIndex, EventListener failedAttemptListener) {
    super(fallback, policyIndex);
    this.failedAttemptListener = failedAttemptListener;
  }

  /**
   * Performs an execution by calling pre-execute else calling the supplier, applying a fallback if it fails, and
   * calling post-execute.
   */
  @Override
  @SuppressWarnings("unchecked")
  protected Function<Execution<R>, ExecutionResult> apply(Function<Execution<R>, ExecutionResult> innerFn,
    Scheduler scheduler) {
    return execution -> {
      ExecutionResult result = innerFn.apply(execution);
      if (execution.isCancelled(this))
        return result;

      if (isFailure(result)) {
        if (failedAttemptListener != null)
          failedAttemptListener.handle(result, execution);

        try {
          result = policy == Fallback.VOID ?
            result.withNonResult() :
            result.withResult(policy.apply((R) result.getResult(), result.getFailure(), execution));
        } catch (Throwable t) {
          result = ExecutionResult.failure(t);
        }
      }

      return postExecute(execution, result);
    };
  }

  /**
   * Performs an async execution by calling pre-execute else calling the supplier and doing a post-execute.
   */
  @Override
  @SuppressWarnings("unchecked")
  protected Function<AsyncExecution<R>, CompletableFuture<ExecutionResult>> applyAsync(
    Function<AsyncExecution<R>, CompletableFuture<ExecutionResult>> innerFn, Scheduler scheduler,
    FailsafeFuture<R> future) {

    return execution -> innerFn.apply(execution).thenCompose(result -> {
      if (result == null || future.isDone())
        return ExecutionResult.NULL_FUTURE;
      if (execution.isCancelled(this))
        return CompletableFuture.completedFuture(result);
      if (!isFailure(result))
        return postExecuteAsync(execution, result, scheduler, future);

      if (failedAttemptListener != null)
        failedAttemptListener.handle(result, execution);

      CompletableFuture<ExecutionResult> promise = new CompletableFuture<>();
      Callable<R> callable = () -> {
        try {
          CompletableFuture<R> fallback = policy.applyStage((R) result.getResult(), result.getFailure(), execution);
          fallback.whenComplete((innerResult, failure) -> {
            if (failure instanceof CompletionException)
              failure = failure.getCause();
            ExecutionResult r = failure == null ? result.withResult(innerResult) : ExecutionResult.failure(failure);
            promise.complete(r);
          });
        } catch (Throwable t) {
          promise.complete(ExecutionResult.failure(t));
        }
        return null;
      };

      try {
        if (!policy.isAsync())
          callable.call();
        else {
          Future<?> scheduledFallback = scheduler.schedule(callable, 0, TimeUnit.NANOSECONDS);

          // Propagate outer cancellations to the Fallback future and its promise
          future.injectCancelFn(policyIndex, (mayInterrupt, cancelResult) -> {
            scheduledFallback.cancel(mayInterrupt);
            promise.complete(cancelResult);
          });
        }
      } catch (Throwable t) {
        // Hard scheduling failure
        promise.completeExceptionally(t);
      }

      return promise.thenCompose(ss -> postExecuteAsync(execution, ss, scheduler, future));
    });
  }
}
