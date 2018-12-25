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
package net.jodah.failsafe.internal.executor;

import net.jodah.failsafe.CircuitBreaker;
import net.jodah.failsafe.CircuitBreakerOpenException;
import net.jodah.failsafe.ExecutionResult;
import net.jodah.failsafe.PolicyExecutor;
import net.jodah.failsafe.util.Duration;

/**
 * A PolicyExecutor that handles failures according to a {@link CircuitBreaker}.
 *
 * @param <T> result type
 * @author Jonathan Halterman
 */
public class CircuitBreakerExecutor extends PolicyExecutor {
  private final CircuitBreaker circuitBreaker;

  public CircuitBreakerExecutor(CircuitBreaker circuitBreaker) {
    this.circuitBreaker = circuitBreaker;
  }

  @Override
  public ExecutionResult preExecute(ExecutionResult result) {
    boolean allowsExecution = circuitBreaker.allowsExecution();
    if (allowsExecution)
      circuitBreaker.preExecute();
    return allowsExecution ? result : new ExecutionResult(null, new CircuitBreakerOpenException(), true, false);
  }

  @Override
  public ExecutionResult postExecute(ExecutionResult result) {
    long elapsedNanos = execution.getElapsedTime().toNanos();
    Duration timeout = circuitBreaker.getTimeout();
    boolean timeoutExceeded = timeout != null && elapsedNanos >= timeout.toNanos();

    if (timeoutExceeded || (!result.noResult && circuitBreaker.isFailure(result.result, result.failure))) {
      circuitBreaker.recordFailure();
      return result.with(true, false);
    } else {
      circuitBreaker.recordSuccess();
      return result.with(true, true);
    }
  }
}
