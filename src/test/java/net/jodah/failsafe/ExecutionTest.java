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
package net.jodah.failsafe;

import org.testng.annotations.Test;

import java.net.ConnectException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static net.jodah.failsafe.Testing.failures;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.*;

/**
 * @author Jonathan Halterman
 */
@Test
public class ExecutionTest {
  ConnectException e = new ConnectException();

  public void testCanRetryForResult() {
    // Given rpRetry for null
    Execution exec = new Execution(new RetryPolicy<>().handleResult(null));

    // When / Then
    assertFalse(exec.complete(null));
    assertTrue(exec.canRetryFor(null));
    assertFalse(exec.canRetryFor(1));

    // Then
    assertEquals(exec.getAttemptCount(), 3);
    assertTrue(exec.isComplete());
    assertEquals(exec.getLastResult(), Integer.valueOf(1));
    assertNull(exec.getLastFailure());

    // Given 2 max retries
    exec = new Execution(new RetryPolicy<>().handleResult(null).withMaxRetries(2));

    // When / Then
    assertFalse(exec.complete(null));
    assertTrue(exec.canRetryFor(null));
    assertFalse(exec.canRetryFor(null));

    // Then
    assertEquals(exec.getAttemptCount(), 3);
    assertTrue(exec.isComplete());
    assertNull(exec.getLastResult());
    assertNull(exec.getLastFailure());
  }

  public void testCanRetryForResultAndThrowable() {
    // Given rpRetry for null
    Execution exec = new Execution(new RetryPolicy<>().withMaxAttempts(10).handleResult(null));

    // When / Then
    assertFalse(exec.complete(null));
    assertTrue(exec.canRetryFor(null, null));
    assertTrue(exec.canRetryFor(1, new IllegalArgumentException()));
    assertFalse(exec.canRetryFor(1, null));

    // Then
    assertEquals(exec.getAttemptCount(), 4);
    assertTrue(exec.isComplete());

    // Given 2 max retries
    exec = new Execution(new RetryPolicy<>().handleResult(null).withMaxRetries(2));

    // When / Then
    assertFalse(exec.complete(null));
    assertTrue(exec.canRetryFor(null, e));
    assertFalse(exec.canRetryFor(null, e));

    // Then
    assertEquals(exec.getAttemptCount(), 3);
    assertTrue(exec.isComplete());
  }

  public void testCanRetryOn() {
    // Given rpRetry on IllegalArgumentException
    Execution exec = new Execution(new RetryPolicy<>().handle(IllegalArgumentException.class));

    // When / Then
    assertTrue(exec.canRetryOn(new IllegalArgumentException()));
    assertFalse(exec.canRetryOn(e));

    // Then
    assertEquals(exec.getAttemptCount(), 2);
    assertTrue(exec.isComplete());
    assertNull(exec.getLastResult());
    assertEquals(exec.getLastFailure(), e);

    // Given 2 max retries
    exec = new Execution(new RetryPolicy<>().withMaxRetries(2));

    // When / Then
    assertTrue(exec.canRetryOn(e));
    assertTrue(exec.canRetryOn(e));
    assertFalse(exec.canRetryOn(e));

    // Then
    assertEquals(exec.getAttemptCount(), 3);
    assertTrue(exec.isComplete());
    assertNull(exec.getLastResult());
    assertEquals(exec.getLastFailure(), e);
  }

  public void testComplete() {
    // Given
    Execution exec = new Execution(new RetryPolicy<>());

    // When
    exec.complete();

    // Then
    assertEquals(exec.getAttemptCount(), 1);
    assertTrue(exec.isComplete());
    assertNull(exec.getLastResult());
    assertNull(exec.getLastFailure());
  }

  public void testCompleteForResult() {
    // Given
    Execution exec = new Execution(new RetryPolicy<>().handleResult(null));

    // When / Then
    assertFalse(exec.complete(null));
    assertTrue(exec.complete(true));

    // Then
    assertEquals(exec.getAttemptCount(), 2);
    assertTrue(exec.isComplete());
    assertEquals(exec.getLastResult(), Boolean.TRUE);
    assertNull(exec.getLastFailure());
  }

  public void testGetAttemptCount() {
    Execution exec = new Execution(new RetryPolicy<>());
    exec.recordFailure(e);
    exec.recordFailure(e);
    assertEquals(exec.getAttemptCount(), 2);
  }

  public void testGetElapsedMillis() throws Throwable {
    Execution exec = new Execution(new RetryPolicy<>());
    assertTrue(exec.getElapsedTime().toMillis() < 100);
    Thread.sleep(150);
    assertTrue(exec.getElapsedTime().toMillis() > 100);
  }

  @SuppressWarnings("unchecked")
  public void testIsComplete() {
    List<Object> list = mock(List.class);
    when(list.size()).thenThrow(failures(2, new IllegalStateException())).thenReturn(5);

    RetryPolicy retryPolicy = new RetryPolicy<>().handle(IllegalStateException.class);
    Execution exec = new Execution(retryPolicy);

    while (!exec.isComplete()) {
      try {
        exec.complete(list.size());
      } catch (IllegalStateException e) {
        exec.recordFailure(e);
      }
    }

    assertEquals(exec.getLastResult(), Integer.valueOf(5));
    assertEquals(exec.getAttemptCount(), 3);
  }

  public void shouldAdjustWaitTimeForBackoff() {
    Execution exec = new Execution(new RetryPolicy<>().withMaxAttempts(10).withBackoff(1, 10, ChronoUnit.NANOS));
    assertEquals(exec.getWaitTime().toNanos(), 0);
    exec.recordFailure(e);
    assertEquals(exec.getWaitTime().toNanos(), 1);
    exec.recordFailure(e);
    assertEquals(exec.getWaitTime().toNanos(), 2);
    exec.recordFailure(e);
    assertEquals(exec.getWaitTime().toNanos(), 4);
    exec.recordFailure(e);
    assertEquals(exec.getWaitTime().toNanos(), 8);
    exec.recordFailure(e);
    assertEquals(exec.getWaitTime().toNanos(), 10);
    exec.recordFailure(e);
    assertEquals(exec.getWaitTime().toNanos(), 10);
  }

  public void shouldAdjustWaitTimeForComputedDelay() {
    Execution exec = new Execution(
        new RetryPolicy<>().withMaxAttempts(10).withDelay((r, f, ctx) -> Duration.ofNanos(ctx.getAttemptCount() * 2)));
    assertEquals(exec.getWaitTime().toNanos(), 0);
    exec.recordFailure(e);
    assertEquals(exec.getWaitTime().toNanos(), 2);
    exec.recordFailure(e);
    assertEquals(exec.getWaitTime().toNanos(), 4);
    exec.recordFailure(e);
    assertEquals(exec.getWaitTime().toNanos(), 6);
    exec.recordFailure(e);
    assertEquals(exec.getWaitTime().toNanos(), 8);
  }

  public void shouldFallbackWaitTimeFromComputedToFixedDelay() {
    Execution exec = new Execution(new RetryPolicy<>().withMaxAttempts(10)
        .withDelay(Duration.ofNanos(5))
        .withDelay((r, f, ctx) -> Duration.ofNanos(ctx.getAttemptCount() % 2 == 0 ? ctx.getAttemptCount() * 2 : -1)));
    assertEquals(exec.getWaitTime().toNanos(), 0);
    exec.recordFailure(e);
    assertEquals(exec.getWaitTime().toNanos(), 5);
    exec.recordFailure(e);
    assertEquals(exec.getWaitTime().toNanos(), 4);
    exec.recordFailure(e);
    assertEquals(exec.getWaitTime().toNanos(), 5);
    exec.recordFailure(e);
    assertEquals(exec.getWaitTime().toNanos(), 8);
    exec.recordFailure(e);
    assertEquals(exec.getWaitTime().toNanos(), 5);
    exec.recordFailure(e);
    assertEquals(exec.getWaitTime().toNanos(), 12);
  }

  public void shouldFallbackWaitTimeFromComputedToBackoffDelay() {
    Execution exec = new Execution(new RetryPolicy<>().withMaxAttempts(10)
        .withBackoff(1, 10, ChronoUnit.NANOS)
        .withDelay((r, f, ctx) -> Duration.ofNanos(ctx.getAttemptCount() % 2 == 0 ? ctx.getAttemptCount() * 2 : -1)));
    assertEquals(exec.getWaitTime().toNanos(), 0);
    exec.recordFailure(e);
    assertEquals(exec.getWaitTime().toNanos(), 1);
    exec.recordFailure(e);
    assertEquals(exec.getWaitTime().toNanos(), 4);
    exec.recordFailure(e);
    assertEquals(exec.getWaitTime().toNanos(), 2);
    exec.recordFailure(e);
    assertEquals(exec.getWaitTime().toNanos(), 8);
    exec.recordFailure(e);
    assertEquals(exec.getWaitTime().toNanos(), 4);
    exec.recordFailure(e);
    assertEquals(exec.getWaitTime().toNanos(), 12);
    exec.recordFailure(e);
    assertEquals(exec.getWaitTime().toNanos(), 8);
  }

  public void shouldAdjustWaitTimeForMaxDuration() throws Throwable {
    Execution exec = new Execution(
        new RetryPolicy<>().withDelay(Duration.ofMillis(49)).withMaxDuration(Duration.ofMillis(50)));
    Thread.sleep(10);
    assertTrue(exec.canRetryOn(e));
    assertTrue(exec.getWaitTime().toNanos() < TimeUnit.MILLISECONDS.toNanos(50) && exec.getWaitTime().toNanos() > 0);
  }

  public void shouldSupportMaxDuration() throws Exception {
    Execution exec = new Execution(new RetryPolicy<>().withMaxDuration(Duration.ofMillis(100)));
    assertTrue(exec.canRetryOn(e));
    assertTrue(exec.canRetryOn(e));
    Thread.sleep(105);
    assertFalse(exec.canRetryOn(e));
    assertTrue(exec.isComplete());
  }

  public void shouldSupportMaxRetries() {
    Execution exec = new Execution(new RetryPolicy<>().withMaxRetries(3));
    assertTrue(exec.canRetryOn(e));
    assertTrue(exec.canRetryOn(e));
    assertTrue(exec.canRetryOn(e));
    assertFalse(exec.canRetryOn(e));
    assertTrue(exec.isComplete());
  }

  public void shouldGetWaitMillis() throws Throwable {
    Execution exec = new Execution(new RetryPolicy<>().withDelay(Duration.ofMillis(100))
        .withMaxDuration(Duration.ofMillis(101))
        .handleResult(null));
    assertEquals(exec.getWaitTime().toMillis(), 0);
    exec.canRetryFor(null);
    assertTrue(exec.getWaitTime().toMillis() <= 100);
    Thread.sleep(150);
    assertFalse(exec.canRetryFor(null));
    assertEquals(exec.getWaitTime().toMillis(), 0);
  }

  @Test(expectedExceptions = IllegalStateException.class)
  public void shouldThrowOnMultipleCompletes() {
    Execution exec = new Execution(new RetryPolicy<>());
    exec.complete();
    exec.complete();
  }

  @Test(expectedExceptions = IllegalStateException.class)
  public void shouldThrowOnCanRetryWhenAlreadyComplete() {
    Execution exec = new Execution(new RetryPolicy<>());
    exec.complete();
    exec.canRetryOn(e);
  }
}
