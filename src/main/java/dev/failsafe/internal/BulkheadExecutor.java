/*
 * Copyright 2021 the original author or authors.
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

import dev.failsafe.Bulkhead;
import dev.failsafe.BulkheadFullException;
import dev.failsafe.ExecutionContext;
import dev.failsafe.spi.ExecutionResult;
import dev.failsafe.spi.FailsafeFuture;
import dev.failsafe.spi.PolicyExecutor;
import dev.failsafe.spi.Scheduler;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * A PolicyExecutor that handles failures according to a {@link Bulkhead}.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public class BulkheadExecutor<R> extends PolicyExecutor<R> {
  private final BulkheadImpl<R> bulkhead;
  private final Duration maxWaitTime;

  public BulkheadExecutor(BulkheadImpl<R> bulkhead, int policyIndex) {
    super(bulkhead, policyIndex);
    this.bulkhead = bulkhead;
    maxWaitTime = bulkhead.getConfig().getMaxWaitTime();
  }

  @Override
  protected ExecutionResult<R> preExecute() {
    try {
      return bulkhead.tryAcquirePermit(maxWaitTime) ?
        null :
        ExecutionResult.exception(new BulkheadFullException(bulkhead));
    } catch (InterruptedException e) {
      // Set interrupt flag
      Thread.currentThread().interrupt();
      return ExecutionResult.exception(e);
    }
  }

  @Override
  protected CompletableFuture<ExecutionResult<R>> preExecuteAsync(Scheduler scheduler, FailsafeFuture<R> future) {
    CompletableFuture<ExecutionResult<R>> promise = new CompletableFuture<>();
    CompletableFuture<Void> acquireFuture = bulkhead.acquirePermitAsync();
    acquireFuture.whenComplete((result, error) -> {
      // Signal for execution to proceed
      promise.complete(ExecutionResult.none());
    });

    if (!promise.isDone()) {
      try {
        // Schedule bulkhead permit timeout
        Future<?> timeoutFuture = scheduler.schedule(() -> {
          promise.complete(ExecutionResult.exception(new BulkheadFullException(bulkhead)));
          acquireFuture.cancel(true);
          return null;
        }, maxWaitTime.toNanos(), TimeUnit.NANOSECONDS);

        // Propagate outer cancellations to the promise, bulkhead acquire future, and timeout future
        future.setCancelFn(this, (mayInterrupt, cancelResult) -> {
          promise.complete(cancelResult);
          acquireFuture.cancel(mayInterrupt);
          timeoutFuture.cancel(mayInterrupt);
        });
      } catch (Throwable t) {
        // Hard scheduling failure
        promise.completeExceptionally(t);
      }
    }

    return promise;
  }

  @Override
  public void onSuccess(ExecutionResult<R> result) {
    bulkhead.releasePermit();
  }

  @Override
  protected ExecutionResult<R> onFailure(ExecutionContext<R> context, ExecutionResult<R> result) {
    bulkhead.releasePermit();
    return result;
  }
}
