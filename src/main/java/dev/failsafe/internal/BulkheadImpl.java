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
import dev.failsafe.internal.util.Durations;
import dev.failsafe.spi.PolicyExecutor;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * A Bulkhead implementation.
 *
 * @param <R> result type
 */
public class BulkheadImpl<R> implements Bulkhead<R> {
  private final BulkheadConfig<R> config;
  private final Semaphore semaphore;

  public BulkheadImpl(BulkheadConfig<R> config) {
    this.config = config;
    semaphore = new Semaphore(config.getMaxConcurrency(), config.isFair());
  }

  @Override
  public BulkheadConfig<R> getConfig() {
    return config;
  }

  @Override
  public void acquirePermit() throws InterruptedException {
    semaphore.acquire();
  }

  @Override
  public boolean tryAcquirePermit() {
    return semaphore.tryAcquire();
  }

  @Override
  public boolean tryAcquirePermit(Duration maxWaitTime) throws InterruptedException {
    return semaphore.tryAcquire(Durations.ofSafeNanos(maxWaitTime).toNanos(), TimeUnit.NANOSECONDS);
  }

  @Override
  public void releasePermit() {
    semaphore.release();
  }

  @Override
  public PolicyExecutor<R> toExecutor(int policyIndex) {
    return new BulkheadExecutor<>(this, policyIndex);
  }
}
