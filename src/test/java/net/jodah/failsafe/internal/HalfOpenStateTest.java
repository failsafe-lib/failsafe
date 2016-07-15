package net.jodah.failsafe.internal;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

import net.jodah.failsafe.CircuitBreaker;
import net.jodah.failsafe.internal.HalfOpenState;

@Test
public class HalfOpenStateTest {
  /**
   * Asserts that the the circuit is opened after a single failure.
   */
  public void testFailureWithDefaultConfig() {
    // Given
    CircuitBreaker breaker = new CircuitBreaker();
    breaker.halfOpen();
    HalfOpenState state = new HalfOpenState(breaker);
    assertFalse(breaker.isOpen());
    assertFalse(breaker.isClosed());

    // When
    state.recordFailure();

    // Then
    assertTrue(breaker.isOpen());
  }

  /**
   * Asserts that the the circuit is opened after the failure ratio is met.
   */
  public void testFailureWithFailureRatio() {
    // Given
    CircuitBreaker breaker = new CircuitBreaker().withFailureThreshold(2, 3);
    breaker.halfOpen();
    HalfOpenState state = new HalfOpenState(breaker);
    assertFalse(breaker.isOpen());

    // When
    state.recordFailure();
    state.recordSuccess();
    assertFalse(breaker.isOpen());
    assertFalse(breaker.isClosed());
    state.recordFailure();

    // Then
    assertTrue(breaker.isOpen());
  }

  /**
   * Asserts that the the circuit is opened after the failure threshold is met.
   */
  public void testFailureWithFailureThreshold() {
    // Given
    CircuitBreaker breaker = new CircuitBreaker().withFailureThreshold(3);
    breaker.halfOpen();
    HalfOpenState state = new HalfOpenState(breaker);

    // When
    for (int i = 0; i < 3; i++) {
      assertFalse(breaker.isOpen());
      assertFalse(breaker.isClosed());
      state.recordFailure();
    }

    // Then
    assertTrue(breaker.isOpen());
  }

  /**
   * Asserts that the circuit is opened after the success ratio fails to be met. The failure ratio is ignored.
   */
  public void testFailureWithSuccessAndFailureRatios() {
    // Given
    CircuitBreaker breaker = new CircuitBreaker().withFailureThreshold(3, 5).withSuccessThreshold(3, 4);
    breaker.halfOpen();
    HalfOpenState state = new HalfOpenState(breaker);

    // When
    state.recordSuccess();
    state.recordFailure();
    state.recordSuccess();
    assertFalse(breaker.isOpen());
    assertFalse(breaker.isClosed());
    state.recordFailure();

    // Then
    assertTrue(breaker.isOpen());
  }

  /**
   * Asserts that the circuit is opened after a single failure. The failure threshold is ignored.
   */
  public void testFailureWithSuccessAndFailureThresholds() {
    // Given
    CircuitBreaker breaker = new CircuitBreaker().withSuccessThreshold(3).withFailureThreshold(2);
    breaker.halfOpen();
    HalfOpenState state = new HalfOpenState(breaker);

    // When
    state.recordSuccess();
    state.recordSuccess();
    assertFalse(breaker.isOpen());
    assertFalse(breaker.isClosed());
    state.recordFailure();

    // Then
    assertTrue(breaker.isOpen());
  }

  /**
   * Asserts that the circuit is opened after the success ratio fails to be met.
   */
  public void testFailureWithSuccessRatio() {
    // Given
    CircuitBreaker breaker = new CircuitBreaker().withSuccessThreshold(2, 3);
    breaker.halfOpen();
    HalfOpenState state = new HalfOpenState(breaker);
    assertFalse(breaker.isOpen());

    // When
    state.recordFailure();
    state.recordSuccess();
    assertFalse(breaker.isOpen());
    assertFalse(breaker.isClosed());
    state.recordFailure();

    // Then
    assertTrue(breaker.isOpen());
  }

  /**
   * Asserts that the circuit is opened after the success ratio fails to be met. The failure threshold is ignored.
   */
  public void testFailureWithSuccessRatioAndFailureThreshold() {
    // Given
    CircuitBreaker breaker = new CircuitBreaker().withSuccessThreshold(2, 4).withFailureThreshold(1);
    breaker.halfOpen();
    HalfOpenState state = new HalfOpenState(breaker);

    // When
    state.recordSuccess();
    state.recordFailure();
    state.recordFailure();
    assertFalse(breaker.isOpen());
    assertFalse(breaker.isClosed());
    state.recordFailure();

    // Then
    assertTrue(breaker.isOpen());
  }

  /**
   * Asserts that the circuit is opened after a single failure.
   */
  public void testFailureWithSuccessThreshold() {
    // Given
    CircuitBreaker breaker = new CircuitBreaker().withSuccessThreshold(3);
    breaker.halfOpen();
    HalfOpenState state = new HalfOpenState(breaker);
    assertFalse(breaker.isOpen());
    assertFalse(breaker.isClosed());

    // When
    state.recordFailure();

    // Then
    assertTrue(breaker.isOpen());
  }

  /**
   * Asserts that the circuit is opened after a single failure.
   */
  public void testFailureWithSuccessThresholdAndFailureRatio() {
    // Given
    CircuitBreaker breaker = new CircuitBreaker().withFailureThreshold(3, 5).withSuccessThreshold(3);
    breaker.halfOpen();
    HalfOpenState state = new HalfOpenState(breaker);
    assertFalse(breaker.isOpen());
    assertFalse(breaker.isClosed());

    // When
    state.recordFailure();

    // Then
    assertTrue(breaker.isOpen());
  }

  /**
   * Asserts that the the circuit is closed after a single success.
   */
  public void testSuccessWithDefaultConfig() {
    // Given
    CircuitBreaker breaker = new CircuitBreaker();
    breaker.halfOpen();
    HalfOpenState state = new HalfOpenState(breaker);
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
    CircuitBreaker breaker = new CircuitBreaker().withFailureThreshold(2, 3);
    breaker.halfOpen();
    HalfOpenState state = new HalfOpenState(breaker);

    // When
    state.recordFailure();
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
    CircuitBreaker breaker = new CircuitBreaker().withFailureThreshold(3);
    breaker.halfOpen();
    HalfOpenState state = new HalfOpenState(breaker);
    assertFalse(breaker.isOpen());
    assertFalse(breaker.isClosed());

    // When
    state.recordSuccess();

    // Then
    assertTrue(breaker.isClosed());
  }

  /**
   * Asserts that the circuit is closed after the success ratio is met. The failure ratio is ignored.
   */
  public void testSuccessWithSuccessAndFailureRatios() {
    // Given
    CircuitBreaker breaker = new CircuitBreaker().withFailureThreshold(3, 5).withSuccessThreshold(3, 4);
    breaker.halfOpen();
    HalfOpenState state = new HalfOpenState(breaker);

    // When
    state.recordSuccess();
    state.recordFailure();
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
    CircuitBreaker breaker = new CircuitBreaker().withSuccessThreshold(3).withFailureThreshold(2);
    breaker.halfOpen();
    HalfOpenState state = new HalfOpenState(breaker);

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
    CircuitBreaker breaker = new CircuitBreaker().withSuccessThreshold(2, 3);
    breaker.halfOpen();
    HalfOpenState state = new HalfOpenState(breaker);

    // When
    state.recordFailure();
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
    CircuitBreaker breaker = new CircuitBreaker().withSuccessThreshold(3, 4).withFailureThreshold(2);
    breaker.halfOpen();
    HalfOpenState state = new HalfOpenState(breaker);

    // When
    state.recordSuccess();
    state.recordSuccess();
    state.recordFailure();
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
    CircuitBreaker breaker = new CircuitBreaker().withSuccessThreshold(3);
    breaker.halfOpen();
    HalfOpenState state = new HalfOpenState(breaker);

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
    CircuitBreaker breaker = new CircuitBreaker().withFailureThreshold(3, 5).withSuccessThreshold(2);
    breaker.halfOpen();
    HalfOpenState state = new HalfOpenState(breaker);

    // When success threshold exceeded
    state.recordSuccess();
    assertFalse(breaker.isOpen());
    assertFalse(breaker.isClosed());
    state.recordSuccess();

    // Then
    assertTrue(breaker.isClosed());
  }
}
