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
package dev.failsafe.functional;

import dev.failsafe.CircuitBreaker;
import dev.failsafe.Execution;
import dev.failsafe.RetryPolicy;
import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests the handling of ordered policy execution via an Execution or AsyncExecution.
 */
@Test
public class PolicyCompositionExecutionTest {
  public void testRetryPolicyCircuitBreaker() {
    RetryPolicy<Object> rp = RetryPolicy.ofDefaults();
    CircuitBreaker<Object> cb = CircuitBreaker.builder().withFailureThreshold(5).build();

    Execution<Object> execution = Execution.of(rp, cb);
    execution.recordException(new Exception());
    execution.recordException(new Exception());
    assertFalse(execution.isComplete());
    execution.recordException(new Exception());
    assertTrue(execution.isComplete());

    assertTrue(cb.isClosed());
  }

  public void testCircuitBreakerRetryPolicy() {
    RetryPolicy<Object> rp = RetryPolicy.builder().withMaxRetries(1).build();
    CircuitBreaker<Object> cb = CircuitBreaker.builder().withFailureThreshold(5).build();

    Execution<Object> execution = Execution.of(cb, rp);
    execution.recordException(new Exception());
    assertFalse(execution.isComplete());
    execution.recordException(new Exception());
    assertTrue(execution.isComplete());

    assertTrue(cb.isClosed());
  }
}
