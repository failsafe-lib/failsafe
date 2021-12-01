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

import dev.failsafe.RateLimitExceededException;
import dev.failsafe.RateLimiter;
import dev.failsafe.internal.RateLimiterStatsTest.TestStopwatch;
import dev.failsafe.testing.Testing;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.Duration;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

@Test
public class RateLimiterImplTest extends Testing {
  static RateLimiter<Object> smoothLimiter = RateLimiter.builder(Duration.ofMillis(500)).build();
  static RateLimiter<Object> burstyLimiter = RateLimiter.builder(2, Duration.ofSeconds(1)).build();
  TestStopwatch stopwatch;

  @BeforeMethod
  protected void beforeMethod() {
    stopwatch = new TestStopwatch();
  }

  public void testIsSmooth() {
    assertTrue(smoothLimiter.isSmooth());
    assertFalse(smoothLimiter.isBursty());
  }

  public void testIsBursty() {
    assertTrue(burstyLimiter.isBursty());
    assertFalse(burstyLimiter.isSmooth());
  }

  public void testAcquirePermit() {
    RateLimiterImpl<Object> limiter = new RateLimiterImpl<>(
      RateLimiter.builder(Duration.ofMillis(100)).build().getConfig(), stopwatch);

    long elapsed = timed(() -> {
      limiter.acquirePermit(); // waits 0
      limiter.acquirePermit(); // waits 100
      limiter.acquirePermit(); // waits 200
    });
    assertTrue(elapsed >= 300 && elapsed <= 400);
  }

  public void testAcquirePermitWithInterrupt() {
    RateLimiterImpl<Object> limiter = new RateLimiterImpl<>(
      RateLimiter.builder(Duration.ofMillis(1000)).build().getConfig(), stopwatch);

    Thread thread = Thread.currentThread();
    runInThread(() -> {
      Thread.sleep(100);
      thread.interrupt();
    });
    long elapsed = timed(() -> assertThrows(() -> {
      limiter.acquirePermit(); // waits 0
      limiter.acquirePermit(); // waits 1000
    }, InterruptedException.class));
    assertTrue(elapsed < 1000);
  }

  public void testAcquireWithTimeout() throws Throwable {
    RateLimiterImpl<Object> limiter = new RateLimiterImpl<>(
      RateLimiter.builder(Duration.ofMillis(100)).build().getConfig(), stopwatch);

    limiter.acquirePermit(Duration.ofMillis(100)); // waits 0
    limiter.acquirePermit(Duration.ofMillis(1000)); // waits 100
    assertThrows(() -> {
      limiter.acquirePermit(Duration.ofMillis(100)); // waits 200
    }, RateLimitExceededException.class);
  }

  public void testTryAcquirePermit() {
    RateLimiterImpl<Object> limiter = new RateLimiterImpl<>(
      RateLimiter.builder(Duration.ofMillis(100)).build().getConfig(), stopwatch);

    assertTrue(limiter.tryAcquirePermit());
    assertFalse(limiter.tryAcquirePermit());

    stopwatch.set(150);
    assertTrue(limiter.tryAcquirePermit());
    assertFalse(limiter.tryAcquirePermit());

    stopwatch.set(210);
    assertTrue(limiter.tryAcquirePermit());
    assertFalse(limiter.tryAcquirePermit());
  }

  public void testTryAcquirePermitWithTimeout() throws Throwable {
    RateLimiterImpl<Object> limiter = new RateLimiterImpl<>(
      RateLimiter.builder(Duration.ofMillis(100)).build().getConfig(), stopwatch);

    assertTrue(limiter.tryAcquirePermit(Duration.ofMillis(50)));
    assertFalse(limiter.tryAcquirePermit(Duration.ofMillis(50)));
    long elapsed = timed(() -> assertTrue(limiter.tryAcquirePermit(Duration.ofMillis(100))));
    assertTrue(elapsed >= 100 && elapsed < 200);

    stopwatch.set(200);
    assertTrue(limiter.tryAcquirePermit(Duration.ofMillis(50)));
    assertFalse(limiter.tryAcquirePermit(Duration.ofMillis(50)));
    elapsed = timed(() -> assertTrue(limiter.tryAcquirePermit(Duration.ofMillis(100))));
    assertTrue(elapsed >= 100 && elapsed < 200);
  }

  public void testTryAcquirePermitsWithTimeout() throws Throwable {
    RateLimiterImpl<Object> limiter = new RateLimiterImpl<>(
      RateLimiter.builder(Duration.ofMillis(100)).build().getConfig(), stopwatch);

    assertFalse(limiter.tryAcquirePermits(2, Duration.ofMillis(50)));
    long elapsed = timed(() -> assertTrue(limiter.tryAcquirePermits(2, Duration.ofMillis(100))));
    assertTrue(elapsed >= 100 && elapsed < 200);

    stopwatch.set(450);
    assertFalse(limiter.tryAcquirePermits(2, Duration.ofMillis(10)));
    elapsed = timed(() -> assertTrue(limiter.tryAcquirePermits(2, Duration.ofMillis(300))));
    assertTrue(elapsed >= 50 && elapsed < 150);
  }
}
