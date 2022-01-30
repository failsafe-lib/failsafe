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

import dev.failsafe.RateLimitExceededException;
import dev.failsafe.RateLimiter;
import dev.failsafe.spi.ExecutionResult;
import dev.failsafe.spi.FailsafeFuture;
import dev.failsafe.spi.PolicyExecutor;
import dev.failsafe.spi.Scheduler;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * A PolicyExecutor that handles failures according to a {@link RateLimiter}.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public class RateLimiterExecutor<R> extends PolicyExecutor<R> {
  private final RateLimiterImpl<R> rateLimiter;
  private final Duration maxWaitTime;

  public RateLimiterExecutor(RateLimiterImpl<R> rateLimiter, int policyIndex) {
    super(rateLimiter, policyIndex);
    this.rateLimiter = rateLimiter;
    maxWaitTime = rateLimiter.getConfig().getMaxWaitTime();
  }

  @Override
  protected ExecutionResult<R> preExecute() {
    try {
      return rateLimiter.tryAcquirePermit(maxWaitTime) ?
        null :
        ExecutionResult.failure(new RateLimitExceededException(rateLimiter));
    } catch (InterruptedException e) {
      // Set interrupt flag
      Thread.currentThread().interrupt();
      return ExecutionResult.failure(e);
    }
  }

  @Override
  protected CompletableFuture<ExecutionResult<R>> preExecuteAsync(Scheduler scheduler, FailsafeFuture<R> future) {
    CompletableFuture<ExecutionResult<R>> promise = new CompletableFuture<>();
    long waitNanos = rateLimiter.acquirePermitWaitNanos(maxWaitTime);
    if (waitNanos == -1)
      promise.complete(ExecutionResult.failure(new RateLimitExceededException(rateLimiter)));
    else {
      try {
        Future<?> scheduledWait = scheduler.schedule(() -> {
          // Signal for execution and post-execution to proceed with a non-result
          return promise.complete(ExecutionResult.none());
        }, waitNanos, TimeUnit.NANOSECONDS);

        // Propagate outer cancellations to the RateLimiter future and its promise
        future.setCancelFn(this, (mayInterrupt, cancelResult) -> {
          scheduledWait.cancel(mayInterrupt);
          promise.complete(cancelResult);
        });
      } catch (Throwable t) {
        // Hard scheduling failure
        promise.completeExceptionally(t);
      }
    }

    return promise;
  }
}
