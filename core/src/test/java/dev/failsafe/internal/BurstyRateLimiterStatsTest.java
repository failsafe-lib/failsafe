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
public class BurstyRateLimiterStatsTest extends RateLimiterStatsTest<BurstyRateLimiterStats> {
  @Override
  BurstyRateLimiterStats createStats() {
    RateLimiterConfig<Object> config = RateLimiter.burstyBuilder(2, Duration.ofSeconds(1)).build().getConfig();
    return new BurstyRateLimiterStats(config, stopwatch);
  }

  BurstyRateLimiterStats createStats(long maxPermits, Duration period) {
    RateLimiterConfig<Object> config = RateLimiter.burstyBuilder(maxPermits, period).build().getConfig();
    return new BurstyRateLimiterStats(config, stopwatch);
  }

  /**
   * Asserts that wait times and available permits are expected, over time, when calling acquirePermits.
   */
  public void testAcquirePermits() {
    // Given 2 max permits per second
    BurstyRateLimiterStats stats = createStats(2, Duration.ofSeconds(1));

    assertEquals(acquire(stats, 1, 7), 3000);
    assertEquals(stats.getAvailablePermits(), -5);
    assertEquals(stats.getCurrentPeriod(), 0);

    stopwatch.set(800);
    assertEquals(acquire(stats, 3), 3200);
    assertEquals(stats.getAvailablePermits(), -8);
    assertEquals(stats.getCurrentPeriod(), 0);

    stopwatch.set(2300);
    assertEquals(acquire(stats, 1), 2700);
    assertEquals(stats.getAvailablePermits(), -5);
    assertEquals(stats.getCurrentPeriod(), 2);

    stopwatch.set(3500);
    assertEquals(acquire(stats, 1, 3), 2500);
    assertEquals((stats.getAvailablePermits()), -6);
    assertEquals(stats.getCurrentPeriod(), 3);

    stopwatch.set(7000);
    assertEquals(acquire(stats, 1), 0);
    assertEquals((stats.getAvailablePermits()), 1);
    assertEquals(stats.getCurrentPeriod(), 7);

    // Given 5 max permits per second
    stopwatch = new TestStopwatch();
    stats = createStats(5, Duration.ofSeconds(1));

    stopwatch.set(300);
    assertEquals(acquire(stats, 3), 0);
    assertEquals(stats.getAvailablePermits(), 2);
    assertEquals(stats.getCurrentPeriod(), 0);

    stopwatch.set(1550);
    assertEquals(acquire(stats, 10), 450);
    assertEquals(stats.getAvailablePermits(), -5);
    assertEquals(stats.getCurrentPeriod(), 1);

    stopwatch.set(2210);
    assertEquals(acquire(stats, 2), 790); // Must wait till next period
    assertEquals(stats.getAvailablePermits(), -2);
    assertEquals(stats.getCurrentPeriod(), 2);
  }

  @Override
  void printInfo(BurstyRateLimiterStats stats, long waitMillis) {
    System.out.printf("[%s] elapsedMillis: %5s, availablePermits: %2s, currentPeriod: %s, waitMillis: %s%n",
      Thread.currentThread().getName(), stats.getElapsed().toMillis(), stats.getAvailablePermits(),
      stats.getCurrentPeriod(), waitMillis);
  }
}
