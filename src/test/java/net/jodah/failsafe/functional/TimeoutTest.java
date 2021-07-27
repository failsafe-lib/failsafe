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

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static net.jodah.failsafe.Testing.testSyncAndAsync;
import static org.testng.Assert.assertEquals;

/**
 * Tests various Timeout policy scenarios.
 */
@Test
public class TimeoutTest {
  /**
   * Tests that an inner timeout does not prevent outer retries from being performed when the inner Supplier is
   * blocked.
   */
  public void testTimeoutThenRetryWithBlockedSupplier() {
    AtomicInteger timeoutCounter = new AtomicInteger();
    AtomicInteger retryPolicyCounter = new AtomicInteger();
    Timeout<Object> timeout = Timeout.of(Duration.ofMillis(1)).onFailure(e -> {
      timeoutCounter.incrementAndGet();
      System.out.println("Timed out");
    });
    RetryPolicy<Object> retryPolicy = new RetryPolicy<>().withMaxRetries(2).onRetry(e -> {
      retryPolicyCounter.incrementAndGet();
      System.out.println("Retrying");
    });

    Runnable test = () -> testSyncAndAsync(Failsafe.with(retryPolicy, timeout), () -> {
      timeoutCounter.set(0);
      retryPolicyCounter.set(0);
    }, () -> {
      Thread.sleep(100);
      throw new Exception();
    }, e -> {
      assertEquals(e.getAttemptCount(), 3);
      assertEquals(e.getExecutionCount(), 3);
      assertEquals(timeoutCounter.get(), 3);
      assertEquals(retryPolicyCounter.get(), 2);
    }, TimeoutExceededException.class);

    // Test without interrupt
    timeout.withInterrupt(false);
    test.run();

    // Test with interrupt
    timeout.withInterrupt(true);
    test.run();
  }

  /**
   * Tests that an inner timeout does not prevent outer retries from being performed when a retry is pending. In this
   * test, the timeout has no effect on the outer retry policy.
   */
  public void testTimeoutThenRetryWithPendingRetry() {
    AtomicInteger executionCounter = new AtomicInteger();
    AtomicInteger timeoutSuccessCounter = new AtomicInteger();
    AtomicInteger timeoutFailureCounter = new AtomicInteger();
    Timeout<Object> timeout = Timeout.of(Duration.ofMillis(10)).onSuccess(e -> {
      timeoutSuccessCounter.incrementAndGet();
      System.out.println("Timed out");
    }).onFailure(e -> {
      timeoutFailureCounter.incrementAndGet();
    });
    RetryPolicy<Object> retryPolicy = new RetryPolicy<>().withDelay(Duration.ofMillis(100)).onRetry(e -> {
      System.out.println("Retrying");
    });

    Runnable test = () -> testSyncAndAsync(Failsafe.with(retryPolicy, timeout), () -> {
      executionCounter.set(0);
      timeoutSuccessCounter.set(0);
    }, () -> {
      executionCounter.incrementAndGet();
      throw new IllegalStateException();
    }, e -> {
      assertEquals(e.getAttemptCount(), 3);
      assertEquals(e.getExecutionCount(), 3);
      assertEquals(executionCounter.get(), 3);
      assertEquals(timeoutSuccessCounter.get(), 3);
      assertEquals(timeoutFailureCounter.get(), 0);
    }, IllegalStateException.class);

    // Test without interrupt
    timeout.withInterrupt(false);
    test.run();

    // Test with interrupt
    timeout.withInterrupt(true);
    test.run();
  }

  /**
   * Tests that an outer timeout will cancel inner retries when the inner Supplier is blocked. The flow should be:
   * <p>
   * <br>Execution that blocks
   * <br>Timeout
   */
  public void testRetryThenTimeoutWithBlockedSupplier() {
    AtomicInteger timeoutCounter = new AtomicInteger();
    AtomicInteger executionCounter = new AtomicInteger();
    Timeout<Object> timeout = Timeout.of(Duration.ofMillis(1)).onFailure(e -> {
      timeoutCounter.incrementAndGet();
      System.out.println("Timed out");
    });
    RetryPolicy<Object> retryPolicy = new RetryPolicy<>().onRetry(e -> {
      System.out.println("Retrying");
    });

    Runnable test = () -> testSyncAndAsync(Failsafe.with(timeout, retryPolicy), () -> {
      executionCounter.set(0);
      timeoutCounter.set(0);
    }, () -> {
      System.out.println("Executing");
      executionCounter.incrementAndGet();
      Thread.sleep(100);
      throw new Exception();
    }, e -> {
      assertEquals(e.getAttemptCount(), 1);
      assertEquals(e.getExecutionCount(), 1);
      assertEquals(executionCounter.get(), 1);
      assertEquals(timeoutCounter.get(), 1);
    }, TimeoutExceededException.class);

    // Test without interrupt
    timeout.withInterrupt(false);
    test.run();

    // Test with interrupt
    timeout.withInterrupt(true);
    test.run();
  }

  /**
   * Tests that an outer timeout will cancel inner retries when an inner retry is pending. The flow should be:
   * <p>
   * <br>Execution
   * <br>Retry sleep/scheduled that blocks
   * <br>Timeout
   */
  public void testRetryThenTimeoutWithPendingRetry() {
    AtomicInteger executionCounter = new AtomicInteger();
    AtomicInteger timeoutCounter = new AtomicInteger();
    AtomicInteger failedAttemptCounter = new AtomicInteger();
    Timeout<Object> timeout = Timeout.of(Duration.ofMillis(100)).onFailure(e -> {
      System.out.println("Timed out");
      timeoutCounter.incrementAndGet();
    });
    RetryPolicy<Object> retryPolicy = new RetryPolicy<>().withDelay(Duration.ofMillis(1000)).onFailedAttempt(e -> {
      failedAttemptCounter.incrementAndGet();
    }).onRetry(e -> {
      System.out.println("Retrying");
    });

    Runnable test = () -> testSyncAndAsync(Failsafe.with(timeout, retryPolicy), () -> {
      executionCounter.set(0);
      timeoutCounter.set(0);
      failedAttemptCounter.set(0);
    }, () -> {
      System.out.println("Executing");
      executionCounter.incrementAndGet();
      throw new Exception();
    }, e -> {
      assertEquals(e.getAttemptCount(), 1);
      assertEquals(e.getExecutionCount(), 1);
      assertEquals(executionCounter.get(), 1);
      assertEquals(timeoutCounter.get(), 1);
      assertEquals(failedAttemptCounter.get(), 1);
    }, TimeoutExceededException.class);

    // Test without interrupt
    timeout.withInterrupt(false);
    test.run();

    // Test with interrupt
    timeout.withInterrupt(true);
    test.run();
  }

  /**
   * Tests an inner timeout that fires while the supplier is blocked.
   */
  public void testTimeoutThenFallbackWithBlockedSupplier() {
    AtomicInteger timeoutCounter = new AtomicInteger();
    AtomicInteger fallbackCounter = new AtomicInteger();
    Timeout<Object> timeout = Timeout.of(Duration.ofMillis(1)).onFailure(e -> {
      System.out.println("Timed out");
      timeoutCounter.incrementAndGet();
    });
    Fallback<Object> fallback = Fallback.of(() -> {
      System.out.println("Falling back");
      fallbackCounter.incrementAndGet();
      throw new IllegalStateException();
    });

    Runnable test = () -> testSyncAndAsync(Failsafe.with(fallback, timeout), () -> {
      timeoutCounter.set(0);
      fallbackCounter.set(0);
    }, () -> {
      System.out.println("Executing");
      Thread.sleep(100);
      throw new Exception();
    }, e -> {
      assertEquals(e.getAttemptCount(), 1);
      assertEquals(e.getExecutionCount(), 1);
      assertEquals(timeoutCounter.get(), 1);
      assertEquals(fallbackCounter.get(), 1);
    }, IllegalStateException.class);

    // Test without interrupt
    timeout.withInterrupt(false);
    test.run();

    // Test with interrupt
    timeout.withInterrupt(true);
    test.run();
  }

  /**
   * Tests that an inner timeout will not interrupt an outer fallback. The inner timeout is never triggered since the
   * supplier completes immediately.
   */
  public void testTimeoutThenFallback() {
    AtomicInteger timeoutCounter = new AtomicInteger();
    AtomicInteger fallbackCounter = new AtomicInteger();
    Timeout<Object> timeout = Timeout.of(Duration.ofMillis(100)).onFailure(e -> {
      System.out.println("Timed out");
      timeoutCounter.incrementAndGet();
    });
    Fallback<Object> fallback = Fallback.of(() -> {
      System.out.println("Falling back");
      fallbackCounter.incrementAndGet();
      throw new IllegalStateException();
    });

    Runnable test = () -> testSyncAndAsync(Failsafe.with(fallback, timeout), () -> {
      fallbackCounter.set(0);
    }, () -> {
      System.out.println("Executing");
      throw new Exception();
    }, e -> {
      assertEquals(e.getAttemptCount(), 1);
      assertEquals(e.getExecutionCount(), 1);
      assertEquals(timeoutCounter.get(), 0);
      assertEquals(fallbackCounter.get(), 1);
    }, IllegalStateException.class);

    // Test without interrupt
    timeout.withInterrupt(false);
    test.run();

    // Test with interrupt
    timeout.withInterrupt(true);
    test.run();
  }

  /**
   * Tests that an outer timeout will interrupt an inner supplier that is blocked, skipping the inner fallback.
   */
  public void testFallbackThenTimeoutWithBlockedSupplier() {
    AtomicInteger timeoutCounter = new AtomicInteger();
    AtomicInteger fallbackCounter = new AtomicInteger();
    Timeout<Object> timeout = Timeout.of(Duration.ofMillis(1)).onFailure(e -> {
      System.out.println("Timed out");
      timeoutCounter.incrementAndGet();
    });
    Fallback<Object> fallback = Fallback.of(() -> {
      System.out.println("Falling back");
      fallbackCounter.incrementAndGet();
      throw new IllegalStateException();
    });

    Runnable test = () -> testSyncAndAsync(Failsafe.with(timeout, fallback), () -> {
      timeoutCounter.set(0);
      fallbackCounter.set(0);
    }, () -> {
      System.out.println("Executing");
      Thread.sleep(100);
      throw new Exception();
    }, e -> {
      assertEquals(e.getAttemptCount(), 1);
      assertEquals(e.getExecutionCount(), 1);
      assertEquals(timeoutCounter.get(), 1);
      assertEquals(fallbackCounter.get(), 0);
    }, TimeoutExceededException.class);

    // Test without interrupt
    timeout.withInterrupt(false);
    test.run();

    // Test with interrupt
    timeout.withInterrupt(true);
    test.run();
  }

  /**
   * Tests that an outer timeout will interrupt an inner fallback that is blocked.
   */
  public void testFallbackThenTimeoutWithBlockedFallback() {
    AtomicInteger timeoutCounter = new AtomicInteger();
    AtomicInteger fallbackCounter = new AtomicInteger();
    Timeout<Object> timeout = Timeout.of(Duration.ofMillis(1)).onFailure(e -> {
      System.out.println("Timed out");
      timeoutCounter.incrementAndGet();
    });
    Fallback<Object> fallback = Fallback.of(() -> {
      System.out.println("Falling back");
      fallbackCounter.incrementAndGet();
      Thread.sleep(100);
      throw new IllegalStateException();
    });

    Runnable test = () -> testSyncAndAsync(Failsafe.with(timeout, fallback), () -> {
      timeoutCounter.set(0);
      fallbackCounter.set(0);
    }, () -> {
      System.out.println("Executing");
      throw new Exception();
    }, e -> {
      assertEquals(e.getAttemptCount(), 1);
      assertEquals(e.getExecutionCount(), 1);
      assertEquals(timeoutCounter.get(), 1);
      assertEquals(fallbackCounter.get(), 1);
    }, TimeoutExceededException.class);

    // Test without interrupt
    timeout.withInterrupt(false);
    test.run();

    // Test with interrupt
    timeout.withInterrupt(true);
    test.run();
  }
}
