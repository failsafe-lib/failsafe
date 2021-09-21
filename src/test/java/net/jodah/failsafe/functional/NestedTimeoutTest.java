package net.jodah.failsafe.functional;

import net.jodah.failsafe.*;
import org.testng.annotations.Test;

import java.time.Duration;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

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
    Timeout<Object> innerTimeout = withStatsAndLogs(Timeout.of(Duration.ofMillis(100)), innerTimeoutStats);
    RetryPolicy<Object> retryPolicy = withStatsAndLogs(new RetryPolicy<>().withMaxRetries(10), retryStats);
    Timeout<Object> outerTimeout = withStatsAndLogs(Timeout.of(Duration.ofMillis(500)), outerTimeoutStats);

    Runnable test = () -> testRunFailure(() -> {
      innerTimeoutStats.reset();
      retryStats.reset();
      outerTimeoutStats.reset();
    }, Failsafe.with(outerTimeout, retryPolicy, innerTimeout), ctx -> {
      Thread.sleep(150);
    }, e -> {
      assertTrue(e.getAttemptCount() >= 3);
      assertTrue(e.getExecutionCount() >= 3);
      assertTrue(innerTimeoutStats.failureCount >= 3);
      assertTrue(retryStats.failedAttemptCount >= 3);
      // assertEquals(innerTimeoutStats.failureCount + 1, retryStats.failedAttemptCount);
      // assertEquals(innerTimeoutStats.executionCount, retryStats.executionCount);
      assertEquals(outerTimeoutStats.failureCount, 1);
    }, TimeoutExceededException.class);

    // Test without interrupt
    test.run();

    // Test with interrupt
    innerTimeout.withInterrupt(true);
    outerTimeout.withInterrupt(true);
    test.run();
  }

  /**
   * Fallback -> RetryPolicy -> Timeout -> Timeout
   * <p>
   * Tests a scenario with a fallback, retry policy and two timeouts, where the outer timeout triggers first.
   */
  public void testFallbackRetryPolicyTimeoutTimeout() {
    Stats innerTimeoutStats = new Stats();
    Stats outerTimeoutStats = new Stats();
    Timeout<Object> innerTimeout = withStatsAndLogs(Timeout.of(Duration.ofMillis(100)), innerTimeoutStats);
    Timeout<Object> outerTimeout = withStatsAndLogs(Timeout.of(Duration.ofMillis(50)), outerTimeoutStats);
    RetryPolicy<Object> retryPolicy = new RetryPolicy<>().withMaxRetries(2);
    Fallback<Object> fallback = Fallback.of(true);

    Runnable test = () -> testRunSuccess(() -> {
      innerTimeoutStats.reset();
      outerTimeoutStats.reset();
    }, Failsafe.with(fallback, retryPolicy, outerTimeout, innerTimeout), ctx -> {
      Thread.sleep(150);
    }, e -> {
      assertEquals(3, e.getAttemptCount());
      assertEquals(innerTimeoutStats.failureCount, 3);
      assertEquals(outerTimeoutStats.failureCount, 3);
    }, true);

    // Test without interrupt
    test.run();

    // Test with interrupt
    outerTimeout.withInterrupt(true);
    innerTimeout.withInterrupt(true);
    test.run();
  }
}
