package net.jodah.failsafe.functional;

import net.jodah.failsafe.*;
import net.jodah.failsafe.function.CheckedRunnable;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.assertFalse;

/**
 * Tests the handling of ordered policy execution.
 */
@Test
public class PolicyOrderingTest {
  static class FooException extends Exception {
  }

  public void testFailsafeWithCircuitBreakerThenRetryThenFallback() {
    RetryPolicy<Object> rp = new RetryPolicy<>().withMaxRetries(2);
    CircuitBreaker<Object> cb = new CircuitBreaker<>().withFailureThreshold(5);
    Fallback<Object> fb = Fallback.of("test");

    Object result = Testing.ignoreExceptions(
      () -> Failsafe.with(fb, rp, cb).onComplete(e -> assertEquals(e.getAttemptCount(), 3)).get(() -> {
        throw new FooException();
      }));

    assertEquals(result, "test");
    assertTrue(cb.isClosed());
  }

  public void testFailsafeWithRetryThenCircuitBreaker() {
    RetryPolicy<Object> rp = new RetryPolicy<>().withMaxRetries(2);
    CircuitBreaker<Object> cb = new CircuitBreaker<>().withFailureThreshold(5);

    Testing.ignoreExceptions(
      () -> Failsafe.with(cb, rp).onComplete(e -> assertEquals(e.getAttemptCount(), 3)).run(() -> {
        throw new Exception();
      }));

    assertTrue(cb.isClosed());
  }

  public void testExecutionWithCircuitBreakerThenRetry() {
    RetryPolicy<Object> rp = new RetryPolicy<>().withMaxRetries(2);
    CircuitBreaker<Object> cb = new CircuitBreaker<>().withFailureThreshold(5);

    Execution execution = new Execution(rp, cb);
    execution.recordFailure(new Exception());
    execution.recordFailure(new Exception());
    assertFalse(execution.isComplete());
    execution.recordFailure(new Exception());
    assertTrue(execution.isComplete());

    assertTrue(cb.isClosed());
  }

  public void testExecutionWithRetryThenCircuitBreaker() {
    RetryPolicy rp = new RetryPolicy().withMaxRetries(1);
    CircuitBreaker cb = new CircuitBreaker().withFailureThreshold(5);

    Execution execution = new Execution(cb, rp);
    execution.recordFailure(new Exception());
    assertFalse(execution.isComplete());
    execution.recordFailure(new Exception());
    assertTrue(execution.isComplete());
    assertTrue(cb.isClosed());
  }

  public void testFailsafeWithRetryThenFallback() {
    RetryPolicy<Object> rp = new RetryPolicy<>().withMaxRetries(2);
    Fallback<Object> fb = Fallback.of("test");
    AtomicInteger executions = new AtomicInteger();

    assertEquals(Failsafe.with(fb, rp).onComplete(e -> executions.set(e.getAttemptCount())).get(() -> {
      throw new IllegalStateException();
    }), "test");
    assertEquals(executions.get(), 3);
  }

  public void testFailsafeWithFallbackThenRetry() {
    RetryPolicy<Object> rp = new RetryPolicy<>().withMaxRetries(2);
    Fallback<Object> fb = Fallback.of("test");
    AtomicInteger executions = new AtomicInteger();

    assertEquals(Failsafe.with(rp, fb).onComplete(e -> executions.set(e.getAttemptCount())).get(() -> {
      throw new IllegalStateException();
    }), "test");
    assertEquals(executions.get(), 1);
  }

  /**
   * Tests that multiple circuit breakers handle failures as expected, regardless of order.
   */
  public void testDuplicateCircuitBreakers() {
    CircuitBreaker cb1 = new CircuitBreaker<>().handle(IllegalArgumentException.class);
    CircuitBreaker cb2 = new CircuitBreaker<>().handle(IllegalStateException.class);

    CheckedRunnable runnable = () -> {
      throw new IllegalArgumentException();
    };
    Testing.ignoreExceptions(() -> Failsafe.with(cb2, cb1).run(runnable));
    assertTrue(cb1.isOpen());
    assertTrue(cb2.isClosed());

    cb1.close();
    Testing.ignoreExceptions(() -> Failsafe.with(cb1, cb2).run(runnable));
    assertTrue(cb1.isOpen());
    assertTrue(cb2.isClosed());
  }

  /**
   * Tests that an inner timeout does not prevent outer retries from being performed.
   */
  public void testTimeoutThenRetry() throws Throwable {
    AtomicInteger timeoutCounter = new AtomicInteger();
    AtomicInteger retryPolicyCounter = new AtomicInteger();
    Timeout<Object> timeout = Timeout.of(Duration.ofMillis(100)).withInterrupt(true).onFailure(e -> {
      timeoutCounter.incrementAndGet();
    });
    RetryPolicy<Object> retryPolicy = new RetryPolicy<>().onRetry(e -> {
      retryPolicyCounter.incrementAndGet();
    });

    // Sync
    Asserts.assertThrows(() -> Failsafe.with(retryPolicy, timeout).get(() -> {
      Thread.sleep(1000);
      throw new Exception();
    }), TimeoutExceededException.class);
    assertEquals(timeoutCounter.get(), 3);
    assertEquals(retryPolicyCounter.get(), 2);

    // Async
    timeoutCounter.set(0);
    retryPolicyCounter.set(0);
    Asserts.assertThrows(() -> Failsafe.with(retryPolicy, timeout).getAsync(() -> {
      Thread.sleep(1000);
      throw new Exception();
    }).get(), ExecutionException.class, TimeoutExceededException.class);
    assertEquals(timeoutCounter.get(), 3);
    assertEquals(retryPolicyCounter.get(), 2);
  }

  /**
   * Tests that an outer timeout will cancel inner retries.
   */
  public void testRetryThenTimeout() {
    AtomicInteger timeoutCounter = new AtomicInteger();
    AtomicInteger retryPolicyCounter = new AtomicInteger();
    Timeout<Object> timeout = Timeout.of(Duration.ofMillis(150)).withInterrupt(true).onFailure(e -> {
      timeoutCounter.incrementAndGet();

    });
    RetryPolicy<Object> retryPolicy = new RetryPolicy<>().onRetry(e -> {
      retryPolicyCounter.incrementAndGet();
    });

    // Sync
    Asserts.assertThrows(() -> Failsafe.with(timeout, retryPolicy).get(() -> {
      Thread.sleep(100);
      throw new Exception();
    }), TimeoutExceededException.class);
    assertEquals(timeoutCounter.get(), 1);
    assertEquals(retryPolicyCounter.get(), 1);

    // Async
    timeoutCounter.set(0);
    retryPolicyCounter.set(0);
    Asserts.assertThrows(() -> Failsafe.with(timeout, retryPolicy).getAsync(() -> {
      Thread.sleep(100);
      throw new Exception();
    }).get(), ExecutionException.class, TimeoutExceededException.class);
    assertEquals(timeoutCounter.get(), 1);
    assertEquals(retryPolicyCounter.get(), 1);
  }
}
