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
import java.util.function.Supplier;

/**
 * A PolicyExecutor that handles failures according to a {@link Fallback}.
 */
class FallbackExecutor extends PolicyExecutor<Fallback> {
  private final EventListener failedAttemptListener;

  FallbackExecutor(Fallback fallback, AbstractExecution execution,   EventListener failedAttemptListener) {
    super(fallback, execution);
    this.failedAttemptListener = failedAttemptListener;
  }

  /**
   * Performs an execution by calling pre-execute else calling the supplier, applying a fallback if it fails, and
   * calling post-execute.
   */
  @Override
  @SuppressWarnings("unchecked")
  protected Supplier<ExecutionResult> supply(Supplier<ExecutionResult> supplier, Scheduler scheduler) {
    return () -> {
      ExecutionResult result = supplier.get();
      if (isFailure(result)) {
        try {
          result = policy == Fallback.VOID ?
            result.withNonResult() :
            result.withResult(policy.apply(result.getResult(), result.getFailure(), execution.copy()));
        } catch (Throwable t) {
          result = ExecutionResult.failure(t);
        }
      }

      return postExecute(result);
    };
  }

  /**
   * Performs an async execution by calling pre-execute else calling the supplier and doing a post-execute.
   */
  @Override
  @SuppressWarnings("unchecked")
  protected Supplier<CompletableFuture<ExecutionResult>> supplyAsync(
    Supplier<CompletableFuture<ExecutionResult>> supplier, Scheduler scheduler, FailsafeFuture<Object> future) {
    return () -> supplier.get().thenCompose(result -> {
      if (isFailure(result)) {
        CompletableFuture<ExecutionResult> promise = new CompletableFuture<>();
        Callable<Object> callable = () -> {
          try {
            CompletableFuture<Object> fallback = policy.applyStage(result.getResult(), result.getFailure(),
              execution.copy());
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
          else
            future.inject((Future) scheduler.schedule(callable, result.getWaitNanos(), TimeUnit.NANOSECONDS));
        } catch (Throwable t) {
          promise.completeExceptionally(t);
        }

        return promise.thenCompose(ss -> postExecuteAsync(ss, scheduler, future));
      }

      return postExecuteAsync(result, scheduler, future);
    });
  }

  @Override
  protected ExecutionResult onFailure(ExecutionResult result) {
    if (failedAttemptListener != null)
      failedAttemptListener.handle(result, execution);
    return result;
  }
}
