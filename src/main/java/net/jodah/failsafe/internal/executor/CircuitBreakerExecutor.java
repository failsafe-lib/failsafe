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

import java.time.Duration;

/**
 * A PolicyExecutor that handles failures according to a {@link CircuitBreaker}.
 *
 * @author Jonathan Halterman
 */
public class CircuitBreakerExecutor extends PolicyExecutor<CircuitBreaker> {
  public CircuitBreakerExecutor(CircuitBreaker circuitBreaker) {
    super(circuitBreaker);
  }

  @Override
  protected ExecutionResult preExecute() {
    if (policy.allowsExecution()) {
      policy.preExecute();
      return null;
    }
    return ExecutionResult.failure(new CircuitBreakerOpenException());
  }

  @Override
  protected boolean isFailure(ExecutionResult result) {
    long elapsedNanos = execution.getElapsedTime().toNanos();
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
    policy.recordFailure();
    return result.withCompleted();
  }
}
