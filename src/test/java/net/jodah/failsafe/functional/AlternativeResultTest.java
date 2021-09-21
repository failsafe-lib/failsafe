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
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * Tests scenarios where a PolicyExecutor's preExecute provides an alternative result, which prevents the user's
 * Supplier from being called. This occurs when a CircuitBreaker is open.
 */
@Test
public class AlternativeResultTest extends Testing {
  public void testRejectedSyncAndAsync() {
    Stats rpStats = new Stats();
    Stats cbStats = new Stats();
    RetryPolicy<Object> rp = withStatsAndLogs(new RetryPolicy<>().withMaxAttempts(7), rpStats);
    CircuitBreaker<Object> cb = withStatsAndLogs(new CircuitBreaker<>().withFailureThreshold(3), cbStats);

    testRunFailure(() -> {
      rpStats.reset();
      cbStats.reset();
      cb.close();
    }, Failsafe.with(rp, cb), ctx -> {
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

  public void testRejectedAsyncExecutionWithRecordFailure() {
    Stats rpStats = new Stats();
    Stats cbStats = new Stats();
    RetryPolicy<Object> rp = withStatsAndLogs(new RetryPolicy<>().withMaxAttempts(7), rpStats);
    CircuitBreaker<Object> cb = withStatsAndLogs(new CircuitBreaker<>().withFailureThreshold(3), cbStats);

    // Test with recordFailure()
    testAsyncExecutionFailure(Failsafe.with(rp, cb), ex -> {
      runAsync(() -> {
        System.out.println("Executing");
        ex.recordFailure(new IllegalStateException());
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
