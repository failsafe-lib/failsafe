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
import dev.failsafe.RateLimitExceededException;
import dev.failsafe.spi.ExecutionResult;
import dev.failsafe.spi.PolicyExecutor;

import java.time.Duration;

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
        ExecutionResult.failure(new BulkheadFullException(bulkhead));
    } catch (InterruptedException e) {
      // Set interrupt flag
      Thread.currentThread().interrupt();
      return ExecutionResult.failure(e);
    }
  }

  @Override
  public boolean isFailure(ExecutionResult<R> result) {
    return !result.isNonResult() && result.getFailure() instanceof RateLimitExceededException;
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
