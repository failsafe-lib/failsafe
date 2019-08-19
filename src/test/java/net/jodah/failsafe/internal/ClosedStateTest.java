/*
 * Copyright 2016 the original author or authors.
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
import net.jodah.failsafe.Testing;
import org.testng.annotations.Test;

import static net.jodah.failsafe.Testing.getInternals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

@Test
public class ClosedStateTest {
  /**
   * Asserts that the the circuit is opened after a single failure.
   */
  public void testFailureWithDefaultConfig() {
    // Given
    CircuitBreaker breaker = new CircuitBreaker();
    breaker.close();
    ClosedState state = new ClosedState(breaker, getInternals(breaker));
    assertFalse(breaker.isOpen());

    // When
    state.recordFailure(null);

    // Then
    assertTrue(breaker.isOpen());
  }

  /**
   * Asserts that the the circuit is opened after the failure ratio is met.
   */
  public void testFailureWithFailureRatio() {
    // Given
    CircuitBreaker breaker = new CircuitBreaker().withFailureThreshold(2, 3);
    breaker.close();
    ClosedState state = new ClosedState(breaker, getInternals(breaker));

    // When
    state.recordFailure(null);
    state.recordSuccess();
    assertTrue(breaker.isClosed());
    state.recordFailure(null);

    // Then
    assertTrue(breaker.isOpen());
  }

  /**
   * Asserts that the the circuit is opened after the failure threshold is met.
   */
  public void testFailureWithFailureThreshold() {
    // Given
    CircuitBreaker breaker = new CircuitBreaker().withFailureThreshold(3);
    breaker.close();
    ClosedState state = new ClosedState(breaker, getInternals(breaker));

    // When
    state.recordFailure(null);
    state.recordSuccess();
    state.recordFailure(null);
    state.recordFailure(null);
    assertTrue(breaker.isClosed());
    state.recordFailure(null);

    // Then
    assertTrue(breaker.isOpen());
  }

  /**
   * Asserts that the the circuit is still closed after a single success.
   */
  public void testSuccessWithDefaultConfig() {
    // Given
    CircuitBreaker breaker = new CircuitBreaker();
    breaker.close();
    ClosedState state = new ClosedState(breaker, getInternals(breaker));
    assertTrue(breaker.isClosed());

    // When
    state.recordSuccess();

    // Then
    assertTrue(breaker.isClosed());
  }

  /**
   * Asserts that the the circuit stays closed after the failure ratio fails to be met.
   */
  public void testSuccessWithFailureRatio() {
    // Given
    CircuitBreaker breaker = new CircuitBreaker().withFailureThreshold(3, 4);
    breaker.close();
    ClosedState state = new ClosedState(breaker, getInternals(breaker));
    assertTrue(breaker.isClosed());

    // When / Then
    for (int i = 0; i < 20; i++) {
      state.recordSuccess();
      state.recordFailure(null);
      assertTrue(breaker.isClosed());
    }
  }

  /**
   * Asserts that the the circuit stays closed after the failure ratio fails to be met.
   */
  public void testSuccessWithFailureThreshold() {
    // Given
    CircuitBreaker breaker = new CircuitBreaker().withFailureThreshold(2);
    breaker.close();
    ClosedState state = new ClosedState(breaker, getInternals(breaker));
    assertTrue(breaker.isClosed());

    // When / Then
    for (int i = 0; i < 20; i++) {
      state.recordSuccess();
      state.recordFailure(null);
      assertTrue(breaker.isClosed());
    }
  }

  /**
   * Asserts that the late configuration of a failure ratio is handled by resetting the state's internal tracking. Also
   * asserts that executions from prior configurations are carried over to a new configuration.
   */
  public void shouldHandleLateSetFailureRatio() {
    // Given
    CircuitBreaker breaker = new CircuitBreaker();
    ClosedState state = Testing.stateFor(breaker);

    // When
    state.recordSuccess();
    assertTrue(breaker.isClosed());
    breaker.withFailureThreshold(2);
    state.recordFailure(null);
    assertTrue(breaker.isClosed());
    state.recordFailure(null);

    // Then
    assertTrue(breaker.isOpen());

    // Given
    breaker = new CircuitBreaker();
    state = Testing.stateFor(breaker);

    // When
    state.recordSuccess();
    assertTrue(breaker.isClosed());
    breaker.withFailureThreshold(2, 3);
    state.recordFailure(null);
    assertTrue(breaker.isClosed());
    state.recordFailure(null);

    // Then
    assertTrue(breaker.isOpen());
  }
}
