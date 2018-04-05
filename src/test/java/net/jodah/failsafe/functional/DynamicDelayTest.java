/*
 * Copyright 2016 the original author or authors.
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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.Test;

import net.jodah.failsafe.ExecutionContext;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.util.Duration;

@Test
public class DynamicDelayTest {
  static class UncheckedExpectedException extends RuntimeException {
  }

  static class DelayException extends UncheckedExpectedException {
  }

  @Test(expectedExceptions = NullPointerException.class)
  public void testNullDelayFunction() {
    new RetryPolicy().withDelay(null);
  }

  @Test(expectedExceptions = NullPointerException.class)
  public void testNullResult() {
    new RetryPolicy().withDelayWhen((result, failure, context) -> new Duration(1L, TimeUnit.SECONDS), null);
  }

  @Test(expectedExceptions = NullPointerException.class)
  public void testNullFailureType() {
    new RetryPolicy().withDelayOn((result, failure, context) -> new Duration(1L, TimeUnit.SECONDS), null);
  }

  @Test(expectedExceptions = UncheckedExpectedException.class)
  public void testUncheckedExceptionInDelayFunction() {
    RetryPolicy retryPolicy = new RetryPolicy().withDelay((result, failure, context) -> {
      throw new UncheckedExpectedException();
    });

    Failsafe.with(retryPolicy).run((ExecutionContext context) -> {
      throw new RuntimeException("try again");
    });
  }

  public void testDynamicDelay() {
    long dynamicDelay = TimeUnit.MILLISECONDS.toNanos(100);

    RetryPolicy retryPolicy = new RetryPolicy().withDelayOn(
        (result, failure, context) -> new Duration(dynamicDelay, TimeUnit.NANOSECONDS), TimeoutException.class);

    List<Long> executionTimes = new ArrayList<>();

    Failsafe.with(retryPolicy).run((ExecutionContext context) -> {
      executionTimes.add(System.nanoTime());
      if (context.getExecutions() == 0)
        throw new TimeoutException();
    });

    assertEquals(executionTimes.size(), 2, "Should have exactly two executions");

    long t0 = executionTimes.get(0);
    long t1 = executionTimes.get(1);

    assertDelay(t1 - t0, dynamicDelay);
  }

  public void shouldFallbackToStaticDelay() {
    long fixedDelay = TimeUnit.MILLISECONDS.toNanos(100);
    long dynamicDelay = TimeUnit.MILLISECONDS.toNanos(300);

    RetryPolicy retryPolicy = new RetryPolicy()
        .retryIf(r -> r != null)
        .withDelay(fixedDelay, TimeUnit.NANOSECONDS)
        .withDelay((result, failure, ctx) -> new Duration(ctx.getExecutions() % 2 == 1 ? dynamicDelay : -1,
            TimeUnit.NANOSECONDS));

    List<Long> executionTimes = new ArrayList<>();

    Failsafe.with(retryPolicy).run((ExecutionContext context) -> {
      executionTimes.add(System.nanoTime());
      if (context.getExecutions() != 2)
        throw new TimeoutException();
    });

    assertEquals(executionTimes.size(), 3, "Should have exactly three executions");

    long t0 = executionTimes.get(0);
    long t1 = executionTimes.get(1);
    long t2 = executionTimes.get(2);

    assertDelay(t1 - t0, dynamicDelay);
    assertDelay(t2 - t1, fixedDelay);
  }

  public void shouldFallbackToBackoffDelay() {
    long backoffDelay = TimeUnit.MILLISECONDS.toNanos(100);
    long dynamicDelay = TimeUnit.MILLISECONDS.toNanos(300);

    RetryPolicy retryPolicy = new RetryPolicy()
        .retryIf(r -> r != null)
        .withBackoff(backoffDelay, backoffDelay * 100, TimeUnit.NANOSECONDS)
        .withDelay((result, failure, ctx) -> new Duration(ctx.getExecutions() % 2 == 0 ? dynamicDelay : -1,
            TimeUnit.NANOSECONDS));

    List<Long> executionTimes = new ArrayList<>();

    Failsafe.with(retryPolicy).run((ExecutionContext context) -> {
      executionTimes.add(System.nanoTime());
      if (context.getExecutions() != 3)
        throw new TimeoutException();
    });

    assertEquals(executionTimes.size(), 4, "Should have exactly four executions");

    long t0 = executionTimes.get(0);
    long t1 = executionTimes.get(1);
    long t2 = executionTimes.get(2);
    long t3 = executionTimes.get(3);

    assertDelay(t1 - t0, backoffDelay);
    assertDelay(t2 - t1, dynamicDelay);
    assertDelay(t3 - t2, backoffDelay * 2);
  }

  public void shouldDelayOnMatchingResult() {
    AtomicInteger delays = new AtomicInteger(0);
    RetryPolicy retryPolicy = new RetryPolicy().retryIf(result -> true).withMaxRetries(4).withDelayWhen((r, f, c) -> {
      delays.incrementAndGet(); // side-effect for test purposes
      return new Duration(1L, TimeUnit.MICROSECONDS);
    }, "expected");

    AtomicInteger attempts = new AtomicInteger(0);
    Object result = Failsafe.with(retryPolicy).withFallback(123).get(() -> {
      int i = attempts.getAndIncrement();
      switch (i) {
        case 0:
        case 3:
          return "expected";
        default:
          return i;
      }
    });

    assertEquals(result, 123, "Fallback should be used");
    assertEquals(attempts.get(), 5, "Expecting five attempts (1 + 4 retries)");
    assertEquals(delays.get(), 2, "Expecting two dynamic delays matching String result");
  }

  public void shouldDelayOnMatchingFailureType() {
    AtomicInteger delays = new AtomicInteger(0);
    RetryPolicy retryPolicy = new RetryPolicy()
        .retryOn(UncheckedExpectedException.class)
        .withMaxRetries(4)
        .withDelayOn((r, f, c) -> {
          delays.incrementAndGet(); // side-effect for test purposes
          return new Duration(1L, TimeUnit.MICROSECONDS);
        }, DelayException.class);

    AtomicInteger attempts = new AtomicInteger(0);
    int result = Failsafe.with(retryPolicy).withFallback(123).get(() -> {
      int i = attempts.getAndIncrement();
      switch (i) {
        case 0:
        case 2:
          throw new DelayException();
        default:
          throw new UncheckedExpectedException();
      }
    });

    assertEquals(result, 123, "Fallback should be used");
    assertEquals(attempts.get(), 5, "Expecting five attempts (1 + 4 retries)");
    assertEquals(delays.get(), 2, "Expecting two dynamic delays matching DelayException failure");
  }

  private void assertDelay(long elapsedNanos, long expectedDelayNanos) {
    long pad = TimeUnit.MILLISECONDS.toNanos(25);
    assertTrue(elapsedNanos > expectedDelayNanos - pad, "Time between executions less than expected");
    assertTrue(elapsedNanos < expectedDelayNanos + pad, "Time between executions more than expected");
  }
}