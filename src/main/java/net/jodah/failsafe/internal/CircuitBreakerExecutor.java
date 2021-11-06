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
package net.jodah.failsafe.internal;

import net.jodah.failsafe.CircuitBreaker;
import net.jodah.failsafe.CircuitBreakerOpenException;
import net.jodah.failsafe.ExecutionContext;
import net.jodah.failsafe.spi.EventHandler;
import net.jodah.failsafe.spi.ExecutionResult;
import net.jodah.failsafe.spi.PolicyExecutor;

/**
 * A PolicyExecutor that handles failures according to a {@link CircuitBreaker}.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public class CircuitBreakerExecutor<R> extends PolicyExecutor<R> {
  private final CircuitBreakerImpl<R> circuitBreaker;

  public CircuitBreakerExecutor(CircuitBreakerImpl<R> circuitBreaker, int policyIndex, EventHandler<R> successHandler,
    EventHandler<R> failureHandler) {
    super(policyIndex, circuitBreaker, successHandler, failureHandler);
    this.circuitBreaker = circuitBreaker;
  }

  @Override
  protected ExecutionResult<R> preExecute() {
    if (circuitBreaker.allowsExecution()) {
      circuitBreaker.preExecute();
      return null;
    }
    return ExecutionResult.failure(new CircuitBreakerOpenException(circuitBreaker));
  }

  @Override
  public void onSuccess(ExecutionResult<R> result) {
    circuitBreaker.recordSuccess();
  }

  @Override
  protected ExecutionResult<R> onFailure(ExecutionContext<R> context, ExecutionResult<R> result) {
    circuitBreaker.recordExecutionFailure(context);
    return result;
  }
}
