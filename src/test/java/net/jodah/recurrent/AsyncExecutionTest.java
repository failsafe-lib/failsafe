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

import net.jodah.recurrent.util.concurrent.Scheduler;

@Test
public class AsyncExecutionTest {
  ConnectException e = new ConnectException();
  AsyncExecution inv;
  RecurrentFuture<Object> future;
  AsyncContextualCallable<Object> callable;
  Scheduler scheduler;

  @BeforeMethod
  @SuppressWarnings("unchecked")
  void beforeMethod() {
    scheduler = mock(Scheduler.class);
    future = mock(RecurrentFuture.class);
    callable = mock(AsyncContextualCallable.class);
  }

  public void testComplete() {
    // Given
    inv = new AsyncExecution(callable, new RetryPolicy(), scheduler, future, null);

    // When
    inv.complete();

    // Then
    assertEquals(inv.getExecutions(), 1);
    assertTrue(inv.isComplete());
    assertNull(inv.getLastResult());
    assertNull(inv.getLastFailure());
    verify(future).complete(null, null, true);
  }

  public void testCompleteForResult() {
    // Given
    inv = new AsyncExecution(callable, new RetryPolicy().retryWhen(null), scheduler, future, null);

    // When / Then
    assertFalse(inv.complete(null));
    assertTrue(inv.complete(true));

    // Then
    assertEquals(inv.getExecutions(), 1);
    assertTrue(inv.isComplete());
    assertEquals(inv.getLastResult(), Boolean.TRUE);
    assertNull(inv.getLastFailure());
    verify(future).complete(true, null, true);
  }

  public void testGetAttemptCount() {
    inv = new AsyncExecution(callable, new RetryPolicy(), scheduler, future, null);
    inv.retryOn(e);
    inv.prepare();
    inv.retryOn(e);
    assertEquals(inv.getExecutions(), 2);
  }

  public void testRetryForResult() {
    // Given retry for null
    inv = new AsyncExecution(callable, new RetryPolicy().retryWhen(null), scheduler, future, null);

    // When / Then
    assertFalse(inv.complete(null));
    assertTrue(inv.retryFor(null));
    inv.prepare();
    assertFalse(inv.retryFor(1));

    // Then
    assertEquals(inv.getExecutions(), 2);
    assertTrue(inv.isComplete());
    assertEquals(inv.getLastResult(), Integer.valueOf(1));
    assertNull(inv.getLastFailure());
    verifyScheduler(1);
    verify(future).complete(1, null, true);

    // Given 2 max retries
    inv = new AsyncExecution(callable, new RetryPolicy().retryWhen(null).withMaxRetries(1), scheduler, future, null);

    // When / Then
    resetMocks();
    assertFalse(inv.complete(null));
    inv.prepare();
    assertTrue(inv.retryFor(null));
    inv.prepare();
    assertFalse(inv.retryFor(null));

    // Then
    assertEquals(inv.getExecutions(), 2);
    assertTrue(inv.isComplete());
    assertNull(inv.getLastResult());
    assertNull(inv.getLastFailure());
    verifyScheduler(1);
    verify(future).complete(null, null, false);
  }

  public void testRetryForResultAndThrowable() {
    // Given retry for null
    inv = new AsyncExecution(callable, new RetryPolicy().retryWhen(null), scheduler, future, null);

    // When / Then
    assertFalse(inv.complete(null));
    assertTrue(inv.retryFor(null, null));
    inv.prepare();
    assertTrue(inv.retryFor(1, new IllegalArgumentException()));
    inv.prepare();
    assertFalse(inv.retryFor(1, null));

    // Then
    assertEquals(inv.getExecutions(), 3);
    assertTrue(inv.isComplete());
    assertEquals(inv.getLastResult(), Integer.valueOf(1));
    assertNull(inv.getLastFailure());
    verifyScheduler(2);
    verify(future).complete(1, null, true);

    // Given 2 max retries
    inv = new AsyncExecution(callable, new RetryPolicy().retryWhen(null).withMaxRetries(1), scheduler, future, null);

    // When / Then
    resetMocks();
    assertFalse(inv.complete(null));
    assertTrue(inv.retryFor(null, e));
    inv.prepare();
    assertFalse(inv.retryFor(null, e));

    // Then
    assertEquals(inv.getExecutions(), 2);
    assertTrue(inv.isComplete());
    assertNull(inv.getLastResult());
    assertEquals(inv.getLastFailure(), e);
    verifyScheduler(1);
    verify(future).complete(null, e, false);
  }

  @SuppressWarnings("unchecked")
  public void testRetryOn() {
    // Given retry on IllegalArgumentException
    inv = new AsyncExecution(callable, new RetryPolicy().retryOn(IllegalArgumentException.class), scheduler, future,
        null);

    // When / Then
    assertTrue(inv.retryOn(new IllegalArgumentException()));
    inv.prepare();
    assertFalse(inv.retryOn(e));

    // Then
    assertEquals(inv.getExecutions(), 2);
    assertTrue(inv.isComplete());
    assertNull(inv.getLastResult());
    assertEquals(inv.getLastFailure(), e);
    verifyScheduler(1);
    verify(future).complete(null, e, false);

    // Given 2 max retries
    inv = new AsyncExecution(callable, new RetryPolicy().withMaxRetries(1), scheduler, future, null);

    // When / Then
    resetMocks();
    assertTrue(inv.retryOn(e));
    inv.prepare();
    assertFalse(inv.retryOn(e));

    // Then
    assertEquals(inv.getExecutions(), 2);
    assertTrue(inv.isComplete());
    assertNull(inv.getLastResult());
    assertEquals(inv.getLastFailure(), e);
    verifyScheduler(1);
    verify(future).complete(null, e, false);
  }

  @Test(expectedExceptions = IllegalStateException.class)
  public void shouldThrowOnRetryWhenAlreadyComplete() {
    inv = new AsyncExecution(callable, new RetryPolicy(), scheduler, future, null);
    inv.complete();
    inv.retryOn(e);
  }

  public void testCompleteOrRetry() {
    // Given retry on IllegalArgumentException
    inv = new AsyncExecution(callable, new RetryPolicy(), scheduler, future, null);

    // When / Then
    inv.completeOrRetry(null, e);
    assertFalse(inv.isComplete());
    inv.completeOrRetry(null, null);

    // Then
    assertEquals(inv.getExecutions(), 2);
    assertTrue(inv.isComplete());
    assertNull(inv.getLastResult());
    assertNull(inv.getLastFailure());
    verifyScheduler(1);
    verify(future).complete(null, null, true);
  }

  @SuppressWarnings("unchecked")
  private void resetMocks() {
    reset(scheduler);
    reset(future);
    reset(callable);
  }

  private void verifyScheduler(int executions) {
    verify(scheduler, times(executions)).schedule(any(Callable.class), any(Long.class), any(TimeUnit.class));
  }
}
