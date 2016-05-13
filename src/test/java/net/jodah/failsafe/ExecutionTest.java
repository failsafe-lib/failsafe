package net.jodah.failsafe;

import static net.jodah.failsafe.Testing.failures;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.net.ConnectException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.testng.annotations.Test;

/**
 * @author Jonathan Halterman
 */
@Test
public class ExecutionTest {
  ConnectException e = new ConnectException();

  public void testCanRetryForResult() {
    // Given retry for null
    Execution exec = new Execution(new RetryPolicy().retryWhen(null));

    // When / Then
    assertFalse(exec.complete(null));
    assertTrue(exec.canRetryFor(null));
    assertFalse(exec.canRetryFor(1));

    // Then
    assertEquals(exec.getExecutions(), 3);
    assertTrue(exec.isComplete());
    assertEquals(exec.getLastResult(), Integer.valueOf(1));
    assertNull(exec.getLastFailure());

    // Given 2 max retries
    exec = new Execution(new RetryPolicy().retryWhen(null).withMaxRetries(2));

    // When / Then
    assertFalse(exec.complete(null));
    assertTrue(exec.canRetryFor(null));
    assertFalse(exec.canRetryFor(null));

    // Then
    assertEquals(exec.getExecutions(), 3);
    assertTrue(exec.isComplete());
    assertNull(exec.getLastResult());
    assertNull(exec.getLastFailure());
  }

  public void testCanRetryForResultAndThrowable() {
    // Given retry for null
    Execution exec = new Execution(new RetryPolicy().retryWhen(null));

    // When / Then
    assertFalse(exec.complete(null));
    assertTrue(exec.canRetryFor(null, null));
    assertTrue(exec.canRetryFor(1, new IllegalArgumentException()));
    assertFalse(exec.canRetryFor(1, null));

    // Then
    assertEquals(exec.getExecutions(), 4);
    assertTrue(exec.isComplete());

    // Given 2 max retries
    exec = new Execution(new RetryPolicy().retryWhen(null).withMaxRetries(2));

    // When / Then
    assertFalse(exec.complete(null));
    assertTrue(exec.canRetryFor(null, e));
    assertFalse(exec.canRetryFor(null, e));

    // Then
    assertEquals(exec.getExecutions(), 3);
    assertTrue(exec.isComplete());
  }

  @SuppressWarnings("unchecked")
  public void testCanRetryOn() {
    // Given retry on IllegalArgumentException
    Execution exec = new Execution(new RetryPolicy().retryOn(IllegalArgumentException.class));

    // When / Then
    assertTrue(exec.canRetryOn(new IllegalArgumentException()));
    assertFalse(exec.canRetryOn(e));

    // Then
    assertEquals(exec.getExecutions(), 2);
    assertTrue(exec.isComplete());
    assertNull(exec.getLastResult());
    assertEquals(exec.getLastFailure(), e);

    // Given 2 max retries
    exec = new Execution(new RetryPolicy().withMaxRetries(2));

    // When / Then
    assertTrue(exec.canRetryOn(e));
    assertTrue(exec.canRetryOn(e));
    assertFalse(exec.canRetryOn(e));

    // Then
    assertEquals(exec.getExecutions(), 3);
    assertTrue(exec.isComplete());
    assertNull(exec.getLastResult());
    assertEquals(exec.getLastFailure(), e);
  }

  public void testComplete() {
    // Given
    Execution exec = new Execution(new RetryPolicy());

    // When
    exec.complete();

    // Then
    assertEquals(exec.getExecutions(), 1);
    assertTrue(exec.isComplete());
    assertNull(exec.getLastResult());
    assertNull(exec.getLastFailure());
  }

  public void testCompleteForResult() {
    // Given
    Execution exec = new Execution(new RetryPolicy().retryWhen(null));

    // When / Then
    assertFalse(exec.complete(null));
    assertTrue(exec.complete(true));

    // Then
    assertEquals(exec.getExecutions(), 2);
    assertTrue(exec.isComplete());
    assertEquals(exec.getLastResult(), Boolean.TRUE);
    assertNull(exec.getLastFailure());
  }

  public void testGetAttemptCount() {
    Execution exec = new Execution(new RetryPolicy());
    exec.recordFailure(e);
    exec.recordFailure(e);
    assertEquals(exec.getExecutions(), 2);
  }

  public void testGetElapsedMillis() throws Throwable {
    Execution exec = new Execution(new RetryPolicy());
    assertTrue(exec.getElapsedTime().toMillis() < 100);
    Thread.sleep(150);
    assertTrue(exec.getElapsedTime().toMillis() > 100);
  }

  @SuppressWarnings("unchecked")
  public void testIsComplete() {
    List<Object> list = mock(List.class);
    when(list.size()).thenThrow(failures(2, new IllegalStateException())).thenReturn(5);

    RetryPolicy retryPolicy = new RetryPolicy().retryOn(IllegalStateException.class);
    Execution exec = new Execution(retryPolicy);

    while (!exec.isComplete()) {
      try {
        exec.complete(list.size());
      } catch (IllegalStateException e) {
        exec.recordFailure(e);
      }
    }

    assertEquals(exec.getLastResult(), Integer.valueOf(5));
    assertEquals(exec.getExecutions(), 3);
  }

  public void shouldAdjustWaitTimeForBackoff() {
    Execution exec = new Execution(new RetryPolicy().withBackoff(1, 10, TimeUnit.NANOSECONDS));
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

  public void shouldAdjustWaitTimeForMaxDuration() throws Throwable {
    Execution exec = new Execution(
        new RetryPolicy().withDelay(49, TimeUnit.MILLISECONDS).withMaxDuration(50, TimeUnit.MILLISECONDS));
    Thread.sleep(10);
    assertTrue(exec.canRetryOn(e));
    assertTrue(exec.getWaitTime().toNanos() < TimeUnit.MILLISECONDS.toNanos(50) && exec.getWaitTime().toNanos() > 0);
  }

  public void shouldSupportMaxDuration() throws Exception {
    Execution exec = new Execution(new RetryPolicy().withMaxDuration(100, TimeUnit.MILLISECONDS));
    assertTrue(exec.canRetryOn(e));
    assertTrue(exec.canRetryOn(e));
    Thread.sleep(100);
    assertFalse(exec.canRetryOn(e));
    assertTrue(exec.isComplete());
  }

  public void shouldSupportMaxRetries() throws Exception {
    Execution exec = new Execution(new RetryPolicy().withMaxRetries(3));
    assertTrue(exec.canRetryOn(e));
    assertTrue(exec.canRetryOn(e));
    assertTrue(exec.canRetryOn(e));
    assertFalse(exec.canRetryOn(e));
    assertTrue(exec.isComplete());
  }

  public void shouldGetWaitMillis() throws Throwable {
    Execution exec = new Execution(new RetryPolicy().withDelay(100, TimeUnit.MILLISECONDS)
        .withMaxDuration(101, TimeUnit.MILLISECONDS)
        .retryWhen(null));
    assertEquals(exec.getWaitTime().toMillis(), 100);
    exec.canRetryFor(null);
    assertTrue(exec.getWaitTime().toMillis() <= 100);
    Thread.sleep(150);
    assertFalse(exec.canRetryFor(null));
    assertEquals(exec.getWaitTime().toMillis(), 0);
  }

  @Test(expectedExceptions = IllegalStateException.class)
  public void shouldThrowOnMultipleCompletes() {
    Execution exec = new Execution(new RetryPolicy());
    exec.complete();
    exec.complete();
  }

  @Test(expectedExceptions = IllegalStateException.class)
  public void shouldThrowOnCanRetryWhenAlreadyComplete() {
    Execution exec = new Execution(new RetryPolicy());
    exec.complete();
    exec.canRetryOn(e);
  }
}
