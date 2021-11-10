/*
 * Copyright 2018 the original author or authors.
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
package dev.failsafe.functional;

import dev.failsafe.*;
import dev.failsafe.testing.Testing;
import net.jodah.concurrentunit.Waiter;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Tests various CircuitBreaker scenarios.
 */
@Test
public class CircuitBreakerTest extends Testing {
  public void shouldRejectInitialExecutionWhenCircuitOpen() {
    // Given
    CircuitBreaker<Object> cb = CircuitBreaker.ofDefaults();

    // When / Then
    testRunFailure(() -> {
      cb.open();
    }, Failsafe.with(cb), ctx -> {
    }, (f, e) -> {
      assertEquals(e.getAttemptCount(), 0);
      assertEquals(e.getExecutionCount(), 0);
    }, CircuitBreakerOpenException.class);
    assertTrue(cb.isOpen());
  }

  /**
   * Should throw CircuitBreakerOpenException when max half-open executions are occurring.
   */
  public void shouldRejectExcessiveAttemptsWhenBreakerHalfOpen() throws Throwable {
    // Given
    CircuitBreaker<Object> breaker = CircuitBreaker.builder().withSuccessThreshold(3).build();
    breaker.halfOpen();
    Waiter waiter = new Waiter();

    // Create some pending half-open executions
    for (int i = 0; i < 3; i++)
      runInThread(() -> Failsafe.with(breaker).run(() -> {
        waiter.resume();
        Thread.sleep(1000);
      }));

    // Assert that the breaker does not allow any more executions at the moment
    waiter.await(10000, 3);
    for (int i = 0; i < 5; i++)
      assertThrows(() -> Failsafe.with(breaker).get(() -> null), CircuitBreakerOpenException.class);
  }

  /**
   * Tests the handling of a circuit breaker with no conditions.
   */
  public void testCircuitBreakerWithoutConditions() {
    // Given
    CircuitBreaker<Object> breaker = CircuitBreaker.builder().withDelay(Duration.ZERO).build();

    // When / Then
    testRunFailure(() -> {
      resetBreaker(breaker);
    }, Failsafe.with(breaker), ctx -> {
      throw new IllegalStateException();
    }, IllegalStateException.class);
    assertTrue(breaker.isOpen());

    // Given
    RetryPolicy<Object> retryPolicy = RetryPolicy.builder().withMaxRetries(5).build();
    AtomicInteger counter = new AtomicInteger();

    // When / Then
    testGetSuccess(() -> {
      resetBreaker(breaker);
    }, Failsafe.with(retryPolicy, breaker), ctx -> {
      if (counter.incrementAndGet() < 3)
        throw new ConnectException();
      return true;
    }, true);
    assertTrue(breaker.isClosed());
  }

  public void shouldThrowCircuitBreakerOpenExceptionAfterFailuresExceeded() {
    // Given
    CircuitBreaker<Object> breaker = CircuitBreaker.builder()
      .withFailureThreshold(2)
      .handleResult(false)
      .withDelay(Duration.ofSeconds(10))
      .build();

    // When
    Failsafe.with(breaker).get(() -> false);
    Failsafe.with(breaker).get(() -> false);

    // Then
    testGetFailure(Failsafe.with(breaker), ctx -> {
      return true;
    }, CircuitBreakerOpenException.class);
  }

  /**
   * Tests a scenario where CircuitBreaker rejects some retried executions, which prevents the user's Supplier from
   * being called.
   */
  public void testRejectedWithRetries() {
    Stats rpStats = new Stats();
    Stats cbStats = new Stats();
    RetryPolicy<Object> rp = withStatsAndLogs(RetryPolicy.builder().withMaxAttempts(7), rpStats).build();
    CircuitBreaker<Object> cb = withStatsAndLogs(CircuitBreaker.builder().withFailureThreshold(3), cbStats).build();

    testRunFailure(() -> {
      rpStats.reset();
      cbStats.reset();
      resetBreaker(cb);
    }, Failsafe.with(rp, cb), ctx -> {
      System.out.println("Executing");
      throw new Exception();
    }, (f, e) -> {
      assertEquals(e.getAttemptCount(), 7);
      assertEquals(e.getExecutionCount(), 3);
      assertEquals(rpStats.failedAttemptCount, 7);
      assertEquals(rpStats.retryCount, 6);
      assertEquals(cb.getExecutionCount(), 3);
      assertEquals(cb.getFailureCount(), 3);
    }, CircuitBreakerOpenException.class);
  }

  /**
   * Tests circuit breaker time based failure thresholding state transitions.
   */
  public void shouldSupportTimeBasedFailureThresholding() throws Throwable {
    // Given
    CircuitBreaker<Boolean> circuitBreaker = CircuitBreaker.<Boolean>builder()
      .withFailureThreshold(2, 3, Duration.ofMillis(200))
      .withDelay(Duration.ofMillis(0))
      .handleResult(false)
      .build();
    FailsafeExecutor<Boolean> executor = Failsafe.with(circuitBreaker);

    // When / Then
    executor.get(() -> false);
    executor.get(() -> true);
    // Force results to roll off
    Thread.sleep(210);
    executor.get(() -> false);
    executor.get(() -> true);
    // Force result to another bucket
    Thread.sleep(50);
    assertTrue(circuitBreaker.isClosed());
    executor.get(() -> false);
    assertTrue(circuitBreaker.isOpen());
    executor.get(() -> false);
    assertTrue(circuitBreaker.isHalfOpen());
    // Half-open -> Open
    executor.get(() -> false);
    assertTrue(circuitBreaker.isOpen());
    executor.get(() -> false);
    assertTrue(circuitBreaker.isHalfOpen());
    // Half-open -> close
    executor.get(() -> true);
    assertTrue(circuitBreaker.isClosed());
  }

  /**
   * Tests circuit breaker time based failure rate thresholding state transitions.
   */
  public void shouldSupportTimeBasedFailureRateThresholding() throws Throwable {
    // Given
    Stats cbStats = new Stats();
    CircuitBreaker<Boolean> circuitBreaker = withStatsAndLogs(CircuitBreaker.<Boolean>builder()
      .withFailureRateThreshold(50, 3, Duration.ofMillis(200))
      .withDelay(Duration.ofMillis(0))
      .handleResult(false), cbStats).build();
    FailsafeExecutor<Boolean> executor = Failsafe.with(circuitBreaker);

    // When / Then
    executor.get(() -> false);
    executor.get(() -> true);
    // Force results to roll off
    Thread.sleep(210);
    executor.get(() -> false);
    executor.get(() -> true);
    // Force result to another bucket
    Thread.sleep(50);
    executor.get(() -> true);
    assertTrue(circuitBreaker.isClosed());
    executor.get(() -> false);
    assertTrue(circuitBreaker.isOpen());
    executor.get(() -> false);
    assertTrue(circuitBreaker.isHalfOpen());
    executor.get(() -> false);
    // Half-open -> Open
    executor.get(() -> false);
    assertTrue(circuitBreaker.isOpen());
    executor.get(() -> false);
    assertTrue(circuitBreaker.isHalfOpen());
    executor.get(() -> true);
    // Half-open -> close
    executor.get(() -> true);
    assertTrue(circuitBreaker.isClosed());
  }
}
