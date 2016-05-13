package net.jodah.failsafe.internal;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

import net.jodah.failsafe.CircuitBreaker;
import net.jodah.failsafe.internal.HalfOpenState;

@Test
public class HalfOpenStateTest {
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

  public void testSuccessWithSuccessThresholdRatio() {
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

  public void testFailureWithSuccessThresholdRatio() {
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

  public void testSuccessWithFailureThresholdRatio() {
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

  public void testFailureWithFailureThresholdRatio() {
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

  public void testSuccessWithSuccessAndFailureThresholds() {
    // Given
    CircuitBreaker breaker = new CircuitBreaker().withSuccessThreshold(3).withFailureThreshold(2);
    breaker.halfOpen();
    HalfOpenState state = new HalfOpenState(breaker);

    // When
    state.recordFailure();
    state.recordSuccess();
    state.recordSuccess();
    assertFalse(breaker.isOpen());
    assertFalse(breaker.isClosed());
    state.recordSuccess();

    // Then
    assertTrue(breaker.isClosed());
  }

  public void testFailureWithSuccessAndFailureThresholds() {
    // Given
    CircuitBreaker breaker = new CircuitBreaker().withSuccessThreshold(3).withFailureThreshold(2);
    breaker.halfOpen();
    HalfOpenState state = new HalfOpenState(breaker);

    // When
    state.recordFailure();
    state.recordSuccess();
    state.recordSuccess();
    state.recordFailure();
    assertFalse(breaker.isOpen());
    assertFalse(breaker.isClosed());
    state.recordFailure();

    // Then
    assertTrue(breaker.isOpen());
  }

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

  public void testFailureWithSuccessRatioAndFailureThreshold() {
    // Given
    CircuitBreaker breaker = new CircuitBreaker().withSuccessThreshold(3, 4).withFailureThreshold(2);
    breaker.halfOpen();
    HalfOpenState state = new HalfOpenState(breaker);

    // When
    state.recordSuccess();
    state.recordFailure();
    assertFalse(breaker.isOpen());
    assertFalse(breaker.isClosed());
    state.recordFailure();

    // Then
    assertTrue(breaker.isOpen());

    // Given
    breaker = new CircuitBreaker().withSuccessThreshold(3, 4).withFailureThreshold(10);
    breaker.halfOpen();
    state = new HalfOpenState(breaker);

    // When
    state.recordFailure();
    state.recordFailure();
    state.recordFailure();
    assertFalse(breaker.isOpen());
    assertFalse(breaker.isClosed());
    state.recordFailure();

    // Then
    assertTrue(breaker.isOpen());
  }

  public void testSuccessWithFailureRatioAndSuccessThreshold() {
    // Given
    CircuitBreaker breaker = new CircuitBreaker().withFailureThreshold(3, 5).withSuccessThreshold(2);
    breaker.halfOpen();
    HalfOpenState state = new HalfOpenState(breaker);

    // When success threshold exceeded
    state.recordSuccess();
    state.recordFailure();
    state.recordSuccess();
    assertFalse(breaker.isOpen());
    assertFalse(breaker.isClosed());
    state.recordSuccess();

    // Then
    assertTrue(breaker.isClosed());

    // Given
    breaker = new CircuitBreaker().withFailureThreshold(3, 5).withSuccessThreshold(10);
    breaker.halfOpen();
    state = new HalfOpenState(breaker);

    // When failure threshold exceeded
    state.recordSuccess();
    state.recordSuccess();
    state.recordSuccess();
    state.recordSuccess();
    assertFalse(breaker.isOpen());
    assertFalse(breaker.isClosed());
    state.recordSuccess();

    // Then
    assertTrue(breaker.isClosed());
  }

  public void testFailureWithFailureRatioAndSuccessThreshold() {
    // Given
    CircuitBreaker breaker = new CircuitBreaker().withFailureThreshold(3, 5).withSuccessThreshold(3);
    breaker.halfOpen();
    HalfOpenState state = new HalfOpenState(breaker);

    // When
    state.recordSuccess();
    state.recordFailure();
    state.recordSuccess();
    state.recordFailure();
    assertFalse(breaker.isOpen());
    assertFalse(breaker.isClosed());
    state.recordFailure();

    // Then
    assertTrue(breaker.isOpen());
  }
}
