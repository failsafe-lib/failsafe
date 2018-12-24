package net.jodah.failsafe.functional;

import net.jodah.failsafe.*;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.assertFalse;

@Test
public class PolicyOrderingTest {
  static class FooException extends Exception {
  }

  public void testFailsafeWithCircuitBreakerThenRetryThenFallback() {
    RetryPolicy rp = new RetryPolicy().withMaxRetries(2);
    CircuitBreaker cb = new CircuitBreaker().withFailureThreshold(5);
    Fallback fb = Fallback.of("test");

    Object result = Testing.ignoreExceptions(() -> Failsafe.with(fb, rp, cb)
      .onComplete((r, f, ctx) -> assertEquals(ctx.getExecutions(), 3))
      .get(() -> {
        throw new FooException();
      }));

    assertEquals(result, "test");
    assertTrue(cb.isClosed());
  }

  public void testFailsafeWithRetryThenCircuitBreaker() {
    RetryPolicy rp = new RetryPolicy().withMaxRetries(2);
    CircuitBreaker cb = new CircuitBreaker().withFailureThreshold(5);

    Testing.ignoreExceptions(() -> Failsafe.with(cb, rp)
      .onComplete((r, f, ctx) -> assertEquals(ctx.getExecutions(), 3))
      .run(() -> {
        throw new Exception();
      }));

    assertTrue(cb.isClosed());
  }

  public void testExecutionWithCircuitBreakerThenRetry() {
    CircuitBreaker cb = new CircuitBreaker().withFailureThreshold(5);
    RetryPolicy rp = new RetryPolicy().withMaxRetries(2);

    Execution execution = new Execution(rp, cb);
    execution.recordFailure(new Exception());
    execution.recordFailure(new Exception());
    assertFalse(execution.isComplete());
    execution.recordFailure(new Exception());
    assertTrue(execution.isComplete());

    assertTrue(cb.isClosed());
  }

  public void testExecutionWithRetryThenCircuitBreaker() {
    RetryPolicy rp = new RetryPolicy().withMaxRetries(2);
    CircuitBreaker cb = new CircuitBreaker().withFailureThreshold(5);

    Execution execution = new Execution(cb, rp);
    execution.recordFailure(new Exception());
    assertTrue(execution.isComplete());

    assertTrue(cb.isClosed());
  }
}
