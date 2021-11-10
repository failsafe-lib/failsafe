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
package dev.failsafe.functional;

import dev.failsafe.function.CheckedRunnable;
import dev.failsafe.testing.Testing;
import dev.failsafe.CircuitBreaker;
import dev.failsafe.Failsafe;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Tests nested circuit breaker scenarios.
 */
@Test
public class NestedCircuitBreakerTest extends Testing {
  /**
   * Tests that multiple circuit breakers handle failures as expected, regardless of order.
   */
  public void testNestedCircuitBreakers() {
    CircuitBreaker<Object> innerCb = CircuitBreaker.builder().handle(IllegalArgumentException.class).build();
    CircuitBreaker<Object> outerCb = CircuitBreaker.builder().handle(IllegalStateException.class).build();

    CheckedRunnable runnable = () -> {
      throw new IllegalArgumentException();
    };
    ignoreExceptions(() -> Failsafe.with(outerCb, innerCb).run(runnable));
    assertTrue(innerCb.isOpen());
    assertTrue(outerCb.isClosed());

    innerCb.close();
    ignoreExceptions(() -> Failsafe.with(innerCb, outerCb).run(runnable));
    assertTrue(innerCb.isOpen());
    assertTrue(outerCb.isClosed());
  }

  /**
   * CircuitBreaker -> CircuitBreaker
   */
  public void testCircuitBreakerCircuitBreaker() {
    // Given
    CircuitBreaker<Object> cb1 = CircuitBreaker.builder().handle(IllegalStateException.class).build();
    CircuitBreaker<Object> cb2 = CircuitBreaker.builder().handle(IllegalArgumentException.class).build();

    testRunFailure(() -> {
      resetBreaker(cb1);
      resetBreaker(cb2);
    }, Failsafe.with(cb2, cb1), ctx -> {
      throw new IllegalStateException();
    }, (f, e) -> {
      assertEquals(1, e.getAttemptCount());
      assertEquals(cb1.getFailureCount(), 1);
      assertTrue(cb1.isOpen());
      assertEquals(cb2.getFailureCount(), 0);
      assertTrue(cb2.isClosed());
    }, IllegalStateException.class);

    testRunFailure(() -> {
      resetBreaker(cb1);
      resetBreaker(cb2);
    }, Failsafe.with(cb2, cb1), ctx -> {
      throw new IllegalStateException();
    }, (f, e) -> {
      assertEquals(1, e.getAttemptCount());
      assertEquals(cb1.getFailureCount(), 1);
      assertTrue(cb1.isOpen());
      assertEquals(cb2.getFailureCount(), 0);
      assertTrue(cb2.isClosed());
    }, IllegalStateException.class);
  }
}
