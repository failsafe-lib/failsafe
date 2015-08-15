package net.jodah.recurrent;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.net.ConnectException;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test
public class AsyncInvocationTest {
  ConnectException e = new ConnectException();
  AsyncInvocation inv;
  RecurrentFuture<Object> future;
  AsyncCallable<Object> callable;
  Scheduler scheduler;

  @BeforeMethod
  @SuppressWarnings("unchecked")
  void beforeMethod() {
    scheduler = mock(Scheduler.class);
    future = mock(RecurrentFuture.class);
    callable = mock(AsyncCallable.class);
  }

  public void testComplete() {
    // Given
    inv = new AsyncInvocation(callable, new RetryPolicy(), scheduler, future);

    // When
    inv.complete();

    // Then
    assertEquals(inv.getAttemptCount(), 1);
    assertTrue(inv.isComplete());
    assertNull(inv.getLastResult());
    assertNull(inv.getLastFailure());
    verify(future).complete(null, null);
  }

  public void testCompleteForResult() {
    // Given
    inv = new AsyncInvocation(callable, new RetryPolicy().retryFor(null), scheduler, future);

    // When / Then
    assertFalse(inv.complete(null));
    assertTrue(inv.complete(true));

    // Then
    assertEquals(inv.getAttemptCount(), 1);
    assertTrue(inv.isComplete());
    assertEquals(inv.getLastResult(), Boolean.TRUE);
    assertNull(inv.getLastFailure());
    verify(future).complete(true, null);
  }

  public void testGetAttemptCount() {
    inv = new AsyncInvocation(callable, new RetryPolicy(), scheduler, future);
    inv.retryOn(e);
    inv.reset();
    inv.retryOn(e);
    assertEquals(inv.getAttemptCount(), 2);
  }

  public void testRetryForResult() {
    // Given retry for null
    inv = new AsyncInvocation(callable, new RetryPolicy().retryFor(null), scheduler, future);

    // When / Then
    assertFalse(inv.complete(null));
    assertTrue(inv.retryFor(null));
    inv.reset();
    assertFalse(inv.retryFor(1));

    // Then
    assertEquals(inv.getAttemptCount(), 2);
    assertTrue(inv.isComplete());
    assertEquals(inv.getLastResult(), Integer.valueOf(1));
    assertNull(inv.getLastFailure());
    verifyScheduler(1);
    verify(future).complete(1, null);

    // Given 2 max retries
    inv = new AsyncInvocation(callable, new RetryPolicy().retryFor(null).withMaxRetries(1), scheduler, future);

    // When / Then
    resetMocks();
    assertFalse(inv.complete(null));
    inv.reset();
    assertTrue(inv.retryFor(null));
    inv.reset();
    assertFalse(inv.retryFor(null));

    // Then
    assertEquals(inv.getAttemptCount(), 2);
    assertTrue(inv.isComplete());
    assertNull(inv.getLastResult());
    assertNull(inv.getLastFailure());
    verifyScheduler(1);
    verify(future).complete(null, null);
  }

  public void testRetryForResultAndThrowable() {
    // Given retry for null
    inv = new AsyncInvocation(callable, new RetryPolicy().retryFor(null), scheduler, future);

    // When / Then
    assertFalse(inv.complete(null));
    assertTrue(inv.retryFor(null, null));
    inv.reset();
    assertTrue(inv.retryFor(1, new IllegalArgumentException()));
    inv.reset();
    assertFalse(inv.retryFor(1, null));

    // Then
    assertEquals(inv.getAttemptCount(), 3);
    assertTrue(inv.isComplete());
    assertEquals(inv.getLastResult(), Integer.valueOf(1));
    assertNull(inv.getLastFailure());
    verifyScheduler(2);
    verify(future).complete(1, null);

    // Given 2 max retries
    inv = new AsyncInvocation(callable, new RetryPolicy().retryFor(null).withMaxRetries(1), scheduler, future);

    // When / Then
    resetMocks();
    assertFalse(inv.complete(null));
    assertTrue(inv.retryFor(null, e));
    inv.reset();
    assertFalse(inv.retryFor(null, e));

    // Then
    assertEquals(inv.getAttemptCount(), 2);
    assertTrue(inv.isComplete());
    assertNull(inv.getLastResult());
    assertEquals(inv.getLastFailure(), e);
    verifyScheduler(1);
    verify(future).complete(null, e);
  }

  @SuppressWarnings("unchecked")
  public void testRetryOn() {
    // Given retry on IllegalArgumentException
    inv = new AsyncInvocation(callable, new RetryPolicy().retryOn(IllegalArgumentException.class), scheduler, future);

    // When / Then
    assertTrue(inv.retryOn(new IllegalArgumentException()));
    inv.reset();
    assertFalse(inv.retryOn(e));

    // Then
    assertEquals(inv.getAttemptCount(), 2);
    assertTrue(inv.isComplete());
    assertNull(inv.getLastResult());
    assertEquals(inv.getLastFailure(), e);
    verifyScheduler(1);
    verify(future).complete(null, e);

    // Given 2 max retries
    inv = new AsyncInvocation(callable, new RetryPolicy().withMaxRetries(1), scheduler, future);

    // When / Then
    resetMocks();
    assertTrue(inv.retryOn(e));
    inv.reset();
    assertFalse(inv.retryOn(e));

    // Then
    assertEquals(inv.getAttemptCount(), 2);
    assertTrue(inv.isComplete());
    assertNull(inv.getLastResult());
    assertEquals(inv.getLastFailure(), e);
    verifyScheduler(1);
    verify(future).complete(null, e);
  }

  @Test(expectedExceptions = IllegalStateException.class)
  public void shouldThrowOnRetryWhenAlreadyComplete() {
    inv = new AsyncInvocation(callable, new RetryPolicy(), scheduler, future);
    inv.complete();
    inv.retryOn(e);
  }

  public void testRetryOrComplete() {
    // Given retry on IllegalArgumentException
    inv = new AsyncInvocation(callable, new RetryPolicy(), scheduler, future);

    // When / Then
    inv.retryOrComplete(null, e);
    assertFalse(inv.isComplete());
    inv.retryOrComplete(null, null);

    // Then
    assertEquals(inv.getAttemptCount(), 2);
    assertTrue(inv.isComplete());
    assertNull(inv.getLastResult());
    assertNull(inv.getLastFailure());
    verifyScheduler(1);
    verify(future).complete(null, null);
  }

  @SuppressWarnings("unchecked")
  private void resetMocks() {
    reset(scheduler);
    reset(future);
    reset(callable);
  }

  private void verifyScheduler(int invocations) {
    verify(scheduler, times(invocations)).schedule(any(Callable.class), any(Long.class), any(TimeUnit.class));
  }
}
