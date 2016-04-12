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
  AsyncExecution exec;
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
    exec = new AsyncExecution(callable, new RetryPolicy(), scheduler, future, null);

    // When
    exec.complete();

    // Then
    assertEquals(exec.getExecutions(), 1);
    assertTrue(exec.isComplete());
    assertNull(exec.getLastResult());
    assertNull(exec.getLastFailure());
    verify(future).complete(null, null, true);
  }

  public void testCompleteForResult() {
    // Given
    exec = new AsyncExecution(callable, new RetryPolicy().retryWhen(null), scheduler, future, null);

    // When / Then
    assertFalse(exec.complete(null));
    assertTrue(exec.complete(true));

    // Then
    assertEquals(exec.getExecutions(), 1);
    assertTrue(exec.isComplete());
    assertEquals(exec.getLastResult(), Boolean.TRUE);
    assertNull(exec.getLastFailure());
    verify(future).complete(true, null, true);
  }

  public void testGetAttemptCount() {
    exec = new AsyncExecution(callable, new RetryPolicy(), scheduler, future, null);
    exec.retryOn(e);
    exec.prepare();
    exec.retryOn(e);
    assertEquals(exec.getExecutions(), 2);
  }

  public void testRetryForResult() {
    // Given retry for null
    exec = new AsyncExecution(callable, new RetryPolicy().retryWhen(null), scheduler, future, null);

    // When / Then
    assertFalse(exec.complete(null));
    assertTrue(exec.retryFor(null));
    exec.prepare();
    assertFalse(exec.retryFor(1));

    // Then
    assertEquals(exec.getExecutions(), 2);
    assertTrue(exec.isComplete());
    assertEquals(exec.getLastResult(), Integer.valueOf(1));
    assertNull(exec.getLastFailure());
    verifyScheduler(1);
    verify(future).complete(1, null, true);

    // Given 2 max retries
    exec = new AsyncExecution(callable, new RetryPolicy().retryWhen(null).withMaxRetries(1), scheduler, future, null);

    // When / Then
    resetMocks();
    assertFalse(exec.complete(null));
    exec.prepare();
    assertTrue(exec.retryFor(null));
    exec.prepare();
    assertFalse(exec.retryFor(null));

    // Then
    assertEquals(exec.getExecutions(), 2);
    assertTrue(exec.isComplete());
    assertNull(exec.getLastResult());
    assertNull(exec.getLastFailure());
    verifyScheduler(1);
    verify(future).complete(null, null, false);
  }

  public void testRetryForResultAndThrowable() {
    // Given retry for null
    exec = new AsyncExecution(callable, new RetryPolicy().retryWhen(null), scheduler, future, null);

    // When / Then
    assertFalse(exec.complete(null));
    assertTrue(exec.retryFor(null, null));
    exec.prepare();
    assertTrue(exec.retryFor(1, new IllegalArgumentException()));
    exec.prepare();
    assertFalse(exec.retryFor(1, null));

    // Then
    assertEquals(exec.getExecutions(), 3);
    assertTrue(exec.isComplete());
    assertEquals(exec.getLastResult(), Integer.valueOf(1));
    assertNull(exec.getLastFailure());
    verifyScheduler(2);
    verify(future).complete(1, null, true);

    // Given 2 max retries
    exec = new AsyncExecution(callable, new RetryPolicy().retryWhen(null).withMaxRetries(1), scheduler, future, null);

    // When / Then
    resetMocks();
    assertFalse(exec.complete(null));
    assertTrue(exec.retryFor(null, e));
    exec.prepare();
    assertFalse(exec.retryFor(null, e));

    // Then
    assertEquals(exec.getExecutions(), 2);
    assertTrue(exec.isComplete());
    assertNull(exec.getLastResult());
    assertEquals(exec.getLastFailure(), e);
    verifyScheduler(1);
    verify(future).complete(null, e, false);
  }

  @SuppressWarnings("unchecked")
  public void testRetryOn() {
    // Given retry on IllegalArgumentException
    exec = new AsyncExecution(callable, new RetryPolicy().retryOn(IllegalArgumentException.class), scheduler, future,
        null);

    // When / Then
    assertTrue(exec.retryOn(new IllegalArgumentException()));
    exec.prepare();
    assertFalse(exec.retryOn(e));

    // Then
    assertEquals(exec.getExecutions(), 2);
    assertTrue(exec.isComplete());
    assertNull(exec.getLastResult());
    assertEquals(exec.getLastFailure(), e);
    verifyScheduler(1);
    verify(future).complete(null, e, false);

    // Given 2 max retries
    exec = new AsyncExecution(callable, new RetryPolicy().withMaxRetries(1), scheduler, future, null);

    // When / Then
    resetMocks();
    assertTrue(exec.retryOn(e));
    exec.prepare();
    assertFalse(exec.retryOn(e));

    // Then
    assertEquals(exec.getExecutions(), 2);
    assertTrue(exec.isComplete());
    assertNull(exec.getLastResult());
    assertEquals(exec.getLastFailure(), e);
    verifyScheduler(1);
    verify(future).complete(null, e, false);
  }

  @Test(expectedExceptions = IllegalStateException.class)
  public void shouldThrowOnRetryWhenAlreadyComplete() {
    exec = new AsyncExecution(callable, new RetryPolicy(), scheduler, future, null);
    exec.complete();
    exec.retryOn(e);
  }

  public void testCompleteOrRetry() {
    // Given retry on IllegalArgumentException
    exec = new AsyncExecution(callable, new RetryPolicy(), scheduler, future, null);

    // When / Then
    exec.completeOrRetry(null, e);
    assertFalse(exec.isComplete());
    exec.completeOrRetry(null, null);

    // Then
    assertEquals(exec.getExecutions(), 2);
    assertTrue(exec.isComplete());
    assertNull(exec.getLastResult());
    assertNull(exec.getLastFailure());
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
