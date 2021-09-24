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

import static org.testng.Assert.assertEquals;

/**
 * Tests various Timeout policy scenarios.
 */
@Test
public class TimeoutTest extends Testing {
  public void shouldCancelTimeoutWhenExecutionComplete() {
    // TODO
  }

  /**
   * Tests that an inner timeout does not prevent outer retries from being performed when the inner Supplier is
   * blocked.
   */
  public void testTimeoutThenRetryWithBlockedSupplier() {
    Stats timeoutStats = new Stats();
    Stats rpStats = new Stats();
    Timeout<Object> timeout = withStatsAndLogs(Timeout.of(Duration.ofMillis(1)), timeoutStats);
    RetryPolicy<Object> retryPolicy = withStatsAndLogs(new RetryPolicy<>().withMaxRetries(2), rpStats);

    Runnable test = () -> testRunFailure(() -> {
      timeoutStats.reset();
      rpStats.reset();
    }, Failsafe.with(retryPolicy, timeout), ctx -> {
      Thread.sleep(100);
      throw new Exception();
    }, e -> {
      assertEquals(e.getAttemptCount(), 3);
      assertEquals(e.getExecutionCount(), 3);
      assertEquals(timeoutStats.executionCount, 3);
      assertEquals(rpStats.retryCount, 2);
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
    Stats timeoutStats = new Stats();
    Timeout<Object> timeout = withStatsAndLogs(Timeout.of(Duration.ofMillis(100)), timeoutStats);
    RetryPolicy<Object> retryPolicy = withLogs(new RetryPolicy<>().withDelay(Duration.ofMillis(100)));

    Runnable test = () -> testRunFailure(() -> {
      executionCounter.set(0);
      timeoutStats.reset();
    }, Failsafe.with(retryPolicy, timeout), ctx -> {
      executionCounter.incrementAndGet();
      throw new IllegalStateException();
    }, e -> {
      assertEquals(e.getAttemptCount(), 3);
      assertEquals(e.getExecutionCount(), 3);
      assertEquals(executionCounter.get(), 3);
      assertEquals(timeoutStats.successCount, 3);
      assertEquals(timeoutStats.failureCount, 0);
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
    AtomicInteger executionCounter = new AtomicInteger();
    Stats timeoutStats = new Stats();
    Timeout<Object> timeout = withStatsAndLogs(Timeout.of(Duration.ofMillis(1)), timeoutStats);
    RetryPolicy<Object> retryPolicy = new RetryPolicy<>().onRetry(e -> {
      System.out.println("Retrying");
    });

    Runnable test = () -> testRunFailure(() -> {
      executionCounter.set(0);
      timeoutStats.reset();
    }, Failsafe.with(timeout, retryPolicy), ctx -> {
      System.out.println("Executing");
      executionCounter.incrementAndGet();
      Thread.sleep(100);
      throw new Exception();
    }, e -> {
      assertEquals(e.getAttemptCount(), 1);
      assertEquals(e.getExecutionCount(), 1);
      assertEquals(executionCounter.get(), 1);
      assertEquals(timeoutStats.failureCount, 1);
    }, TimeoutExceededException.class);

    // Test without interrupt
    timeout.withInterrupt(false);
    test.run();

    // Test with interrupt
    timeout.withInterrupt(true);
    test.run();
  }

  public void testTimeoutDuringExecution() {
    Timeout<Object> timeout = withLogs(Timeout.of(Duration.ofMillis(100)));
    RetryPolicy<Object> retryPolicy = withLogs(new RetryPolicy<>().withDelay(Duration.ofMillis(1000)));

    Asserts.assertThrows(() -> Failsafe.with(timeout, retryPolicy).run(() -> {
      Thread.sleep(1000);
      throw new Exception();
    }), TimeoutExceededException.class);
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
    Stats timeoutStats = new Stats();
    Stats rpStats = new Stats();
    Timeout<Object> timeout = withStatsAndLogs(Timeout.of(Duration.ofMillis(100)), timeoutStats);
    RetryPolicy<Object> retryPolicy = withStatsAndLogs(new RetryPolicy<>().withDelay(Duration.ofMillis(1000)), rpStats);

    Runnable test = () -> testRunFailure(() -> {
      executionCounter.set(0);
      timeoutStats.reset();
      rpStats.reset();
    }, Failsafe.with(timeout, retryPolicy), ctx -> {
      System.out.println("Executing");
      executionCounter.incrementAndGet();
      throw new Exception();
    }, e -> {
      assertEquals(e.getAttemptCount(), 1);
      assertEquals(e.getExecutionCount(), 1);
      assertEquals(executionCounter.get(), 1);
      assertEquals(timeoutStats.failureCount, 1);
      assertEquals(rpStats.failedAttemptCount, 1);
    }, TimeoutExceededException.class);

    // Test without interrupt
    test.run();

    // Test with interrupt
    timeout.withInterrupt(true);
    test.run();
  }

  /**
   * Tests an inner timeout that fires while the supplier is blocked.
   */
  public void testTimeoutThenFallbackWithBlockedSupplier() {
    Stats timeoutStats = new Stats();
    Stats fbStats = new Stats();
    Timeout<Object> timeout = withStatsAndLogs(Timeout.of(Duration.ofMillis(1)), timeoutStats);
    Fallback<Object> fallback = withStatsAndLogs(Fallback.of(() -> {
      System.out.println("Falling back");
      throw new IllegalStateException();
    }), fbStats);

    Runnable test = () -> testRunFailure(() -> {
      timeoutStats.reset();
      fbStats.reset();
    }, Failsafe.with(fallback, timeout), ctx -> {
      System.out.println("Executing");
      Thread.sleep(100);
      throw new Exception();
    }, e -> {
      assertEquals(e.getAttemptCount(), 1);
      assertEquals(e.getExecutionCount(), 1);
      assertEquals(timeoutStats.failureCount, 1);
      assertEquals(fbStats.executionCount, 1);
    }, IllegalStateException.class);

    // Test without interrupt
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
    Stats timeoutStats = new Stats();
    Stats fbStats = new Stats();
    Timeout<Object> timeout = withStatsAndLogs(Timeout.of(Duration.ofMillis(100)), timeoutStats);
    Fallback<Object> fallback = withStatsAndLogs(Fallback.of(() -> {
      System.out.println("Falling back");
      throw new IllegalStateException();
    }), fbStats);

    Runnable test = () -> testRunFailure(() -> {
      timeoutStats.reset();
      fbStats.reset();
    }, Failsafe.with(fallback, timeout), ctx -> {
      System.out.println("Executing");
      throw new Exception();
    }, e -> {
      assertEquals(e.getAttemptCount(), 1);
      assertEquals(e.getExecutionCount(), 1);
      assertEquals(timeoutStats.failureCount, 0);
      assertEquals(fbStats.executionCount, 1);
    }, IllegalStateException.class);

    // Test without interrupt
    test.run();

    // Test with interrupt
    timeout.withInterrupt(true);
    test.run();
  }

  /**
   * Tests that an outer timeout will interrupt an inner supplier that is blocked, skipping the inner fallback.
   */
  public void testFallbackThenTimeoutWithBlockedSupplier() {
    Stats timeoutStats = new Stats();
    Stats fbStats = new Stats();
    Timeout<Object> timeout = withStatsAndLogs(Timeout.of(Duration.ofMillis(1)), timeoutStats);
    Fallback<Object> fallback = withStatsAndLogs(Fallback.of(() -> {
      System.out.println("Falling back");
      throw new IllegalStateException();
    }), fbStats);

    Runnable test = () -> testRunFailure(() -> {
      timeoutStats.reset();
      fbStats.reset();
    }, Failsafe.with(timeout, fallback), ctx -> {
      System.out.println("Executing");
      Thread.sleep(100);
      throw new Exception();
    }, e -> {
      assertEquals(e.getAttemptCount(), 1);
      assertEquals(e.getExecutionCount(), 1);
      assertEquals(timeoutStats.failureCount, 1);
      assertEquals(fbStats.executionCount, 0);
    }, TimeoutExceededException.class);

    // Test without interrupt
    test.run();

    // Test with interrupt
    timeout.withInterrupt(true);
    test.run();
  }

  /**
   * Tests that an outer timeout will interrupt an inner fallback that is blocked.
   */
  public void testFallbackThenTimeoutWithBlockedFallback() {
    Stats timeoutStats = new Stats();
    Stats fbStats = new Stats();
    Timeout<Object> timeout = withStatsAndLogs(Timeout.of(Duration.ofMillis(100)), timeoutStats);
    Fallback<Object> fallback = withStatsAndLogs(Fallback.of(() -> {
      System.out.println("Falling back");
      Thread.sleep(200);
      throw new IllegalStateException();
    }), fbStats);

    Runnable test = () -> testRunFailure(() -> {
      timeoutStats.reset();
      fbStats.reset();
    }, Failsafe.with(timeout, fallback), ctx -> {
      System.out.println("Executing");
      throw new Exception();
    }, e -> {
      assertEquals(e.getAttemptCount(), 1);
      assertEquals(e.getExecutionCount(), 1);
      assertEquals(timeoutStats.failureCount, 1);
      assertEquals(fbStats.executionCount, 1);
    }, TimeoutExceededException.class);

    // Test without interrupt
    test.run();

    // Test with interrupt
    timeout.withInterrupt(true);
    test.run();
  }
}
