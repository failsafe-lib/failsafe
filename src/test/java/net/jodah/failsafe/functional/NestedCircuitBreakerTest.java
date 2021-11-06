package net.jodah.failsafe.functional;

import net.jodah.failsafe.CircuitBreaker;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.testing.Testing;
import net.jodah.failsafe.function.CheckedRunnable;
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
