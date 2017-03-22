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

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.net.ConnectException;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import net.jodah.failsafe.util.concurrent.Scheduler;

@Test
public class AsyncExecutionTest {
  ConnectException e = new ConnectException();
  AsyncExecution exec;
  FailsafeFuture<Object> future;
  Callable<Object> callable;
  Scheduler scheduler;

  @BeforeMethod
  @SuppressWarnings("unchecked")
  void beforeMethod() {
    scheduler = mock(Scheduler.class);
    future = mock(FailsafeFuture.class);
    callable = mock(Callable.class);
  }

  public void testComplete() {
    // Given
    exec = new AsyncExecution(callable, scheduler, future, configFor(new RetryPolicy()));

    // When
    exec.complete();

    // Then
    assertEquals(exec.getExecutions(), 1);
    assertTrue(exec.isComplete());
    assertNull(exec.getLastResult());
    assertNull(exec.getLastFailure());
    verify(future).complete(null, null, null, true);
  }

  public void testCompleteForResult() {
    // Given
    exec = new AsyncExecution(callable, scheduler, future, configFor(new RetryPolicy().retryWhen(null)));

    // When / Then
    assertFalse(exec.complete(null));
    exec.before();
    assertTrue(exec.complete(true));

    // Then
    assertEquals(exec.getExecutions(), 2);
    assertTrue(exec.isComplete());
    assertEquals(exec.getLastResult(), Boolean.TRUE);
    assertNull(exec.getLastFailure());
    verify(future).complete(true, null, null, true);
  }

  public void testGetAttemptCount() {
    exec = new AsyncExecution(callable, scheduler, future, configFor(new RetryPolicy()));
    exec.retryOn(e);
    exec.before();
    exec.retryOn(e);
    assertEquals(exec.getExecutions(), 2);
  }

  public void testRetryForResult() {
    // Given retry for null
    exec = new AsyncExecution(callable, scheduler, future, configFor(new RetryPolicy().retryWhen(null)));

    // When / Then
    assertFalse(exec.complete(null));
    exec.before();
    assertTrue(exec.retryFor(null));
    exec.before();
    assertFalse(exec.retryFor(1));

    // Then
    assertEquals(exec.getExecutions(), 3);
    assertTrue(exec.isComplete());
    assertEquals(exec.getLastResult(), Integer.valueOf(1));
    assertNull(exec.getLastFailure());
    verifyScheduler(1);
    verify(future).complete(1, null, null, true);

    // Given 2 max retries
    exec = new AsyncExecution(callable, scheduler, future,
        configFor(new RetryPolicy().retryWhen(null).withMaxRetries(2)));

    // When / Then
    resetMocks();
    assertFalse(exec.complete(null));
    exec.before();
    assertTrue(exec.retryFor(null));
    exec.before();
    assertFalse(exec.retryFor(null));

    // Then
    assertEquals(exec.getExecutions(), 3);
    assertTrue(exec.isComplete());
    assertNull(exec.getLastResult());
    assertNull(exec.getLastFailure());
    verifyScheduler(1);
    verify(future).complete(null, null, null, false);
  }

  public void testRetryForResultAndThrowable() {
    // Given retry for null
    exec = new AsyncExecution(callable, scheduler, future, configFor(new RetryPolicy().retryWhen(null)));

    // When / Then
    assertFalse(exec.complete(null));
    exec.before();
    assertTrue(exec.retryFor(null, null));
    exec.before();
    assertTrue(exec.retryFor(1, new IllegalArgumentException()));
    exec.before();
    assertFalse(exec.retryFor(1, null));

    // Then
    assertEquals(exec.getExecutions(), 4);
    assertTrue(exec.isComplete());
    assertEquals(exec.getLastResult(), Integer.valueOf(1));
    assertNull(exec.getLastFailure());
    verifyScheduler(2);
    verify(future).complete(1, null, null, true);

    // Given 2 max retries
    exec = new AsyncExecution(callable, scheduler, future,
        configFor(new RetryPolicy().retryWhen(null).withMaxRetries(2)));

    // When / Then
    resetMocks();
    assertFalse(exec.complete(null));
    exec.before();
    assertTrue(exec.retryFor(null, e));
    exec.before();
    assertFalse(exec.retryFor(null, e));

    // Then
    assertEquals(exec.getExecutions(), 3);
    assertTrue(exec.isComplete());
    assertNull(exec.getLastResult());
    assertEquals(exec.getLastFailure(), e);
    verifyScheduler(1);
    verify(future).complete(null, e, null, false);
  }

  public void testRetryOn() {
    // Given retry on IllegalArgumentException
    exec = new AsyncExecution(callable, scheduler, future,
        configFor(new RetryPolicy().retryOn(IllegalArgumentException.class)));

    // When / Then
    assertTrue(exec.retryOn(new IllegalArgumentException()));
    exec.before();
    assertFalse(exec.retryOn(e));

    // Then
    assertEquals(exec.getExecutions(), 2);
    assertTrue(exec.isComplete());
    assertNull(exec.getLastResult());
    assertEquals(exec.getLastFailure(), e);
    verifyScheduler(1);
    verify(future).complete(null, e, null, false);

    // Given 2 max retries
    exec = new AsyncExecution(callable, scheduler, future, configFor(new RetryPolicy().withMaxRetries(1)));

    // When / Then
    resetMocks();
    assertTrue(exec.retryOn(e));
    exec.before();
    assertFalse(exec.retryOn(e));

    // Then
    assertEquals(exec.getExecutions(), 2);
    assertTrue(exec.isComplete());
    assertNull(exec.getLastResult());
    assertEquals(exec.getLastFailure(), e);
    verifyScheduler(1);
    verify(future).complete(null, e, null, false);
  }

  @Test(expectedExceptions = IllegalStateException.class)
  public void shouldThrowOnRetryWhenAlreadyComplete() {
    exec = new AsyncExecution(callable, scheduler, future, configFor(new RetryPolicy()));
    exec.complete();
    exec.before();
    exec.retryOn(e);
  }

  public void testCompleteOrRetry() {
    // Given retry on IllegalArgumentException
    exec = new AsyncExecution(callable, scheduler, future, configFor(new RetryPolicy()));

    // When / Then
    exec.completeOrRetry(null, e);
    assertFalse(exec.isComplete());
    exec.before();
    exec.completeOrRetry(null, null);

    // Then
    assertEquals(exec.getExecutions(), 2);
    assertTrue(exec.isComplete());
    assertNull(exec.getLastResult());
    assertNull(exec.getLastFailure());
    verifyScheduler(1);
    verify(future).complete(null, null, null, true);
  }

  @SuppressWarnings("unchecked")
  private void resetMocks() {
    reset(scheduler);
    reset(future);
    reset(callable);
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  static <T> FailsafeConfig<T, FailsafeConfig<T, ?>> configFor(RetryPolicy retryPolicy) {
    return (FailsafeConfig<T, FailsafeConfig<T, ?>>) new FailsafeConfig().with(retryPolicy);
  }

  private void verifyScheduler(int executions) {
    verify(scheduler, times(executions)).schedule(any(Callable.class), any(Long.class), any(TimeUnit.class));
  }
}
