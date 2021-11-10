package net.jodah.failsafe.functional;

import net.jodah.failsafe.*;
import net.jodah.failsafe.testing.Testing;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.function.BiConsumer;

import static org.testng.Assert.*;

/**
 * Tests nested timeout scenarios.
 */
@Test
public class NestedTimeoutTest extends Testing {
  /**
   * Timeout -> RetryPolicy -> Timeout
   * <p>
   * Tests a scenario where an inner timeout is exceeded, triggering retries, then eventually the outer timeout is
   * exceeded.
   */
  public void testTimeoutRetryPolicyTimeout() {
    Stats innerTimeoutStats = new Stats();
    Stats retryStats = new Stats();
    Stats outerTimeoutStats = new Stats();
    RetryPolicy<Object> retryPolicy = withStatsAndLogs(RetryPolicy.builder().withMaxRetries(10), retryStats).build();

    BiConsumer<Timeout<Object>, Timeout<Object>> test = (innerTimeout, outerTimeout) -> testRunFailure(false, () -> {
      innerTimeoutStats.reset();
      retryStats.reset();
      outerTimeoutStats.reset();
    }, Failsafe.with(outerTimeout, retryPolicy, innerTimeout), ctx -> {
      Thread.sleep(150);
    }, (f, e) -> {
      assertTrue(e.getAttemptCount() >= 3);
      assertTrue(e.getExecutionCount() >= 3);
      assertTrue(innerTimeoutStats.failureCount >= 3);
      assertTrue(retryStats.failedAttemptCount >= 3);
      // assertEquals(innerTimeoutStats.failureCount + 1, retryStats.failedAttemptCount);
      // assertEquals(innerTimeoutStats.executionCount, retryStats.executionCount);
      assertEquals(outerTimeoutStats.failureCount, 1);
    }, TimeoutExceededException.class);

    // Test without interrupt
    Timeout<Object> innerTimeout = withStatsAndLogs(Timeout.builder(Duration.ofMillis(100)), innerTimeoutStats).build();
    Timeout<Object> outerTimeout = withStatsAndLogs(Timeout.builder(Duration.ofMillis(500)), outerTimeoutStats).build();
    test.accept(innerTimeout, outerTimeout);

    // Test with interrupt
    innerTimeout = withStatsAndLogs(Timeout.builder(Duration.ofMillis(100)).withInterrupt(), innerTimeoutStats).build();
    outerTimeout = withStatsAndLogs(Timeout.builder(Duration.ofMillis(500)).withInterrupt(), outerTimeoutStats).build();
    test.accept(innerTimeout, outerTimeout);
  }

  /**
   * Fallback -> RetryPolicy -> Timeout -> Timeout
   * <p>
   * Tests a scenario with a fallback, retry policy and two timeouts, where the outer timeout triggers first.
   */
  public void testFallbackRetryPolicyTimeoutTimeout() {
    Stats innerTimeoutStats = new Stats();
    Stats outerTimeoutStats = new Stats();
    RetryPolicy<Object> retryPolicy = RetryPolicy.ofDefaults();
    Fallback<Object> fallback = Fallback.of(true);

    BiConsumer<Timeout<Object>, Timeout<Object>> test = (innerTimeout, outerTimeout) -> testRunSuccess(false, () -> {
      innerTimeoutStats.reset();
      outerTimeoutStats.reset();
    }, Failsafe.with(fallback, retryPolicy, outerTimeout, innerTimeout), ctx -> {
      Thread.sleep(150);
    }, (f, e) -> {
      assertEquals(3, e.getAttemptCount());
      assertEquals(innerTimeoutStats.failureCount, 3);
      assertEquals(outerTimeoutStats.failureCount, 3);
    }, true);

    // Test without interrupt
    Timeout<Object> innerTimeout = withStatsAndLogs(Timeout.builder(Duration.ofMillis(100)), innerTimeoutStats).build();
    Timeout<Object> outerTimeout = withStatsAndLogs(Timeout.builder(Duration.ofMillis(50)), outerTimeoutStats).build();
    test.accept(innerTimeout, outerTimeout);

    // Test with interrupt
    innerTimeout = withStatsAndLogs(Timeout.builder(Duration.ofMillis(100)).withInterrupt(), innerTimeoutStats).build();
    outerTimeout = withStatsAndLogs(Timeout.builder(Duration.ofMillis(50)).withInterrupt(), outerTimeoutStats).build();
    test.accept(innerTimeout, outerTimeout);
    test.accept(innerTimeout, outerTimeout);
  }

  /**
   * RetryPolicy -> Timeout -> Timeout
   * <p>
   * Tests a scenario where three timeouts should cause all delegates to be cancelled with interrupts.
   */
  public void shouldCancelNestedTimeoutsWithInterrupt() {
    // Given
    RetryPolicy<Boolean> rp = RetryPolicy.ofDefaults();
    Timeout<Boolean> outerTimeout = Timeout.of(Duration.ofMillis(1000));
    Timeout<Boolean> innerTimeout = Timeout.<Boolean>builder(Duration.ofMillis(200)).withInterrupt().build();

    // When / Then
    testGetFailure(false, Failsafe.with(rp, innerTimeout, outerTimeout), ctx -> {
      assertTrue(ctx.getLastFailure() == null || ctx.getLastFailure() instanceof TimeoutExceededException);

      try {
        assertFalse(ctx.isCancelled());
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        assertTrue(ctx.isCancelled());
        throw e;
      }
      fail("Expected interruption");
      return false;
    }, (f, e) -> {
      assertEquals(e.getAttemptCount(), 3);
    }, TimeoutExceededException.class);
  }
}
