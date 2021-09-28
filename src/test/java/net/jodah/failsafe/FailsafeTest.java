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
    RetryPolicy<Boolean> retryPolicy = new RetryPolicy<>();

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
    assertThrows(() -> Failsafe.with(new RetryPolicy<>().withMaxRetries(0)).run(() -> {
      throw new TimeoutException();
    }), FailsafeException.class, TimeoutException.class);
  }
}
