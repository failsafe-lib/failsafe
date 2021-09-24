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

import net.jodah.failsafe.spi.ExecutionResult;
import net.jodah.failsafe.spi.FailurePolicyInternal;
import net.jodah.failsafe.spi.PolicyExecutor;
import net.jodah.failsafe.spi.PolicyHandlers;

/**
 * A PolicyExecutor that handles failures according to a {@link CircuitBreaker}.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
class CircuitBreakerExecutor<R> extends PolicyExecutor<R, CircuitBreaker<R>> {
  CircuitBreakerExecutor(CircuitBreaker<R> circuitBreaker, int policyIndex, FailurePolicyInternal<R> failurePolicy,
    PolicyHandlers<R> policyHandlers) {
    super(circuitBreaker, policyIndex, failurePolicy, policyHandlers);
  }

  @Override
  protected ExecutionResult<R> preExecute() {
    if (policy.allowsExecution()) {
      policy.preExecute();
      return null;
    }
    return ExecutionResult.failure(new CircuitBreakerOpenException(policy));
  }

  @Override
  public void onSuccess(ExecutionResult<R> result) {
    policy.recordSuccess();
  }

  @Override
  protected ExecutionResult<R> onFailure(ExecutionContext<R> context, ExecutionResult<R> result) {
    policy.recordExecutionFailure(context);
    return result;
  }
}
