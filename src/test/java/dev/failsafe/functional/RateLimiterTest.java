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

import dev.failsafe.*;
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
  public void shouldThrowRateLimitExceededExceptionAfterPermitsExceeded() {
    // Given
    RateLimiter<Object> limiter = RateLimiter.builder(Duration.ofMillis(100)).build();

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
    RateLimiter<Object> limiter = RateLimiter.builder(Duration.ofMillis(10))
      .withMaxWaitTime(Duration.ofMillis(20))
      .build();

    // When / Then
    testRunFailure(() -> {
      resetLimiter(limiter);
      runAsync(() -> {
        limiter.tryAcquirePermits(50, Duration.ofMinutes(1)); // limiter should now be well over its max permits
      });
      Thread.sleep(100);
    }, Failsafe.with(limiter), ctx -> {
    }, RateLimitExceededException.class);
  }

  /**
   * Tests a scenario where RateLimiter rejects some retried executions, which prevents the user's Supplier from being
   * called.
   */
  public void testRejectedWithRetries() {
    Stats rpStats = new Stats();
    Stats rlStats = new Stats();
    RetryPolicy<Object> rp = withStatsAndLogs(RetryPolicy.builder().withMaxAttempts(7), rpStats).build();
    RateLimiter<Object> rl = withStatsAndLogs(RateLimiter.builder(3, Duration.ofSeconds(1)), rlStats).build();

    testRunFailure(() -> {
      rpStats.reset();
      rlStats.reset();
      resetLimiter(rl);
    }, Failsafe.with(rp, rl), ctx -> {
      System.out.println("Executing");
      throw new Exception();
    }, (f, e) -> {
      assertEquals(e.getAttemptCount(), 7);
      assertEquals(e.getExecutionCount(), 3);
      assertEquals(rpStats.failedAttemptCount, 7);
      assertEquals(rpStats.retryCount, 6);
    }, RateLimitExceededException.class);
  }

  /**
   * Asserts that a rate limiter propagates an InterruptedException.
   */
  public void testAcquirePermitWithInterrupt() {
    RateLimiter<Object> limiter = RateLimiter.builder(Duration.ofSeconds(1))
      .withMaxWaitTime(Duration.ofSeconds(5))
      .build();

    testRunFailure(() -> {
      resetLimiter(limiter);
      limiter.tryAcquirePermit();
      Thread thread = Thread.currentThread();
      runInThread(() -> {
        Thread.sleep(100);
        thread.interrupt();
      });
    }, Failsafe.with(limiter), ctx -> {
      System.out.println("Executing");
      throw new Exception();
    }, InterruptedException.class);
  }
}
