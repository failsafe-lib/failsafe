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

import net.jodah.failsafe.internal.CircuitBreakerInternals;

import java.time.Duration;

/**
 * A PolicyExecutor that handles failures according to a {@link CircuitBreaker}.
 *
 * @author Jonathan Halterman
 */
class CircuitBreakerExecutor extends PolicyExecutor<CircuitBreaker> {
  private final CircuitBreakerInternals breakerInternals;

  CircuitBreakerExecutor(CircuitBreaker circuitBreaker, CircuitBreakerInternals breakerInternals,
    AbstractExecution execution) {
    super(circuitBreaker, execution);
    this.breakerInternals = breakerInternals;
  }

  @Override
  protected ExecutionResult preExecute() {
    if (policy.allowsExecution()) {
      policy.preExecute();
      return null;
    }
    return ExecutionResult.failure(new CircuitBreakerOpenException(policy));
  }

  @Override
  protected boolean isFailure(ExecutionResult result) {
    long elapsedNanos = execution.getElapsedAttemptTime().toNanos();
    Duration timeout = policy.getTimeout();
    boolean timeoutExceeded = timeout != null && elapsedNanos >= timeout.toNanos();
    return timeoutExceeded || super.isFailure(result);
  }

  @Override
  protected void onSuccess(ExecutionResult result) {
    policy.recordSuccess();
  }

  @Override
  protected ExecutionResult onFailure(ExecutionResult result) {
    breakerInternals.recordFailure(execution);
    return result.withComplete();
  }
}
