package net.jodah.failsafe.functional;

import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.Fallback;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.testing.Testing;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * Tests various Fallback scenarios.
 */
@Test
public class FallbackTest extends Testing {
  /**
   * Tests a simple execution that does not fallback.
   */
  public void shouldNotFallback() {
    testGetSuccess(Failsafe.with(Fallback.of(true)), ctx -> {
      return false;
    }, (f, e) -> {
      assertEquals(e.getAttemptCount(), 1);
      assertEquals(e.getExecutionCount(), 1);
    }, false);
  }

  /**
   * Tests the handling of a fallback with no conditions.
   */
  public void testFallbackWithoutConditions() {
    // Given
    Fallback<Object> fallback = Fallback.of(true);

    // When / Then
    testRunSuccess(Failsafe.with(fallback), ctx -> {
      throw new IllegalArgumentException();
    }, true);

    // Given
    RetryPolicy<Object> retryPolicy = new RetryPolicy<>().withMaxRetries(2);

    // When / Then
    testRunSuccess(Failsafe.with(fallback, retryPolicy), ctx -> {
      throw new IllegalStateException();
    }, true);
  }

  /**
   * Tests the handling of a fallback with conditions.
   */
  public void testFallbackWithConditions() {
    // Given
    Fallback<Boolean> fallback = Fallback.of(true).handle(IllegalArgumentException.class);

    // When / Then
    testRunFailure(Failsafe.with(fallback), ctx -> {
      throw new IllegalStateException();
    }, IllegalStateException.class);

    testRunSuccess(Failsafe.with(fallback), ctx -> {
      throw new IllegalArgumentException();
    }, true);
  }

  /**
   * Tests Fallback.ofException.
   */
  public void shouldFallbackOfException() {
    // Given
    Fallback<Object> fallback = Fallback.ofException(e -> new IllegalStateException(e.getLastFailure()));

    // When / Then
    testRunFailure(Failsafe.with(fallback), ctx -> {
      throw new IllegalArgumentException();
    }, IllegalStateException.class);
  }
}
