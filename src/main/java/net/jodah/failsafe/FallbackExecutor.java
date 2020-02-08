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

import java.util.concurrent.*;

/**
 * A PolicyExecutor that handles failures according to a {@link Fallback}.
 */
class FallbackExecutor extends PolicyExecutor<Fallback> {
  FallbackExecutor(Fallback fallback, AbstractExecution execution) {
    super(fallback, execution);
  }

  @Override
  @SuppressWarnings("unchecked")
  protected ExecutionResult onFailure(ExecutionResult result) {
    try {
      return policy == Fallback.VOID ?
        result.withNonResult() :
        result.withResult(policy.apply(result.getResult(), result.getFailure(), execution.copy()));
    } catch (Exception e) {
      return ExecutionResult.failure(e);
    }
  }

  @SuppressWarnings("unchecked")
  protected CompletableFuture<ExecutionResult> onFailureAsync(ExecutionResult result, Scheduler scheduler,
    FailsafeFuture<Object> future) {
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
      } catch (Exception e) {
        promise.complete(ExecutionResult.failure(e));
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

    return promise;
  }
}
