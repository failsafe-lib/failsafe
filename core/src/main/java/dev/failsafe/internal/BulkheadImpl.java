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
import dev.failsafe.BulkheadConfig;
import dev.failsafe.internal.util.FutureLinkedList;
import dev.failsafe.spi.PolicyExecutor;

import java.time.Duration;
import java.util.concurrent.*;

/**
 * A Bulkhead implementation that supports sync and async waiting.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public class BulkheadImpl<R> implements Bulkhead<R> {
  private static final CompletableFuture<Void> NULL_FUTURE = CompletableFuture.completedFuture(null);
  private final BulkheadConfig<R> config;
  private final int maxPermits;

  // Mutable state
  private int permits;
  private final FutureLinkedList futures = new FutureLinkedList();

  public BulkheadImpl(BulkheadConfig<R> config) {
    this.config = config;
    maxPermits = config.getMaxConcurrency();
    permits = maxPermits;
  }

  @Override
  public BulkheadConfig<R> getConfig() {
    return config;
  }

  @Override
  public void acquirePermit() throws InterruptedException {
    try {
      acquirePermitAsync().get();
    } catch (CancellationException | ExecutionException ignore) {
      // Not possible since the future will always be completed with null
    }
  }

  @Override
  public synchronized boolean tryAcquirePermit() {
    if (permits > 0) {
      permits -= 1;
      return true;
    }
    return false;
  }

  @Override
  public boolean tryAcquirePermit(Duration maxWaitTime) throws InterruptedException {
    CompletableFuture<Void> future = acquirePermitAsync();
    if (future == NULL_FUTURE)
      return true;

    try {
      future.get(maxWaitTime.toNanos(), TimeUnit.NANOSECONDS);
      return true;
    } catch (CancellationException | ExecutionException | TimeoutException e) {
      return false;
    }
  }

  /**
   * Returns a CompletableFuture that is completed when a permit is acquired. Externally completing this future will
   * remove the waiter from the bulkhead's internal queue.
   */
  synchronized CompletableFuture<Void> acquirePermitAsync() {
    if (permits > 0) {
      permits -= 1;
      return NULL_FUTURE;
    } else {
      return futures.add();
    }
  }

  @Override
  public synchronized void releasePermit() {
    if (permits < maxPermits) {
      permits += 1;
      CompletableFuture<Void> future = futures.pollFirst();
      if (future != null)
        future.complete(null);
    }
  }

  @Override
  public PolicyExecutor<R> toExecutor(int policyIndex) {
    return new BulkheadExecutor<>(this, policyIndex);
  }
}
