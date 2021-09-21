package net.jodah.failsafe.functional;

import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.Fallback;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.Testing;
import net.jodah.failsafe.function.ContextualRunnable;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

/**
 * Tests nested retry policy scenarios.
 */
@Test
public class NestedRetryPolicyTest extends Testing {
  Service service;

  @BeforeMethod
  protected void beforeMethod() {
    service = mock(Service.class);
  }

  /**
   * RetryPolicy -> RetryPolicy
   * <p>
   * Tests a scenario with nested retry policies where the inner policy is exceeded and skipped.
   */
  public void testNestedRetryPoliciesWhereInnerIsExceeded() {
    Stats outerRetryStats = new Stats();
    Stats innerRetryStats = new Stats();
    RetryPolicy<Object> outerRetryPolicy = withStats(new RetryPolicy<>().withMaxRetries(10), outerRetryStats);
    RetryPolicy<Object> innerRetryPolicy = withStats(new RetryPolicy<>().withMaxRetries(1), innerRetryStats);

    testGetSuccess(() -> {
      when(service.connect()).thenThrow(failures(5, new IllegalStateException())).thenReturn(true);
      outerRetryStats.reset();
      innerRetryStats.reset();
    }, Failsafe.with(outerRetryPolicy, innerRetryPolicy), ctx -> service.connect(), e -> {
      assertEquals(e.getAttemptCount(), 6);
      assertEquals(outerRetryStats.failedAttemptCount, 4);
      assertEquals(outerRetryStats.failureCount, 0);
      assertEquals(innerRetryStats.failedAttemptCount, 2);
      assertEquals(innerRetryStats.failureCount, 1);
    }, true);
  }

  /**
   * Fallback -> RetryPolicy -> RetryPolicy
   */
  public void testFallbackRetryPolicyRetryPolicy() {
    Stats retryPolicy1Stats = new Stats();
    Stats retryPolicy2Stats = new Stats();
    RetryPolicy<Object> retryPolicy1 = withStats(
      new RetryPolicy<>().handle(IllegalStateException.class).withMaxRetries(2), retryPolicy1Stats);
    RetryPolicy<Object> retryPolicy2 = withStats(
      new RetryPolicy<>().handle(IllegalArgumentException.class).withMaxRetries(3), retryPolicy2Stats);
    Fallback<Object> fallback = Fallback.ofAsync(() -> true);

    ContextualRunnable<Object> runnable = ctx -> {
      throw ctx.getAttemptCount() % 2 == 0 ? new IllegalStateException() : new IllegalArgumentException();
    };

    testRunSuccess(() -> {
      retryPolicy1Stats.reset();
      retryPolicy2Stats.reset();
    }, Failsafe.with(fallback, retryPolicy2, retryPolicy1), runnable, e -> {
      // Then
      // Expected RetryPolicy failure sequence:
      //    rp1 java.lang.IllegalStateException - failure, retry
      //    rp1 java.lang.IllegalArgumentException - success
      //    rp2 java.lang.IllegalArgumentException - failure, retry
      //    rp1 java.lang.IllegalStateException - failure, retry, retries exhausted
      //    rp1 java.lang.IllegalArgumentException - success
      //    rp2 java.lang.IllegalArgumentException - failure, retry
      //    rp1 java.lang.IllegalStateException - failure, retries exceeded
      //    rp2 java.lang.IllegalStateException - success
      assertEquals(retryPolicy1Stats.failedAttemptCount, 3);
      assertEquals(retryPolicy1Stats.failureCount, 1);
      assertEquals(retryPolicy2Stats.failedAttemptCount, 2);
      assertEquals(retryPolicy2Stats.failureCount, 0);
    }, true);

    testRunSuccess(() -> {
      retryPolicy1Stats.reset();
      retryPolicy2Stats.reset();
    }, Failsafe.with(fallback, retryPolicy1, retryPolicy2), runnable, e -> {
      // Expected RetryPolicy failure sequence:
      //    rp2 java.lang.IllegalStateException - success
      //    rp1 java.lang.IllegalStateException - failure, retry
      //    rp2 java.lang.IllegalArgumentException - failure, retry
      //    rp2 java.lang.IllegalStateException - success
      //    rp1 java.lang.IllegalStateException - failure, retry, retries exhausted
      //    rp2 java.lang.IllegalArgumentException - failure, retry
      //    rp2 java.lang.IllegalStateException - success
      //    rp1 java.lang.IllegalStateException - retries exceeded
      assertEquals(retryPolicy1Stats.failedAttemptCount, 3);
      assertEquals(retryPolicy1Stats.failureCount, 1);
      assertEquals(retryPolicy2Stats.failedAttemptCount, 2);
      assertEquals(retryPolicy2Stats.failureCount, 0);
    }, true);

  }
}
