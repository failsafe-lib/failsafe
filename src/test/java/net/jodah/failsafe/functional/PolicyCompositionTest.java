/*
 * Copyright 2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package net.jodah.failsafe.functional;

import net.jodah.failsafe.*;
import net.jodah.failsafe.testing.Testing;
import org.testng.annotations.Test;

import java.time.Duration;

import static org.testng.Assert.*;

/**
 * Tests various policy composition scenarios.
 */
@Test
public class PolicyCompositionTest extends Testing {
  /**
   * RetryPolicy -> CircuitBreaker
   */
  public void testRetryPolicyCircuitBreaker() {
    RetryPolicy<Boolean> rp = new RetryPolicy<Boolean>().withMaxRetries(-1);
    CircuitBreaker<Boolean> cb = new CircuitBreaker<Boolean>().withFailureThreshold(3)
      .withDelay(Duration.ofMinutes(10));
    Service service = mockService(2, true);

    testGetSuccess(() -> {
      service.reset();
      resetBreaker(cb);
    }, Failsafe.with(rp, cb), ctx -> {
      return service.connect();
    }, (f, e) -> {
      assertEquals(e.getAttemptCount(), 3);
      assertEquals(cb.getFailureCount(), 2);
      assertEquals(cb.getSuccessCount(), 1);
      assertTrue(cb.isClosed());
    }, true);
  }

  /**
   * RetryPolicy -> CircuitBreaker
   * <p>
   * Asserts handling of an open breaker.
   */
  public void testRetryPolicyCircuitBreakerWithOpenBreaker() {
    // Given
    RetryPolicy<Object> retryPolicy = Testing.withLogs(new RetryPolicy<>());
    CircuitBreaker<Object> cb = Testing.withLogs(new CircuitBreaker<>());

    // When / Then
    testRunFailure(() -> {
      resetBreaker(cb);
    }, Failsafe.with(retryPolicy, cb), ctx -> {
      Thread.sleep(10);
      throw new Exception();
    }, (f, e) -> {
    }, CircuitBreakerOpenException.class);
  }

  /**
   * CircuitBreaker -> RetryPolicy
   */
  public void testCircuitBreakerRetryPolicy() {
    RetryPolicy<Object> rp = new RetryPolicy<>().withMaxRetries(2);
    CircuitBreaker<Object> cb = new CircuitBreaker<>().withFailureThreshold(5);

    testRunFailure(() -> {
      resetBreaker(cb);
    }, Failsafe.with(cb).compose(rp), ctx -> {
      throw new IllegalStateException();
    }, (f, e) -> {
      assertEquals(e.getAttemptCount(), 3);
      assertEquals(cb.getFailureCount(), 1);
      assertEquals(cb.getSuccessCount(), 0);
      assertTrue(cb.isClosed());
    }, IllegalStateException.class);
  }

  /**
   * Fallback -> RetryPolicy -> CircuitBreaker
   */
  public void testFallbackRetryPolicyCircuitBreaker() {
    RetryPolicy<Object> rp = new RetryPolicy<>().withMaxRetries(2);
    CircuitBreaker<Object> cb = new CircuitBreaker<>().withFailureThreshold(5);
    Fallback<Object> fb = Fallback.ofAsync(() -> "test");

    testRunSuccess(() -> {
      resetBreaker(cb);
    }, Failsafe.with(fb).compose(rp).compose(cb), ctx -> {
      throw new IllegalStateException();
    }, (f, e) -> {
      assertEquals(cb.getFailureCount(), 3);
      assertEquals(cb.getSuccessCount(), 0);
      assertTrue(cb.isClosed());
    }, "test");
  }

  /**
   * Fallback -> RetryPolicy
   */
  public void testFallbackRetryPolicy() {
    Fallback<Object> fb = Fallback.of(e -> {
      assertNull(e.getLastResult());
      assertTrue(e.getLastFailure() instanceof IllegalStateException);
      return "test";
    });
    RetryPolicy<Object> rp = new RetryPolicy<>();

    testRunSuccess(Failsafe.with(fb).compose(rp), ctx -> {
      throw new IllegalStateException();
    }, (f, e) -> {
      assertEquals(e.getAttemptCount(), 3);
    }, "test");
  }

  /**
   * RetryPolicy -> Fallback
   */
  public void testRetryPolicyFallback() {
    // Given
    RetryPolicy<Object> rp = new RetryPolicy<>().withMaxRetries(2);
    Fallback<Object> fb = Fallback.of("test");

    // When / Then
    testRunSuccess(Failsafe.with(rp).compose(fb), ctx -> {
      throw new IllegalStateException();
    }, (f, e) -> {
      assertEquals(e.getAttemptCount(), 1);
    }, "test");
  }

  /**
   * Fallback -> CircuitBreaker
   * <p>
   * Tests fallback with a circuit breaker that is closed.
   */
  public void testFallbackCircuitBreaker() {
    // Given
    Fallback<Object> fallback = Fallback.of(e -> {
      assertNull(e.getLastResult());
      assertTrue(e.getLastFailure() instanceof IllegalStateException);
      return false;
    });
    CircuitBreaker<Object> breaker = new CircuitBreaker<>().withSuccessThreshold(3);

    // When / Then
    testGetSuccess(() -> {
      resetBreaker(breaker);
    }, Failsafe.with(fallback, breaker), ctx -> {
      throw new IllegalStateException();
    }, false);
  }

  /**
   * Fallback -> CircuitBreaker
   * <p>
   * Tests fallback with a circuit breaker that is open.
   */
  public void testFallbackCircuitBreakerOpen() {
    // Given
    Fallback<Object> fallback = Fallback.of(e -> {
      assertNull(e.getLastResult());
      assertTrue(e.getLastFailure() instanceof CircuitBreakerOpenException);
      return false;
    });
    CircuitBreaker<Object> breaker = new CircuitBreaker<>().withSuccessThreshold(3);

    // When / Then with open breaker
    testGetSuccess(() -> {
      breaker.open();
    }, Failsafe.with(fallback, breaker), ctx -> {
      return true;
    }, false);
  }

  /**
   * RetryPolicy -> Timeout
   * <p>
   * Tests 2 timeouts, then a success, and asserts the ExecutionContext is cancelled after each timeout.
   */
  public void testRetryPolicyTimeout() {
    // Given
    RetryPolicy<Object> rp = new RetryPolicy<>().onFailedAttempt(e -> {
      assertTrue(e.getLastFailure() instanceof TimeoutExceededException);
    });
    Stats timeoutStats = new Stats();
    Timeout<Object> timeout = withStatsAndLogs(Timeout.of(Duration.ofMillis(50)), timeoutStats);
    Recorder recorder = new Recorder();

    // When / Then
    Runnable test = () -> testGetSuccess(false, () -> {
      recorder.reset();
      timeoutStats.reset();
    }, Failsafe.with(rp, timeout), ctx -> {
      if (ctx.getAttemptCount() < 2) {
        Thread.sleep(100);
        recorder.assertTrue(ctx.isCancelled());
      } else {
        recorder.assertFalse(ctx.isCancelled());
      }
      return "success";
    }, (f, e) -> {
      recorder.throwFailure();
      assertEquals(e.getAttemptCount(), 3);
      assertEquals(e.getExecutionCount(), 3);
      assertEquals(timeoutStats.failureCount, 2);
      assertEquals(timeoutStats.successCount, 1);
    }, "success");

    // Without interrupt
    test.run();

    // With interrupt
    timeout.withInterrupt(true);
    test.run();
  }

  /**
   * CircuitBreaker -> Timeout
   */
  public void testCircuitBreakerTimeout() {
    // Given
    Timeout<Object> timeout = Timeout.of(Duration.ofMillis(50));
    CircuitBreaker<Object> breaker = new CircuitBreaker<>();
    assertTrue(breaker.isClosed());

    // When / Then
    testRunFailure(() -> {
      resetBreaker(breaker);
    }, Failsafe.with(breaker, timeout), ctx -> {
      System.out.println("Executing");
      Thread.sleep(100);
    }, TimeoutExceededException.class);
    assertTrue(breaker.isOpen());
  }

  /**
   * Fallback -> Timeout
   */
  public void testFallbackTimeout() {
    // Given
    Fallback<Object> fallback = Fallback.of(e -> {
      assertTrue(e.getLastFailure() instanceof TimeoutExceededException);
      return false;
    });
    Timeout<Object> timeout = Timeout.of(Duration.ofMillis(10));

    // When / Then
    testGetSuccess(false, Failsafe.with(fallback, timeout), ctx -> {
      Thread.sleep(100);
      return true;
    }, false);
  }
}
