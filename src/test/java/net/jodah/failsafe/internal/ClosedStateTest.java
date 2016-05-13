package net.jodah.failsafe.internal;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

import net.jodah.failsafe.CircuitBreaker;

@Test
public class ClosedStateTest {
  public void testSuccessWithDefaultConfig() {
    // Given
    CircuitBreaker breaker = new CircuitBreaker();
    breaker.close();
    ClosedState state = new ClosedState(breaker);
    assertTrue(breaker.isClosed());

    // When
    state.recordSuccess();

    // Then
    assertTrue(breaker.isClosed());
  }

  public void testFailureWithDefaultConfig() {
    // Given
    CircuitBreaker breaker = new CircuitBreaker();
    breaker.close();
    ClosedState state = new ClosedState(breaker);
    assertFalse(breaker.isOpen());

    // When
    state.recordFailure();

    // Then
    assertTrue(breaker.isOpen());
  }

  public void testSuccessWithFailureThreshold() {
    // Given
    CircuitBreaker breaker = new CircuitBreaker().withFailureThreshold(3);
    breaker.close();
    ClosedState state = new ClosedState(breaker);
    assertTrue(breaker.isClosed());

    // When
    state.recordSuccess();
    state.recordSuccess();
    state.recordSuccess();

    // Then
    assertTrue(breaker.isClosed());
  }

  public void testFailureWithFailureThreshold() {
    // Given
    CircuitBreaker breaker = new CircuitBreaker().withFailureThreshold(3);
    breaker.close();
    ClosedState state = new ClosedState(breaker);

    // When
    state.recordFailure();
    state.recordSuccess();
    state.recordFailure();
    state.recordFailure();
    assertTrue(breaker.isClosed());
    state.recordFailure();

    // Then
    assertTrue(breaker.isOpen());
  }

  /**
   * Asserts that after numerous execution outcomes, when the last 3 out of 4 executions eventually are failures, the
   * circuit opens.
   */
  public void testSuccessWithFailureThresholdRatio() {
    // Given
    CircuitBreaker breaker = new CircuitBreaker().withFailureThreshold(3, 4);
    breaker.close();
    ClosedState state = new ClosedState(breaker);
    assertTrue(breaker.isClosed());

    // When / Then
    for (int i = 0; i < 20; i++) {
      state.recordSuccess();
      assertTrue(breaker.isClosed());
    }
  }

  public void testFailureWithFailureThresholdRatio() {
    // Given
    CircuitBreaker breaker = new CircuitBreaker().withFailureThreshold(3, 4);
    breaker.close();
    ClosedState state = new ClosedState(breaker);

    // When
    for (int i = 0; i < 10; i++) {
      state.recordSuccess();
      state.recordFailure();
      assertTrue(breaker.isClosed());
      state.recordFailure();
      assertTrue(breaker.isClosed());
      state.recordSuccess();
      assertTrue(breaker.isClosed());
    }

    state.recordFailure();

    // Then
    assertTrue(breaker.isOpen());
  }
}
