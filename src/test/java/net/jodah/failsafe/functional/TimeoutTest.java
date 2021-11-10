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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.testng.Assert.assertEquals;

/**
 * Tests various Timeout policy scenarios.
 */
@Test
public class TimeoutTest extends Testing {
  /**
   * Tests a simple execution that does not timeout.
   */
  public void shouldNotTimeout() {
    // Given
    Timeout<Object> timeout = Timeout.of(Duration.ofSeconds(1));

    // When / Then
    testGetSuccess(Failsafe.with(timeout), ctx -> {
      return "success";
    }, (f, e) -> {
      assertEquals(e.getAttemptCount(), 1);
      assertEquals(e.getExecutionCount(), 1);
    }, "success");
  }

  public void shouldCancelTimeoutWhenExecutionComplete() {
    // TODO
  }

  /**
   * Tests that an inner timeout does not prevent outer retries from being performed when the inner Supplier is
   * blocked.
   */
  public void testRetryTimeoutWithBlockedSupplier() {
    Stats timeoutStats = new Stats();
    Stats rpStats = new Stats();
    RetryPolicy<Object> retryPolicy = withStatsAndLogs(RetryPolicy.builder(), rpStats).build();

    Consumer<Timeout<Object>> test = timeout -> testGetSuccess(false, () -> {
      timeoutStats.reset();
      rpStats.reset();
    }, Failsafe.with(retryPolicy, timeout), ctx -> {
      if (ctx.getAttemptCount() < 2)
        Thread.sleep(100);
      return false;
    }, (f, e) -> {
      assertEquals(e.getAttemptCount(), 3);
      assertEquals(e.getExecutionCount(), 3);
      assertEquals(timeoutStats.executionCount, 3);
      assertEquals(rpStats.retryCount, 2);
    }, false);

    // Test without interrupt
    Timeout<Object> timeout = withStatsAndLogs(Timeout.builder(Duration.ofMillis(50)), timeoutStats).build();
    test.accept(timeout);

    // Test with interrupt
    timeout = withStatsAndLogs(Timeout.builder(Duration.ofMillis(50)).withInterrupt(), timeoutStats).build();
    test.accept(timeout);
  }

  /**
   * Tests that when an outer retry is scheduled any inner timeouts are cancelled. This prevents the timeout from
   * accidentally cancelling a scheduled retry that may be pending.
   */
  public void testRetryTimeoutWithPendingRetry() {
    AtomicInteger executionCounter = new AtomicInteger();
    Stats timeoutStats = new Stats();
    RetryPolicy<Object> retryPolicy = withLogs(RetryPolicy.builder().withDelay(Duration.ofMillis(100))).build();

    Consumer<Timeout<Object>> test = timeout -> testRunFailure(() -> {
      executionCounter.set(0);
      timeoutStats.reset();
    }, Failsafe.with(retryPolicy, timeout), ctx -> {
      executionCounter.incrementAndGet();
      throw new IllegalStateException();
    }, (f, e) -> {
      assertEquals(e.getAttemptCount(), 3);
      assertEquals(e.getExecutionCount(), 3);
      assertEquals(executionCounter.get(), 3);
      assertEquals(timeoutStats.successCount, 3);
      assertEquals(timeoutStats.failureCount, 0);
    }, IllegalStateException.class);

    // Test without interrupt
    Timeout<Object> timeout = withStatsAndLogs(Timeout.builder(Duration.ofMillis(100)), timeoutStats).build();
    test.accept(timeout);

    // Test with interrupt
    timeout = withStatsAndLogs(Timeout.builder(Duration.ofMillis(100)).withInterrupt(), timeoutStats).build();
    test.accept(timeout);
  }

  /**
   * Tests that an outer timeout will cancel inner retries when the inner Supplier is blocked. The flow should be:
   * <p>
   * <br>Execution that blocks
   * <br>Timeout
   */
  public void testTimeoutRetryWithBlockedSupplier() {
    AtomicInteger executionCounter = new AtomicInteger();
    Stats timeoutStats = new Stats();
    RetryPolicy<Object> retryPolicy = RetryPolicy.builder().onRetry(e -> {
      System.out.println("Retrying");
    }).build();

    Consumer<Timeout<Object>> test = timeout -> testRunFailure(false, () -> {
      executionCounter.set(0);
      timeoutStats.reset();
    }, Failsafe.with(timeout, retryPolicy), ctx -> {
      System.out.println("Executing");
      executionCounter.incrementAndGet();
      Thread.sleep(100);
      throw new Exception();
    }, (f, e) -> {
      assertEquals(e.getAttemptCount(), 1);
      assertEquals(e.getExecutionCount(), 1);
      assertEquals(executionCounter.get(), 1);
      assertEquals(timeoutStats.failureCount, 1);
    }, TimeoutExceededException.class);

    // Test without interrupt
    Timeout<Object> timeout = withStatsAndLogs(Timeout.builder(Duration.ofMillis(1)), timeoutStats).build();
    test.accept(timeout);

    // Test with interrupt
    timeout = withStatsAndLogs(Timeout.builder(Duration.ofMillis(1)).withInterrupt(), timeoutStats).build();
    test.accept(timeout);
  }

  /**
   * Tests that an outer timeout will cancel inner retries when an inner retry is pending. The flow should be:
   * <p>
   * <br>Execution
   * <br>Retry sleep/scheduled that blocks
   * <br>Timeout
   */
  public void testTimeoutRetryWithPendingRetry() {
    AtomicInteger executionCounter = new AtomicInteger();
    Stats timeoutStats = new Stats();
    Stats rpStats = new Stats();
    RetryPolicy<Object> retryPolicy = withStatsAndLogs(RetryPolicy.builder().withDelay(Duration.ofMillis(1000)),
      rpStats).build();

    Consumer<Timeout<Object>> test = timeout -> testRunFailure(false, () -> {
      executionCounter.set(0);
      timeoutStats.reset();
      rpStats.reset();
    }, Failsafe.with(timeout).compose(retryPolicy), ctx -> {
      System.out.println("Executing");
      executionCounter.incrementAndGet();
      throw new Exception();
    }, (f, e) -> {
      assertEquals(e.getAttemptCount(), 1);
      assertEquals(e.getExecutionCount(), 1);
      assertEquals(executionCounter.get(), 1);
      assertEquals(timeoutStats.failureCount, 1);
      assertEquals(rpStats.failedAttemptCount, 1);
    }, TimeoutExceededException.class);

    // Test without interrupt
    Timeout<Object> timeout = withStatsAndLogs(Timeout.builder(Duration.ofMillis(100)), timeoutStats).build();
    test.accept(timeout);

    // Test with interrupt
    timeout = withStatsAndLogs(Timeout.builder(Duration.ofMillis(100)).withInterrupt(), timeoutStats).build();
    test.accept(timeout);
  }

  /**
   * Tests an inner timeout that fires while the supplier is blocked.
   */
  public void testFallbackTimeoutWithBlockedSupplier() {
    Stats timeoutStats = new Stats();
    Stats fbStats = new Stats();
    Fallback<Object> fallback = withStatsAndLogs(Fallback.builder(() -> {
      System.out.println("Falling back");
      throw new IllegalStateException();
    }), fbStats).build();

    Consumer<Timeout<Object>> test = timeout -> testRunFailure(false, () -> {
      timeoutStats.reset();
      fbStats.reset();
    }, Failsafe.with(fallback).compose(timeout), ctx -> {
      System.out.println("Executing");
      Thread.sleep(100);
      throw new Exception();
    }, (f, e) -> {
      assertEquals(e.getAttemptCount(), 1);
      assertEquals(e.getExecutionCount(), 1);
      assertEquals(timeoutStats.failureCount, 1);
      assertEquals(fbStats.executionCount, 1);
    }, IllegalStateException.class);

    // Test without interrupt
    Timeout<Object> timeout = withStatsAndLogs(Timeout.builder(Duration.ofMillis(1)), timeoutStats).build();
    test.accept(timeout);

    // Test with interrupt
    timeout = withStatsAndLogs(Timeout.builder(Duration.ofMillis(1)).withInterrupt(), timeoutStats).build();
    test.accept(timeout);
  }

  /**
   * Tests that an inner timeout will not interrupt an outer fallback. The inner timeout is never triggered since the
   * supplier completes immediately.
   */
  public void testFallbackTimeout() {
    Stats timeoutStats = new Stats();
    Stats fbStats = new Stats();
    Fallback<Object> fallback = withStatsAndLogs(Fallback.builder(() -> {
      System.out.println("Falling back");
      throw new IllegalStateException();
    }), fbStats).build();

    Consumer<Timeout<Object>> test = timeout -> testRunFailure(() -> {
      timeoutStats.reset();
      fbStats.reset();
    }, Failsafe.with(fallback).compose(timeout), ctx -> {
      System.out.println("Executing");
      throw new Exception();
    }, (f, e) -> {
      assertEquals(e.getAttemptCount(), 1);
      assertEquals(e.getExecutionCount(), 1);
      assertEquals(timeoutStats.failureCount, 0);
      assertEquals(fbStats.executionCount, 1);
    }, IllegalStateException.class);

    // Test without interrupt
    Timeout<Object> timeout = withStatsAndLogs(Timeout.builder(Duration.ofMillis(100)), timeoutStats).build();
    test.accept(timeout);

    // Test with interrupt
    timeout = withStatsAndLogs(Timeout.builder(Duration.ofMillis(100)).withInterrupt(), timeoutStats).build();
    test.accept(timeout);
  }

  /**
   * Tests that an outer timeout will interrupt an inner supplier that is blocked, skipping the inner fallback.
   */
  public void testTimeoutFallbackWithBlockedSupplier() {
    Stats timeoutStats = new Stats();
    Stats fbStats = new Stats();
    Fallback<Object> fallback = withStatsAndLogs(Fallback.builder(() -> {
      System.out.println("Falling back");
      throw new IllegalStateException();
    }), fbStats).build();

    Consumer<Timeout<Object>> test = timeout -> testRunFailure(false, () -> {
      timeoutStats.reset();
      fbStats.reset();
    }, Failsafe.with(timeout).compose(fallback), ctx -> {
      System.out.println("Executing");
      Thread.sleep(100);
      throw new Exception();
    }, (f, e) -> {
      assertEquals(e.getAttemptCount(), 1);
      assertEquals(e.getExecutionCount(), 1);
      assertEquals(timeoutStats.failureCount, 1);
      assertEquals(fbStats.executionCount, 0);
    }, TimeoutExceededException.class);

    // Test without interrupt
    Timeout<Object> timeout = withStatsAndLogs(Timeout.builder(Duration.ofMillis(1)), timeoutStats).build();
    test.accept(timeout);

    // Test with interrupt
    timeout = withStatsAndLogs(Timeout.builder(Duration.ofMillis(1)).withInterrupt(), timeoutStats).build();
    test.accept(timeout);
  }

  /**
   * Tests that an outer timeout will interrupt an inner fallback that is blocked.
   */
  public void testTimeoutFallbackWithBlockedFallback() {
    Stats timeoutStats = new Stats();
    Stats fbStats = new Stats();
    Fallback<Object> fallback = withStatsAndLogs(Fallback.builder(() -> {
      System.out.println("Falling back");
      Thread.sleep(200);
      throw new IllegalStateException();
    }), fbStats).build();

    Consumer<Timeout<Object>> test = timeout -> testRunFailure(false, () -> {
      timeoutStats.reset();
      fbStats.reset();
    }, Failsafe.with(timeout).compose(fallback), ctx -> {
      System.out.println("Executing");
      throw new Exception();
    }, (f, e) -> {
      assertEquals(e.getAttemptCount(), 1);
      assertEquals(e.getExecutionCount(), 1);
      assertEquals(timeoutStats.failureCount, 1);
      assertEquals(fbStats.executionCount, 1);
    }, TimeoutExceededException.class);

    // Test without interrupt
    Timeout<Object> timeout = withStatsAndLogs(Timeout.builder(Duration.ofMillis(100)), timeoutStats).build();
    test.accept(timeout);

    // Test with interrupt
    timeout = withStatsAndLogs(Timeout.builder(Duration.ofMillis(100)).withInterrupt(), timeoutStats).build();
    test.accept(timeout);
  }
}
