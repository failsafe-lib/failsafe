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
import net.jodah.failsafe.Testing.Stats;
import org.testng.annotations.Test;

import static net.jodah.failsafe.Testing.*;
import static org.testng.Assert.assertEquals;

/**
 * Tests scenarios where a PolicyExecutor's preExecute provides an alternative result, which prevents the user's
 * Supplier from being called. This occurs when a CircuitBreaker is open.
 */
@Test
public class AlternativeResultTest {
  public void testRejectedSyncAndAsync() {
    Stats rpStats = new Stats();
    Stats cbStats = new Stats();
    RetryPolicy<Object> rp = withStats(new RetryPolicy<>().withMaxAttempts(7), rpStats, true);
    CircuitBreaker<Object> cb = withStats(new CircuitBreaker<>().withFailureThreshold(3), cbStats, true);

    testSyncAndAsyncFailure(Failsafe.with(rp, cb), () -> {
      rpStats.reset();
      cbStats.reset();
      cb.close();
    }, () -> {
      System.out.println("Executing");
      throw new Exception();
    }, e -> {
      assertEquals(e.getAttemptCount(), 7);
      assertEquals(e.getExecutionCount(), 3);
      assertEquals(rpStats.failedAttemptCount, 7);
      assertEquals(rpStats.retryCount, 6);
      assertEquals(cb.getExecutionCount(), 3);
      assertEquals(cb.getFailureCount(), 3);
    }, CircuitBreakerOpenException.class);
  }

  public void testRejectedAsyncExecutionWithRetry() {
    Stats rpStats = new Stats();
    Stats cbStats = new Stats();
    RetryPolicy<Object> rp = withStats(new RetryPolicy<>().withMaxAttempts(7), rpStats, true);
    CircuitBreaker<Object> cb = withStats(new CircuitBreaker<>().withFailureThreshold(3), cbStats, true);

    // Test with retryOn()
    testAsyncExecutionFailure(Failsafe.with(rp, cb), ex -> {
      runAsync(() -> {
        System.out.println("Executing");
        ex.retryOn(new IllegalStateException());
      });
    }, e -> {
      assertEquals(e.getAttemptCount(), 7);
      assertEquals(e.getExecutionCount(), 3);
      assertEquals(rpStats.failedAttemptCount, 7);
      assertEquals(rpStats.retryCount, 6);
      assertEquals(cb.getExecutionCount(), 3);
      assertEquals(cb.getFailureCount(), 3);
    }, CircuitBreakerOpenException.class);
  }

  @Test(enabled = false)
  public void testRejectedAsyncExecutionWithCompleteAndRetry() {
    Stats rpStats = new Stats();
    Stats cbStats = new Stats();
    RetryPolicy<Object> rp = withStats(new RetryPolicy<>().withMaxAttempts(7), rpStats, true);
    CircuitBreaker<Object> cb = withStats(new CircuitBreaker<>().withFailureThreshold(3), cbStats, true);

    // Test with complete() and retry()
    rpStats.reset();
    cbStats.reset();
    cb.close();
    testAsyncExecutionFailure(Failsafe.with(rp, cb), ex -> {
      runAsync(() -> {
        System.out.println("Executing");
        if (!ex.complete(null, new IllegalStateException())) {
          ex.retry();
        }
      });
    }, e -> {
      assertEquals(e.getAttemptCount(), 7);
      assertEquals(e.getExecutionCount(), 3);
      assertEquals(rpStats.failedAttemptCount, 7);
      assertEquals(rpStats.retryCount, 6);
      assertEquals(cb.getExecutionCount(), 3);
      assertEquals(cb.getFailureCount(), 3);
    }, CircuitBreakerOpenException.class);
  }
}
