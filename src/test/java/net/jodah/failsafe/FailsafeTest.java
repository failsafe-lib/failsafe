package net.jodah.failsafe;

import net.jodah.failsafe.testing.Testing;
import org.testng.annotations.Test;

import java.util.concurrent.TimeoutException;

import static org.testng.Assert.assertEquals;

/**
 * Tests general Failsafe behaviors.
 */
@Test
public class FailsafeTest extends Testing {
  /**
   * Asserts that errors can be reported through Failsafe.
   */
  public void shouldSupportErrors() {
    // Given
    RetryPolicy<Boolean> retryPolicy = RetryPolicy.ofDefaults();

    // When / Then
    testRunFailure(Failsafe.with(retryPolicy), ctx -> {
      throw new InternalError();
    }, (f, e) -> {
      assertEquals(e.getAttemptCount(), 3);
    }, InternalError.class);
  }

  /**
   * Asserts that checked exeptions are wrapped with FailsafeException for sync executions.
   */
  public void shouldWrapCheckedExceptionsForSyncExecutions() {
    RetryPolicy<Object> retryPolicy = RetryPolicy.builder().withMaxRetries(0).build();

    assertThrows(() -> Failsafe.with(retryPolicy).run(() -> {
      throw new TimeoutException();
    }), FailsafeException.class, TimeoutException.class);
  }
}
