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
package dev.failsafe.internal;

import dev.failsafe.internal.TimedCircuitStats.Bucket;
import dev.failsafe.internal.TimedCircuitStats.Clock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.Arrays;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

@Test
public class TimedCircuitStatsTest extends CircuitStatsTest {
  TimedCircuitStats stats;
  private TestClock clock;

  static class TestClock extends Clock {
    long currentTimeMillis;

    void set(long currentTimeMillis) {
      this.currentTimeMillis = currentTimeMillis;
    }

    @Override
    long currentTimeMillis() {
      return currentTimeMillis;
    }

    @Override
    public String toString() {
      return "TestClock[currentTimeMillis=" + currentTimeMillis + ']';
    }
  }

  @BeforeMethod
  protected void beforeMethod() {
    clock = new TestClock();
  }

  public void testMetrics() {
    // Given 4 buckets representing 1 second each
    stats = new TimedCircuitStats(4, Duration.ofSeconds(4), clock, null);
    assertEquals(stats.getSuccessRate(), 0);
    assertEquals(stats.getFailureRate(), 0);
    assertEquals(stats.getExecutionCount(), 0);

    // Record into bucket 1
    recordExecutions(stats, 50, i -> i % 5 == 0); // currentTime = 0
    assertEquals(stats.currentIndex, 0);
    assertEquals(stats.getCurrentBucket().startTimeMillis, 0);
    assertEquals(stats.getSuccessCount(), 10);
    assertEquals(stats.getSuccessRate(), 20);
    assertEquals(stats.getFailureCount(), 40);
    assertEquals(stats.getFailureRate(), 80);
    assertEquals(stats.getExecutionCount(), 50);

    // Record into bucket 2
    clock.set(1000);
    recordSuccesses(stats, 10);
    assertEquals(stats.currentIndex, 1);
    assertEquals(stats.getCurrentBucket().startTimeMillis, 1000);
    assertEquals(stats.getSuccessCount(), 20);
    assertEquals(stats.getSuccessRate(), 33);
    assertEquals(stats.getFailureCount(), 40);
    assertEquals(stats.getFailureRate(), 67);
    assertEquals(stats.getExecutionCount(), 60);

    // Record into bucket 3
    clock.set(2500);
    recordFailures(stats, 20);
    assertEquals(stats.currentIndex, 2);
    assertEquals(stats.getCurrentBucket().startTimeMillis, 2000);
    assertEquals(stats.getSuccessCount(), 20);
    assertEquals(stats.getSuccessRate(), 25);
    assertEquals(stats.getFailureCount(), 60);
    assertEquals(stats.getFailureRate(), 75);
    assertEquals(stats.getExecutionCount(), 80);

    // Record into bucket 4
    clock.set(3100);
    recordExecutions(stats, 25, i -> i % 5 == 0);
    assertEquals(stats.currentIndex, 3);
    assertEquals(stats.getCurrentBucket().startTimeMillis, 3000);
    assertEquals(stats.getSuccessCount(), 25);
    assertEquals(stats.getSuccessRate(), 24);
    assertEquals(stats.getFailureCount(), 80);
    assertEquals(stats.getFailureRate(), 76);
    assertEquals(stats.getExecutionCount(), 105);

    // Record into bucket 2, skipping bucket 1
    clock.set(5400);
    recordSuccesses(stats, 8);
    assertEquals(stats.currentIndex, 1);
    // Assert bucket 1 was skipped and reset based on its previous start time
    Bucket bucket1 = stats.buckets[0];
    assertEquals(bucket1.startTimeMillis, 4000);
    assertEquals(bucket1.successes, 0);
    assertEquals(bucket1.failures, 0);
    assertEquals(stats.getCurrentBucket().startTimeMillis, 5000);
    assertEquals(stats.getSuccessCount(), 13);
    assertEquals(stats.getSuccessRate(), 25);
    assertEquals(stats.getFailureCount(), 40);
    assertEquals(stats.getFailureRate(), 75);
    assertEquals(stats.getExecutionCount(), 53);

    // Record into bucket 4, skipping bucket 3
    clock.set(7300);
    recordFailures(stats, 5);
    assertEquals(stats.currentIndex, 3);
    // Assert bucket 3 was skipped and reset based on its previous start time
    Bucket bucket3 = stats.buckets[2];
    assertEquals(bucket3.startTimeMillis, 6000);
    assertEquals(bucket3.successes, 0);
    assertEquals(bucket3.failures, 0);
    assertEquals(stats.getCurrentBucket().startTimeMillis, 7000);
    assertEquals(stats.getSuccessCount(), 8);
    assertEquals(stats.getSuccessRate(), 62);
    assertEquals(stats.getFailureCount(), 5);
    assertEquals(stats.getFailureRate(), 38);
    assertEquals(stats.getExecutionCount(), 13);

    // Skip all buckets, starting at 1 again
    int startTime = 22500;
    clock.set(startTime);
    stats.getCurrentBucket();
    assertEquals(stats.currentIndex, 0);
    for (Bucket bucket : stats.buckets) {
      assertEquals(bucket.startTimeMillis, startTime);
      assertEquals(bucket.successes, 0);
      assertEquals(bucket.failures, 0);
      startTime += 1000;
    }
    assertEquals(stats.getSuccessRate(), 0);
    assertEquals(stats.getFailureRate(), 0);
    assertEquals(stats.getExecutionCount(), 0);
  }

  public void testCopyToEqualSizedStats() {
    stats = new TimedCircuitStats(4, Duration.ofSeconds(4), clock, null);
    assertValues(stats, b(0, 0, 0), b(-1, 0, 0), b(-1, 0, 0), b(-1, 0, 0));
    recordSuccesses(stats, 2);
    clock.set(1100);
    recordFailures(stats, 3);
    assertEquals(stats.currentIndex, 1);

    TimedCircuitStats right1 = new TimedCircuitStats(4, Duration.ofSeconds(4), clock, stats);
    assertValues(right1, b(-1, 0, 0), b(-1, 0, 0), b(0, 2, 0), b(1000, 0, 3));
    assertEquals(right1.currentIndex, 3);
    clock.set(2500);
    recordSuccesses(right1, 5);
    assertValues(right1, b(2000, 5, 0), b(-1, 0, 0), b(0, 2, 0), b(1000, 0, 3));
    assertEquals(right1.currentIndex, 0);

    clock.set(2200);
    recordSuccesses(stats, 2);
    clock.set(3300);
    recordFailures(stats, 3);
    assertEquals(stats.currentIndex, 3);

    TimedCircuitStats right2 = new TimedCircuitStats(4, Duration.ofSeconds(4), clock, stats);
    assertValues(right2, b(0, 2, 0), b(1000, 0, 3), b(2000, 2, 0), b(3000, 0, 3));
    assertEquals(right2.currentIndex, 3);
    clock.set(4400);
    recordSuccesses(right2, 4);
    assertValues(right2, b(4000, 4, 0), b(1000, 0, 3), b(2000, 2, 0), b(3000, 0, 3));
    assertEquals(right2.currentIndex, 0);

    TimedCircuitStats right3 = new TimedCircuitStats(4, Duration.ofSeconds(4), clock, right2);
    assertValues(right3, b(1000, 0, 3), b(2000, 2, 0), b(3000, 0, 3), b(4000, 4, 0));
    assertEquals(right3.currentIndex, 3);
    clock.set(7500);
    recordExecutions(right3, 4, i -> i % 2 == 0);
    assertValues(right3, b(5000, 0, 0), b(6000, 0, 0), b(7000, 2, 2), b(4000, 4, 0));
    assertEquals(right3.currentIndex, 2);
  }

  public void testCopyToSmallerStats() {
    stats = new TimedCircuitStats(5, Duration.ofSeconds(5), clock, null);
    recordSuccesses(stats, 2);
    clock.set(1100);
    recordFailures(stats, 3);
    clock.set(2200);
    recordSuccesses(stats, 4);
    clock.set(3300);
    recordFailures(stats, 5);
    clock.set(4400);
    recordFailures(stats, 6);

    TimedCircuitStats right1 = new TimedCircuitStats(3, Duration.ofSeconds(3), clock, stats);
    assertValues(right1, b(2000, 4, 0), b(3000, 0, 5), b(4000, 0, 6));
    assertEquals(right1.currentIndex, 2);
    clock.set(6500);
    recordSuccesses(right1, 33);
    assertValues(right1, b(5000, 0, 0), b(6000, 33, 0), b(4000, 0, 6));
    assertEquals(right1.currentIndex, 1);
  }

  public void testCopyToLargerStats() {
    stats = new TimedCircuitStats(3, Duration.ofSeconds(3), clock, null);
    recordSuccesses(stats, 2);
    clock.set(1100);
    recordFailures(stats, 3);
    clock.set(2200);
    recordSuccesses(stats, 4);

    TimedCircuitStats right1 = new TimedCircuitStats(5, Duration.ofSeconds(5), clock, stats);
    assertValues(right1, b(0, 2, 0), b(1000, 0, 3), b(2000, 4, 0), b(-1, 0, 0), b(-1, 0, 0));
    assertEquals(right1.currentIndex, 2);
    clock.set(3300);
    recordSuccesses(right1, 22);
    assertValues(right1, b(0, 2, 0), b(1000, 0, 3), b(2000, 4, 0), b(3000, 22, 0), b(-1, 0, 0));
    assertEquals(right1.currentIndex, 3);

    TimedCircuitStats right2 = new TimedCircuitStats(6, Duration.ofSeconds(6), clock, right1);
    assertValues(right2, b(-1, 0, 0), b(0, 2, 0), b(1000, 0, 3), b(2000, 4, 0), b(3000, 22, 0), b(-1, 0, 0));
    assertEquals(right2.currentIndex, 4);
    clock.set(7250);
    recordFailures(right2, 123);
    assertValues(right2, b(5000, 0, 0), b(6000, 0, 0), b(7000, 0, 123), b(2000, 4, 0), b(3000, 22, 0), b(4000, 0, 0));
    assertEquals(right2.currentIndex, 2);
  }

  /**
   * Accepts a [startTime, successCount, failureCount] tuple.
   */
  private static int[] b(int... values) {
    return new int[] { values[0], values[1], values[2] };
  }

  private static int[][] valuesFor(TimedCircuitStats stats) {
    int[][] values = new int[stats.buckets.length][];
    for (int i = 0; i < values.length; i++)
      values[i] = b((int) stats.buckets[i].startTimeMillis, stats.buckets[i].successes, stats.buckets[i].failures);
    return values;
  }

  private static void assertValues(TimedCircuitStats stats, int[]... right) {
    int[][] left = valuesFor(stats);
    assertTrue(Arrays.deepEquals(left, right), Arrays.deepToString(left) + " != " + Arrays.deepToString(right));
  }
}
