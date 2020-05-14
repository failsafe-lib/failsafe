package net.jodah.failsafe.internal;

import org.testng.annotations.Test;

import java.util.Arrays;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

@Test
public class CountingCircuitStatsTest extends CircuitStatsTest {
  CountingCircuitStats stats;

  public void shouldReturnUnitializedValues() {
    stats = new CountingCircuitStats(100, null);
    for (int i = 0; i < 100; i++) {
      assertEquals(stats.setNext(true), -1);
    }

    assertEquals(stats.setNext(true), 1);
    assertEquals(stats.setNext(true), 1);
  }

  public void testMetrics() {
    stats = new CountingCircuitStats(100, null);
    assertEquals(stats.getSuccessRate(), 0);
    assertEquals(stats.getFailureRate(), 0);
    assertEquals(stats.getExecutionCount(), 0);

    recordExecutions(stats, 50, i -> i % 3 == 0);

    assertEquals(stats.getSuccessCount(), 17);
    assertEquals(stats.getSuccessRate(), 34);
    assertEquals(stats.getFailureCount(), 33);
    assertEquals(stats.getFailureRate(), 66);
    assertEquals(stats.getExecutionCount(), 50);

    recordSuccesses(stats, 100);

    assertEquals(stats.getSuccessCount(), 100);
    assertEquals(stats.getSuccessRate(), 100);
    assertEquals(stats.getFailureCount(), 0);
    assertEquals(stats.getFailureRate(), 0);
    assertEquals(stats.getExecutionCount(), 100);
  }

  public void testCopyToEqualSizedStats() {
    stats = new CountingCircuitStats(5, null);
    recordSuccesses(stats, 2);
    recordFailures(stats, 3);

    stats.currentIndex = 0;
    CountingCircuitStats right = new CountingCircuitStats(5, stats);
    assertValues(right, true, true, false, false, false);

    stats.currentIndex = 2;
    right = new CountingCircuitStats(5, stats);
    assertValues(right, false, false, false, true, true);

    stats.currentIndex = 4;
    right = new CountingCircuitStats(5, stats);
    assertValues(right, false, true, true, false, false);
  }

  public void testCopyToSmallerStats() {
    stats = new CountingCircuitStats(10, null);
    recordSuccesses(stats, 5);
    recordFailures(stats, 5);

    stats.currentIndex = 0;
    CountingCircuitStats right = new CountingCircuitStats(4, stats);
    assertValues(right, false, false, false, false);

    stats.currentIndex = 2;
    right = new CountingCircuitStats(4, stats);
    assertValues(right, false, false, true, true);

    stats.currentIndex = 7;
    right = new CountingCircuitStats(4, stats);
    assertValues(right, true, true, false, false);
  }

  public void testCopyToLargerStats() {
    stats = new CountingCircuitStats(5, null);
    recordSuccesses(stats, 2);
    recordFailures(stats, 3);

    stats.currentIndex = 0;
    CountingCircuitStats right = new CountingCircuitStats(6, stats);
    assertValues(right, true, true, false, false, false);

    stats.currentIndex = 2;
    right = new CountingCircuitStats(6, stats);
    assertValues(right, false, false, false, true, true);

    stats.currentIndex = 4;
    right = new CountingCircuitStats(6, stats);
    assertValues(right, false, true, true, false, false);
  }

  private static boolean[] valuesFor(CountingCircuitStats stats) {
    boolean[] values = new boolean[stats.getExecutionCount()];
    for (int i = 0; i < values.length; i++)
      values[i] = stats.bitSet.get(i);
    return values;
  }

  private static void assertValues(CountingCircuitStats bs, boolean... right) {
    boolean[] left = valuesFor(bs);
    assertTrue(Arrays.equals(left, right), Arrays.toString(left) + " != " + Arrays.toString(right));
  }
}
