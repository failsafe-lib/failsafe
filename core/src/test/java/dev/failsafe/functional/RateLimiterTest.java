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
package dev.failsafe.functional;

import dev.failsafe.Failsafe;
import dev.failsafe.RateLimitExceededException;
import dev.failsafe.RateLimiter;
import dev.failsafe.testing.Testing;
import org.testng.annotations.Test;

import java.time.Duration;

import static dev.failsafe.internal.InternalTesting.resetLimiter;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Tests various RateLimiter scenarios.
 */
@Test
public class RateLimiterTest extends Testing {
  public void testReservePermit() {
    // Given
    RateLimiter<Object> limiter = RateLimiter.smoothBuilder(Duration.ofMillis(100)).build();

    // When / Then
    assertEquals(limiter.reservePermit(), Duration.ZERO);
    assertTrue(limiter.reservePermit().toMillis() > 0);
    assertTrue(limiter.reservePermit().toMillis() > 100);
  }

  public void testTryReservePermit() {
    // Given
    RateLimiter<Object> limiter = RateLimiter.smoothBuilder(Duration.ofMillis(100)).build();

    // When / Then
    assertEquals(limiter.tryReservePermit(Duration.ofMillis(1)), Duration.ZERO);
    assertEquals(limiter.tryReservePermit(Duration.ofMillis(10)), Duration.ofNanos(-1));
    assertTrue(limiter.tryReservePermit(Duration.ofMillis(100)).toMillis() > 0);
    assertTrue(limiter.tryReservePermit(Duration.ofMillis(200)).toMillis() > 100);
    assertEquals(limiter.tryReservePermit(Duration.ofMillis(100)), Duration.ofNanos(-1));
  }

  public void testPermitAcquiredAfterWait() {
    // Given
    RateLimiter<Object> limiter = RateLimiter.smoothBuilder(Duration.ofMillis(50))
      .withMaxWaitTime(Duration.ofSeconds(1))
      .build();

    // When / Then
    testGetSuccess(() -> {
      resetLimiter(limiter);
      limiter.tryAcquirePermit(); // limiter should now be out of permits
    }, Failsafe.with(limiter), ctx -> {
      return "test";
    }, "test");
  }

  public void shouldThrowRateLimitExceededExceptionAfterPermitsExceeded() {
    // Given
    RateLimiter<Object> limiter = RateLimiter.smoothBuilder(Duration.ofMillis(100)).build();

    // When / Then
    testRunFailure(() -> {
      resetLimiter(limiter);
      limiter.tryAcquirePermit(); // limiter should now be out of permits
    }, Failsafe.with(limiter), ctx -> {
    }, RateLimitExceededException.class);
  }

  /**
   * Asserts that an exceeded maxWaitTime causes RateLimitExceededException.
   */
  public void testMaxWaitTimeExceeded() {
    // Given
    RateLimiter<Object> limiter = RateLimiter.smoothBuilder(Duration.ofMillis(10)).build();

    // When / Then
    testRunFailure(() -> {
      resetLimiter(limiter);
      runAsync(() -> {
        limiter.tryAcquirePermits(50, Duration.ofMinutes(1)); // limiter should now be well over its max permits
      });
      Thread.sleep(150);
    }, Failsafe.with(limiter), ctx -> {
    }, RateLimitExceededException.class);
  }
}
