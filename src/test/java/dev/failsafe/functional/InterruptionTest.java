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
import dev.failsafe.testing.Asserts;
import dev.failsafe.testing.Testing;
import org.testng.annotations.Test;

import java.time.Duration;

import static org.testng.Assert.*;

/**
 * Tests various execution interrupt scenarios.
 */
@Test
public class InterruptionTest extends Testing {
  /**
   * Asserts that a blocked execution can be interrupted.
   */
  public void testInterruptSyncExecution() {
    scheduleInterrupt(100);

    Asserts.assertThrows(() -> Failsafe.with(retryNever).run(() -> {
      Thread.sleep(1000);
      fail("Expected interruption");
    }), FailsafeException.class, InterruptedException.class);
    // Clear interrupt flag
    assertTrue(Thread.interrupted());
  }

  /**
   * Asserts that a blocked retry policy delay can be interrupted.
   */
  public void testInterruptSyncRetryPolicyDelay() {
    RetryPolicy<Object> rp = RetryPolicy.builder().withDelay(Duration.ofMillis(500)).build();
    scheduleInterrupt(100);

    Asserts.assertThrows(() -> Failsafe.with(rp).run(() -> {
      throw new Exception();
    }), FailsafeException.class, InterruptedException.class);
    // Clear interrupt flag
    assertTrue(Thread.interrupted());
  }

  /**
   * Asserts that a blocked rate limiter acquirePermit can be interrupted.
   */
  public void testInterruptRateLimiterAcquirePermit() {
    RateLimiter<Object> limiter = RateLimiter.smoothBuilder(Duration.ofSeconds(1))
      .withMaxWaitTime(Duration.ofSeconds(5))
      .build();
    limiter.tryAcquirePermit(); // Rate limiter should be full
    scheduleInterrupt(100);

    assertThrows(() -> Failsafe.with(limiter).run(() -> {
      System.out.println("Executing");
      throw new Exception();
    }), FailsafeException.class, InterruptedException.class);
    // Clear interrupt flag
    assertTrue(Thread.interrupted());
  }

  /**
   * Asserts that a blocked bulkhead acquirePermit can be interrupted.
   */
  public void testInterruptBulkheadAcquirePermit() {
    Bulkhead<Object> bulkhead = Bulkhead.builder(1).withMaxWaitTime(Duration.ofSeconds(5)).build();
    bulkhead.tryAcquirePermit(); // Bulkhead should be full
    scheduleInterrupt(100);

    assertThrows(() -> Failsafe.with(bulkhead).run(() -> {
      System.out.println("Executing");
      throw new Exception();
    }), FailsafeException.class, InterruptedException.class);

    // Clear interrupt flag
    assertTrue(Thread.interrupted());
  }

  /**
   * Ensures that an internally interrupted execution should always have the interrupt flag cleared afterwards.
   */
  public void shouldResetInterruptFlagAfterInterruption() {
    // Given
    Timeout<Object> timeout = Timeout.builder(Duration.ofMillis(1)).withInterrupt().build();

    // When / Then
    testRunFailure(false, Failsafe.with(timeout), ctx -> {
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }, (f, e) -> {
      assertFalse(Thread.currentThread().isInterrupted(), "Interrupt flag should be cleared after Failsafe handling");
    }, TimeoutExceededException.class);
  }

  /**
   * Schedules an interrupt of the calling thread.
   */
  private void scheduleInterrupt(long millis) {
    Thread thread = Thread.currentThread();
    runInThread(() -> {
      Thread.sleep(millis);
      thread.interrupt();
    });
  }
}
