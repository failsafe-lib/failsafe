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
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

@Test
public class HalfOpenStateTest {
  CircuitBreaker breaker;

  @BeforeMethod
  protected void beforeMethod() {
    breaker = new CircuitBreaker<>().onOpen(() -> System.out.println("Opening"));
    //      .onHalfOpen(() -> System.out.println("Half-opening"))
    //      .onClose(() -> System.out.println("Closing"))
    //      .onSuccess(e -> System.out.println("Success"))
    //      .onFailure(e -> System.out.println("Failure"));
  }

  /**
   * Asserts that the the circuit is opened after a single failure.
   */
  public void testFailureWithDefaultConfig() {
    // Given
    breaker.halfOpen();
    HalfOpenState state = Testing.stateFor(breaker);
    assertFalse(breaker.isOpen());
    assertFalse(breaker.isClosed());

    // When
    state.recordFailure(null);

    // Then
    assertTrue(breaker.isOpen());
  }

  /**
   * Asserts that the the circuit is opened after the failure threshold is met.
   */
  public void testFailureWithFailureThreshold() {
    // Given
    breaker.withFailureThreshold(3);
    breaker.halfOpen();
    HalfOpenState state = Testing.stateFor(breaker);

    // When
    for (int i = 0; i < 3; i++) {
      assertFalse(breaker.isOpen());
      assertFalse(breaker.isClosed());
      state.recordFailure(null);
    }

    // Then
    assertTrue(breaker.isOpen());
  }

  /**
   * Asserts that the the circuit is opened after the failure ratio is met.
   */
  public void testFailureWithFailureRatio() {
    // Given
    breaker.withFailureThreshold(2, 3);
    breaker.halfOpen();
    HalfOpenState state = Testing.stateFor(breaker);
    assertFalse(breaker.isOpen());

    // When
    state.recordFailure(null);
    state.recordSuccess();
    assertFalse(breaker.isOpen());
    assertFalse(breaker.isClosed());
    state.recordFailure(null);

    // Then
    assertTrue(breaker.isOpen());
  }

  /**
   * Asserts that the circuit is opened after a single failure. The failure threshold is ignored.
   */
  public void testFailureWithSuccessAndFailureThresholds() {
    // Given
    breaker.withSuccessThreshold(3).withFailureThreshold(2);
    breaker.halfOpen();
    HalfOpenState state = Testing.stateFor(breaker);

    // When
    state.recordSuccess();
    state.recordSuccess();
    assertFalse(breaker.isOpen());
    assertFalse(breaker.isClosed());
    state.recordFailure(null);

    // Then
    assertTrue(breaker.isOpen());
  }

  /**
   * Asserts that the circuit is opened after the success ratio fails to be met. The failure ratio is ignored.
   */
  public void testFailureWithSuccessAndFailureRatios() {
    // Given
    breaker.withFailureThreshold(3, 5).withSuccessThreshold(3, 4);
    breaker.halfOpen();
    HalfOpenState state = Testing.stateFor(breaker);

    // When
    state.recordSuccess();
    state.recordFailure(null);
    state.recordSuccess();
    assertFalse(breaker.isOpen());
    assertFalse(breaker.isClosed());
    state.recordFailure(null);

    // Then
    assertTrue(breaker.isOpen());
  }

  /**
   * Asserts that the circuit is opened after the success ratio fails to be met.
   */
  public void testFailureWithSuccessRatio() {
    // Given
    breaker.withSuccessThreshold(2, 3);
    breaker.halfOpen();
    HalfOpenState state = Testing.stateFor(breaker);
    assertFalse(breaker.isOpen());

    // When
    state.recordFailure(null);
    state.recordSuccess();
    assertFalse(breaker.isOpen());
    assertFalse(breaker.isClosed());
    state.recordFailure(null);

    // Then
    assertTrue(breaker.isOpen());
  }

  /**
   * Asserts that the circuit is opened after the success ratio fails to be met. The failure threshold is ignored.
   */
  public void testFailureWithSuccessRatioAndFailureThreshold() {
    // Given
    breaker.withSuccessThreshold(2, 4).withFailureThreshold(1);
    breaker.halfOpen();
    HalfOpenState state = Testing.stateFor(breaker);

    // When
    state.recordSuccess();
    state.recordFailure(null);
    state.recordFailure(null);
    assertFalse(breaker.isOpen());
    assertFalse(breaker.isClosed());
    state.recordFailure(null);

    // Then
    assertTrue(breaker.isOpen());
  }

  /**
   * Asserts that the circuit is opened after a single failure.
   */
  public void testFailureWithSuccessThreshold() {
    // Given
    breaker.withSuccessThreshold(3);
    breaker.halfOpen();
    HalfOpenState state = Testing.stateFor(breaker);
    assertFalse(breaker.isOpen());
    assertFalse(breaker.isClosed());

    // When
    state.recordSuccess();
    state.recordFailure(null);

    // Then
    assertTrue(breaker.isOpen());
  }

  /**
   * Asserts that the circuit is opened after a single failure.
   */
  public void testFailureWithSuccessThresholdAndFailureRatio() {
    // Given
    breaker.withFailureThreshold(3, 5).withSuccessThreshold(3);
    breaker.halfOpen();
    HalfOpenState state = Testing.stateFor(breaker);
    assertFalse(breaker.isOpen());
    assertFalse(breaker.isClosed());

    // When
    state.recordFailure(null);

    // Then
    assertTrue(breaker.isOpen());
  }

  /**
   * Asserts that the the circuit is closed after a single success.
   */
  public void testSuccessWithDefaultConfig() {
    // Given
    breaker.halfOpen();
    HalfOpenState state = Testing.stateFor(breaker);
    assertFalse(breaker.isOpen());
    assertFalse(breaker.isClosed());

    // When
    state.recordSuccess();

    // Then
    assertTrue(breaker.isClosed());
  }

  /**
   * Asserts that the the circuit is closed after the failure ratio fails to be met.
   */
  public void testSuccessWithFailureRatio() {
    // Given
    breaker.withFailureThreshold(2, 3);
    breaker.halfOpen();
    HalfOpenState state = Testing.stateFor(breaker);

    // When
    state.recordFailure(null);
    state.recordSuccess();
    assertFalse(breaker.isOpen());
    assertFalse(breaker.isClosed());
    state.recordSuccess();

    // Then
    assertTrue(breaker.isClosed());
  }

  /**
   * Asserts that the the circuit is closed after a single success.
   */
  public void testSuccessWithFailureThreshold() {
    // Given
    breaker.withFailureThreshold(3);
    breaker.halfOpen();
    HalfOpenState state = Testing.stateFor(breaker);
    assertFalse(breaker.isOpen());
    assertFalse(breaker.isClosed());

    // When
    state.recordFailure(null);
    state.recordSuccess();

    // Then
    assertTrue(breaker.isClosed());
  }

  /**
   * Asserts that the circuit is closed after the success ratio is met. The failure ratio is ignored.
   */
  public void testSuccessWithSuccessAndFailureRatios() {
    // Given
    breaker.withFailureThreshold(3, 5).withSuccessThreshold(3, 4);
    breaker.halfOpen();
    HalfOpenState state = Testing.stateFor(breaker);

    // When
    state.recordSuccess();
    state.recordFailure(null);
    state.recordSuccess();
    assertFalse(breaker.isOpen());
    assertFalse(breaker.isClosed());
    state.recordSuccess();

    // Then
    assertTrue(breaker.isClosed());
  }

  /**
   * Asserts that the circuit is closed after the success threshold is met. The failure threshold is ignored.
   */
  public void testSuccessWithSuccessAndFailureThresholds() {
    // Given
    breaker.withSuccessThreshold(3).withFailureThreshold(2);
    breaker.halfOpen();
    HalfOpenState state = Testing.stateFor(breaker);

    // When
    state.recordSuccess();
    state.recordSuccess();
    assertFalse(breaker.isOpen());
    assertFalse(breaker.isClosed());
    state.recordSuccess();

    // Then
    assertTrue(breaker.isClosed());
  }

  /**
   * Asserts that the circuit is closed after the success ratio is met.
   */
  public void testSuccessWithSuccessRatio() {
    // Given
    breaker.withSuccessThreshold(2, 3);
    breaker.halfOpen();
    HalfOpenState state = Testing.stateFor(breaker);

    // When
    state.recordFailure(null);
    state.recordSuccess();
    assertFalse(breaker.isOpen());
    assertFalse(breaker.isClosed());
    state.recordSuccess();

    // Then
    assertTrue(breaker.isClosed());
  }

  /**
   * Asserts that the circuit is closed after the success ratio is met. The failure threshold is ignored.
   */
  public void testSuccessWithSuccessRatioAndFailureThreshold() {
    // Given
    breaker.withSuccessThreshold(3, 4).withFailureThreshold(2);
    breaker.halfOpen();
    HalfOpenState state = Testing.stateFor(breaker);

    // When
    state.recordSuccess();
    state.recordSuccess();
    state.recordFailure(null);
    assertFalse(breaker.isOpen());
    assertFalse(breaker.isClosed());
    state.recordSuccess();

    // Then
    assertTrue(breaker.isClosed());
  }

  /**
   * Asserts that the circuit is closed after the success threshold is met.
   */
  public void testSuccessWithSuccessThreshold() {
    // Given
    breaker.withSuccessThreshold(3);
    breaker.halfOpen();
    HalfOpenState state = Testing.stateFor(breaker);

    // When
    for (int i = 0; i < 3; i++) {
      assertFalse(breaker.isOpen());
      assertFalse(breaker.isClosed());
      state.recordSuccess();
    }

    // Then
    assertTrue(breaker.isClosed());
  }

  /**
   * Asserts that the circuit is closed after the success threshold is met. The failure ratio is ignored.
   */
  public void testSuccessWithSuccessThresholdAndFailureRatio() {
    // Given
    breaker.withFailureThreshold(3, 5).withSuccessThreshold(2);
    breaker.halfOpen();
    HalfOpenState state = Testing.stateFor(breaker);

    // When success threshold exceeded
    state.recordSuccess();
    assertFalse(breaker.isOpen());
    assertFalse(breaker.isClosed());
    state.recordSuccess();

    // Then
    assertTrue(breaker.isClosed());
  }

  /**
   * Asserts that the late configuration of a failure ratio is handled by resetting the state's internal tracking. Also
   * asserts that executions from prior configurations are carried over to a new configuration.
   */
  public void shouldHandleLateSetFailureRatio() {
    // Given
    breaker.halfOpen();
    HalfOpenState state = Testing.stateFor(breaker);

    // When
    breaker.withFailureThreshold(2);
    state.recordFailure(null);
    assertTrue(breaker.isHalfOpen());
    state.recordFailure(null);

    // Then
    assertTrue(breaker.isOpen());

    // Given
    breaker = new CircuitBreaker().withFailureThreshold(3);
    breaker.halfOpen();
    state = Testing.stateFor(breaker);

    // When
    state.recordFailure(null);
    state.recordFailure(null);
    breaker.withFailureThreshold(3, 5);
    state.recordSuccess();
    state.recordSuccess();
    assertTrue(breaker.isHalfOpen());
    state.recordFailure(null);

    // Then
    assertTrue(breaker.isOpen());
  }

  /**
   * Asserts that the late configuration of a success ratio is handled by resetting the state's internal tracking. Also
   * asserts that executions from prior configurations are carried over to a new configuration.
   */
  public void shouldHandleLateSetSucessRatio() {
    // Given
    breaker.halfOpen();
    HalfOpenState state = Testing.stateFor(breaker);

    // When
    breaker.withSuccessThreshold(2);
    state.recordSuccess();
    assertTrue(breaker.isHalfOpen());
    state.recordSuccess();

    // Then
    assertTrue(breaker.isClosed());

    // Given
    breaker = new CircuitBreaker().withFailureThreshold(3);
    breaker.halfOpen();
    state = Testing.stateFor(breaker);

    // When
    state.recordFailure(null);
    state.recordFailure(null);
    breaker.withSuccessThreshold(2, 4);
    state.recordSuccess();
    assertTrue(breaker.isHalfOpen());
    state.recordSuccess();

    // Then
    assertTrue(breaker.isClosed());
  }
}
