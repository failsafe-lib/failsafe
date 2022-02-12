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

import dev.failsafe.internal.RateLimiterStats.Stopwatch;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertEquals;

@Test
public abstract class RateLimiterStatsTest<T extends RateLimiterStats> {
  TestStopwatch stopwatch;

  public static class TestStopwatch extends Stopwatch {
    long currentTimeMillis;

    void set(long currentTimeMillis) {
      this.currentTimeMillis = currentTimeMillis;
    }

    @Override
    long elapsedNanos() {
      return TimeUnit.MILLISECONDS.toNanos(currentTimeMillis);
    }

    @Override
    void reset() {
      currentTimeMillis = 0;
    }
  }

  /**
   * Creates a stats with some arbitrary configuration.
   */
  abstract T createStats();

  @BeforeMethod
  protected void beforeMethod() {
    stopwatch = new TestStopwatch();
  }

  /**
   * Asserts that a single acquirePermits call yields the same result as numerous individual acquirePermits calls.
   */
  public void shouldAcquirePermitsEqually() {
    // Given
    T stats1 = createStats();
    T stats2 = createStats();

    // When making initial calls
    long singlsCallWaitMillis = acquire(stats1, 7);
    long mulipleCallsWaitMillis = acquire(stats2, 1, 7);

    // Then
    assertEquals(singlsCallWaitMillis, mulipleCallsWaitMillis);

    // Given
    stopwatch.set(2700);

    // When making additional calls
    singlsCallWaitMillis = acquire(stats1, 5);
    mulipleCallsWaitMillis = acquire(stats2, 1, 5);

    // Then
    assertEquals(singlsCallWaitMillis, mulipleCallsWaitMillis);
  }

  /**
   * Asserts that acquire on a new stats object with a single permit has zero wait time.
   */
  public void shouldHaveZeroWaitTime() {
    assertEquals(acquire(createStats(), 1), 0);
  }

  /**
   * Acquires {@code permits}, prints out info, and returns the wait time converted to millis.
   */
  long acquire(T stats, long permits) {
    return acquire(stats, permits, 1);
  }

  /**
   * Acquires {@code permits} {@code numberOfCalls} times, prints out info, and returns the wait time converted to
   * millis.
   */
  long acquire(T stats, long permits, int numberOfCalls) {
    long lastResult = 0;
    for (int i = 0; i < numberOfCalls; i++)
      lastResult = stats.acquirePermits(permits, null);
    lastResult = toMillis(lastResult);
    printInfo(stats, lastResult);
    return lastResult;
  }

  abstract void printInfo(T stats, long waitMillis);

  static long toMillis(long nanos) {
    return TimeUnit.NANOSECONDS.toMillis(nanos);
  }
}
