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
package dev.failsafe.internal;

import dev.failsafe.RateLimiter;
import dev.failsafe.RateLimiterConfig;
import org.testng.annotations.Test;

import java.time.Duration;

import static org.testng.Assert.assertEquals;

@Test
public class SmoothRateLimiterStatsTest extends RateLimiterStatsTest<SmoothRateLimiterStats> {
  @Override
  SmoothRateLimiterStats createStats() {
    RateLimiterConfig<Object> config = RateLimiter.smoothBuilder(Duration.ofMillis(500)).build().getConfig();
    return new SmoothRateLimiterStats(config, stopwatch);
  }

  SmoothRateLimiterStats createStats(Duration rate) {
    RateLimiterConfig<Object> config = RateLimiter.smoothBuilder(rate).build().getConfig();
    return new SmoothRateLimiterStats(config, stopwatch);
  }

  /**
   * Asserts that wait times and available permits are expected, over time, when calling acquirePermits.
   */
  public void testAcquirePermits() {
    // Given 1 per mit every 500 millis
    SmoothRateLimiterStats stats = createStats(Duration.ofMillis(500));

    long waitMillis = acquire(stats, 1, 7);
    assertResults(stats, waitMillis, 3000, 3500);

    stopwatch.set(800);
    waitMillis = acquire(stats, 3);
    assertResults(stats, waitMillis, 3700, 5000);

    stopwatch.set(2300);
    waitMillis = acquire(stats, 1);
    assertResults(stats, waitMillis, 2700, 5500);

    stopwatch.set(3500);
    waitMillis = acquire(stats, 3);
    assertResults(stats, waitMillis, 3000, 7000);

    stopwatch.set(9100);
    waitMillis = acquire(stats, 3);
    assertResults(stats, waitMillis, 900, 10500);

    stopwatch.set(11000);
    waitMillis = acquire(stats, 1);
    assertResults(stats, waitMillis, 0, 11500);

    // Given 1 permit every 200 millis
    stopwatch = new TestStopwatch();
    stats = createStats(Duration.ofMillis(200));

    waitMillis = acquire(stats, 3);
    assertResults(stats, waitMillis, 400, 600);

    stopwatch.set(550);
    waitMillis = acquire(stats, 2);
    assertResults(stats, waitMillis, 250, 1000);

    stopwatch.set(2210);
    waitMillis = acquire(stats, 2);
    assertResults(stats, waitMillis, 190, 2600);
  }

  public void testAcquireInitialStats() {
    // Given 1 permit every 100 millis
    SmoothRateLimiterStats stats = createStats(Duration.ofMillis(100));

    assertEquals(toMillis(stats.acquirePermits(1, null)), 0);
    assertEquals(toMillis(stats.acquirePermits(1, null)), 100);
    stopwatch.set(100);
    assertEquals(toMillis(stats.acquirePermits(1, null)), 100);
    assertEquals(toMillis(stats.acquirePermits(1, null)), 200);

    // Given 1 permit every 100 millis
    stats = createStats(Duration.ofMillis(100));
    stopwatch.set(0);
    assertEquals(toMillis(stats.acquirePermits(1, null)), 0);
    stopwatch.set(150);
    assertEquals(toMillis(stats.acquirePermits(1, null)), 0);
    stopwatch.set(250);
    assertEquals(toMillis(stats.acquirePermits(2, null)), 50);
  }

  private static void assertResults(SmoothRateLimiterStats stats, long waitMillis, long expectedWaitMillis,
    long expectedNextFreePermitMillis) {
    assertEquals(waitMillis, expectedWaitMillis);
    assertEquals(toMillis(stats.getNextFreePermitNanos()), expectedNextFreePermitMillis);

    // Asserts that the nextFreePermitNanos makes sense relative to the elapsedNanos and waitMillis
    long computedNextFreePermitMillis =
      toMillis(stats.stopwatch.elapsedNanos()) + waitMillis + toMillis(stats.intervalNanos);
    assertEquals(toMillis(stats.getNextFreePermitNanos()), computedNextFreePermitMillis);
  }

  @Override
  void printInfo(SmoothRateLimiterStats stats, long waitMillis) {
    System.out.printf("[%s] elapsedMillis: %4s, waitMillis: %s, nextFreePermitNanos: %s%n",
      Thread.currentThread().getName(), stats.getElapsed().toMillis(), waitMillis,
      toMillis(stats.getNextFreePermitNanos()));
  }
}
