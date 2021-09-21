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
package net.jodah.failsafe.functional;

import net.jodah.failsafe.*;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Tests various policy composition scenarios.
 */
@Test
public class PolicyCompositionTest extends Testing {
  /**
   * Fallback -> RetryPolicy -> CircuitBreaker
   */
  public void testFallbackRetryPolicyCircuitBreaker() {
    RetryPolicy<Object> rp = new RetryPolicy<>().withMaxRetries(2);
    CircuitBreaker<Object> cb = new CircuitBreaker<>().withFailureThreshold(5);
    Fallback<Object> fb = Fallback.ofAsync(() -> "test");

    testRunSuccess(() -> {
      resetBreaker(cb);
    }, Failsafe.with(fb, rp, cb), ctx -> {
      throw new IllegalStateException();
    }, e -> {
      assertEquals(cb.getFailureCount(), 3);
      assertEquals(cb.getSuccessCount(), 0);
      assertTrue(cb.isClosed());
    }, "test");
  }

  /**
   * CircuitBreaker -> RetryPolicy
   */
  public void testCircuitBreakerRetryPolicy() {
    RetryPolicy<Object> rp = new RetryPolicy<>().withMaxRetries(2);
    CircuitBreaker<Object> cb = new CircuitBreaker<>().withFailureThreshold(5);

    testRunFailure(() -> {
      resetBreaker(cb);
    }, Failsafe.with(cb, rp), ctx -> {
      throw new IllegalStateException();
    }, e -> {
      assertEquals(e.getAttemptCount(), 3);
      assertEquals(cb.getFailureCount(), 1);
      assertEquals(cb.getSuccessCount(), 0);
      assertTrue(cb.isClosed());
    }, IllegalStateException.class);
  }

  /**
   * Fallback -> RetryPolicy
   */
  public void testFallbackRetryPolicy() {
    RetryPolicy<Object> rp = new RetryPolicy<>().withMaxRetries(2);
    Fallback<Object> fb = Fallback.of("test");

    testRunSuccess(Failsafe.with(fb, rp), ctx -> {
      throw new IllegalStateException();
    }, e -> {
      assertEquals(e.getAttemptCount(), 3);
    }, "test");
  }

  /**
   * RetryPolicy -> Fallback
   */
  public void testRetryPolicyFallback() {
    RetryPolicy<Object> rp = new RetryPolicy<>().withMaxRetries(2);
    Fallback<Object> fb = Fallback.of("test");

    testRunSuccess(Failsafe.with(rp, fb), ctx -> {
      throw new IllegalStateException();
    }, e -> {
      assertEquals(e.getAttemptCount(), 1);
    }, "test");
  }
}
