package net.jodah.recurrent;

import static net.jodah.recurrent.Testing.failures;
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
    Execution inv = new Execution(new RetryPolicy().retryWhen(null));

    // When / Then
    assertFalse(inv.complete(null));
    assertTrue(inv.canRetryFor(null));
    assertFalse(inv.canRetryFor(1));

    // Then
    assertEquals(inv.getExecutions(), 2);
    assertTrue(inv.isComplete());
    assertEquals(inv.getLastResult(), Integer.valueOf(1));
    assertNull(inv.getLastFailure());

    // Given 2 max retries
    inv = new Execution(new RetryPolicy().retryWhen(null).withMaxRetries(2));

    // When / Then
    assertFalse(inv.complete(null));
    assertTrue(inv.canRetryFor(null));
    assertTrue(inv.canRetryFor(null));
    assertFalse(inv.canRetryFor(null));

    // Then
    assertEquals(inv.getExecutions(), 3);
    assertTrue(inv.isComplete());
    assertNull(inv.getLastResult());
    assertNull(inv.getLastFailure());
  }

  public void testCanRetryForResultAndThrowable() {
    // Given retry for null
    Execution inv = new Execution(new RetryPolicy().retryWhen(null));

    // When / Then
    assertFalse(inv.complete(null));
    assertTrue(inv.canRetryFor(null, null));
    assertTrue(inv.canRetryFor(1, new IllegalArgumentException()));
    assertFalse(inv.canRetryFor(1, null));

    // Then
    assertEquals(inv.getExecutions(), 3);
    assertTrue(inv.isComplete());

    // Given 2 max retries
    inv = new Execution(new RetryPolicy().retryWhen(null).withMaxRetries(2));

    // When / Then
    assertFalse(inv.complete(null));
    assertTrue(inv.canRetryFor(null, e));
    assertTrue(inv.canRetryFor(null, e));
    assertFalse(inv.canRetryFor(null, e));

    // Then
    assertEquals(inv.getExecutions(), 3);
    assertTrue(inv.isComplete());
  }

  @SuppressWarnings("unchecked")
  public void testCanRetryOn() {
    // Given retry on IllegalArgumentException
    Execution inv = new Execution(new RetryPolicy().retryOn(IllegalArgumentException.class));

    // When / Then
    assertTrue(inv.canRetryOn(new IllegalArgumentException()));
    assertFalse(inv.canRetryOn(e));

    // Then
    assertEquals(inv.getExecutions(), 2);
    assertTrue(inv.isComplete());
    assertNull(inv.getLastResult());
    assertEquals(inv.getLastFailure(), e);

    // Given 2 max retries
    inv = new Execution(new RetryPolicy().withMaxRetries(2));

    // When / Then
    assertTrue(inv.canRetryOn(e));
    assertTrue(inv.canRetryOn(e));
    assertFalse(inv.canRetryOn(e));

    // Then
    assertEquals(inv.getExecutions(), 3);
    assertTrue(inv.isComplete());
    assertNull(inv.getLastResult());
    assertEquals(inv.getLastFailure(), e);
  }

  public void testComplete() {
    // Given
    Execution inv = new Execution(new RetryPolicy());

    // When
    inv.complete();

    // Then
    assertEquals(inv.getExecutions(), 1);
    assertTrue(inv.isComplete());
    assertNull(inv.getLastResult());
    assertNull(inv.getLastFailure());
  }

  public void testCompleteForResult() {
    // Given
    Execution inv = new Execution(new RetryPolicy().retryWhen(null));

    // When / Then
    assertFalse(inv.complete(null));
    assertTrue(inv.complete(true));

    // Then
    assertEquals(inv.getExecutions(), 1);
    assertTrue(inv.isComplete());
    assertEquals(inv.getLastResult(), Boolean.TRUE);
    assertNull(inv.getLastFailure());
  }

  public void testGetAttemptCount() {
    Execution inv = new Execution(new RetryPolicy());
    inv.fail(e);
    inv.fail(e);
    assertEquals(inv.getExecutions(), 2);
  }

  public void testGetElapsedMillis() throws Throwable {
    Execution inv = new Execution(new RetryPolicy());
    assertTrue(inv.getElapsedMillis() < 100);
    Thread.sleep(150);
    assertTrue(inv.getElapsedMillis() > 100);
  }

  @SuppressWarnings("unchecked")
  public void testIsComplete() {
    List<Object> list = mock(List.class);
    when(list.size()).thenThrow(failures(2, IllegalStateException.class)).thenReturn(5);

    RetryPolicy retryPolicy = new RetryPolicy().retryOn(IllegalStateException.class);
    Execution inv = new Execution(retryPolicy);

    while (!inv.isComplete()) {
      try {
        inv.complete(list.size());
      } catch (IllegalStateException e) {
        inv.fail(e);
      }
    }

    assertEquals(inv.getLastResult(), Integer.valueOf(5));
    assertEquals(inv.getExecutions(), 3);
  }

  public void shouldAdjustWaitTimeForBackoff() {
    Execution inv = new Execution(new RetryPolicy().withBackoff(1, 10, TimeUnit.NANOSECONDS));
    assertEquals(inv.getWaitNanos(), 1);
    inv.fail(e);
    assertEquals(inv.getWaitNanos(), 2);
    inv.fail(e);
    assertEquals(inv.getWaitNanos(), 4);
    inv.fail(e);
    assertEquals(inv.getWaitNanos(), 8);
    inv.fail(e);
    assertEquals(inv.getWaitNanos(), 10);
    inv.fail(e);
    assertEquals(inv.getWaitNanos(), 10);
  }

  public void shouldAdjustWaitTimeForMaxDuration() throws Throwable {
    Execution inv = new Execution(
        new RetryPolicy().withDelay(49, TimeUnit.MILLISECONDS).withMaxDuration(50, TimeUnit.MILLISECONDS));
    Thread.sleep(10);
    assertTrue(inv.canRetryOn(e));
    assertTrue(inv.getWaitNanos() < TimeUnit.MILLISECONDS.toNanos(50) && inv.getWaitNanos() > 0);
  }

  public void shouldSupportMaxDuration() throws Exception {
    Execution inv = new Execution(new RetryPolicy().withMaxDuration(100, TimeUnit.MILLISECONDS));
    assertTrue(inv.canRetryOn(e));
    assertTrue(inv.canRetryOn(e));
    Thread.sleep(100);
    assertFalse(inv.canRetryOn(e));
    assertTrue(inv.isComplete());
  }

  public void shouldSupportMaxRetries() throws Exception {
    Execution inv = new Execution(new RetryPolicy().withMaxRetries(3));
    assertTrue(inv.canRetryOn(e));
    assertTrue(inv.canRetryOn(e));
    assertTrue(inv.canRetryOn(e));
    assertFalse(inv.canRetryOn(e));
    assertTrue(inv.isComplete());
  }

  public void shouldGetWaitMillis() throws Throwable {
    Execution inv = new Execution(new RetryPolicy().withDelay(100, TimeUnit.MILLISECONDS)
        .withMaxDuration(101, TimeUnit.MILLISECONDS)
        .retryWhen(null));
    assertEquals(inv.getWaitMillis(), 100);
    inv.canRetryFor(null);
    assertTrue(inv.getWaitMillis() <= 100);
    Thread.sleep(150);
    assertFalse(inv.canRetryFor(null));
    assertEquals(inv.getWaitMillis(), 0);
  }

  @Test(expectedExceptions = IllegalStateException.class)
  public void shouldThrowOnMultipleCompletes() {
    Execution inv = new Execution(new RetryPolicy());
    inv.complete();
    inv.complete();
  }

  @Test(expectedExceptions = IllegalStateException.class)
  public void shouldThrowOnCanRetryWhenAlreadyComplete() {
    Execution inv = new Execution(new RetryPolicy());
    inv.complete();
    inv.canRetryOn(e);
  }
}
