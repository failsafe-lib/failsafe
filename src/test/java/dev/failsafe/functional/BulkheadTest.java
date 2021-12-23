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

import static dev.failsafe.internal.InternalTesting.resetBulkhead;
import static dev.failsafe.internal.InternalTesting.resetLimiter;
import static org.testng.Assert.assertEquals;

/**
 * Tests various Bulkhead scenarios.
 */
@Test
public class BulkheadTest extends Testing {
  public void shouldThrowBulkheadFullExceptionAfterPermitsExceeded() {
    // Given
    Bulkhead<Object> bulkhead = Bulkhead.of(2);
    bulkhead.tryAcquirePermit();
    bulkhead.tryAcquirePermit(); // bulkhead should be full

    // When / Then
    testRunFailure(Failsafe.with(bulkhead), ctx -> {
    }, BulkheadFullException.class);
  }

  /**
   * Asserts that an exceeded maxWaitTime causes BulkheadFullException.
   */
  public void testMaxWaitTimeExceeded() {
    // Given
    Bulkhead<Object> bulkhead = Bulkhead.builder(2).withMaxWaitTime(Duration.ofMillis(20)).build();
    bulkhead.tryAcquirePermit();
    bulkhead.tryAcquirePermit(); // bulkhead should be full

    // When / Then
    testRunFailure(Failsafe.with(bulkhead), ctx -> {
    }, BulkheadFullException.class);
  }

  /**
   * Tests a scenario where Bulkhead rejects some retried executions, which prevents the user's Supplier from being
   * called.
   */
  public void testRejectedWithRetries() {
    Stats rpStats = new Stats();
    Stats rlStats = new Stats();
    RetryPolicy<Object> rp = withStatsAndLogs(RetryPolicy.builder().withMaxAttempts(7), rpStats).build();
    Bulkhead<Object> bh = withStatsAndLogs(Bulkhead.builder(2), rlStats).build();
    bh.tryAcquirePermit();
    bh.tryAcquirePermit(); // bulkhead should be full

    testRunFailure(() -> {
      rpStats.reset();
      rlStats.reset();
    }, Failsafe.with(rp, bh), ctx -> {
      System.out.println("Executing");
      throw new Exception();
    }, (f, e) -> {
      assertEquals(e.getAttemptCount(), 7);
      assertEquals(e.getExecutionCount(), 0);
      assertEquals(rpStats.failedAttemptCount, 7);
      assertEquals(rpStats.retryCount, 6);
    }, BulkheadFullException.class);
  }

  /**
   * Asserts that a bulkhead propagates an InterruptedException.
   */
  public void testAcquirePermitWithInterrupt() {
    Bulkhead<Object> bulkhead = Bulkhead.builder(1).withMaxWaitTime(Duration.ofSeconds(5)).build();
    bulkhead.tryAcquirePermit(); // Bulkhead should be full

    testRunFailure(() -> {
      Thread thread = Thread.currentThread();
      runInThread(() -> {
        Thread.sleep(100);
        thread.interrupt();
      });
    }, Failsafe.with(bulkhead), ctx -> {
      System.out.println("Executing");
      throw new Exception();
    }, InterruptedException.class);
  }
}
