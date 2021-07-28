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

import net.jodah.failsafe.Testing.Stats;
import net.jodah.failsafe.Testing.SyncExecutor;
import net.jodah.failsafe.util.concurrent.Scheduler;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.ConnectException;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static net.jodah.failsafe.Testing.testAsyncFailure;
import static net.jodah.failsafe.Testing.withStats;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

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

  public void testCompleteForNoResult() {
    // Given
    exec = new AsyncExecution(scheduler, future, executorFor(new RetryPolicy<>()));

    // When
    exec.complete();

    // Then
    assertEquals(exec.getAttemptCount(), 1);
    assertEquals(exec.getExecutionCount(), 1);
    assertTrue(exec.isComplete());
    assertNull(exec.getLastResult());
    assertNull(exec.getLastFailure());
    verify(future).completeResult(ExecutionResult.NONE);
  }

  public void testCompleteForResult() {
    // Given
    exec = new AsyncExecution(scheduler, future, executorFor(new RetryPolicy<>().handleResult(null)));

    // When / Then
    assertFalse(exec.complete(null));
    exec.preExecute();
    assertTrue(exec.complete(true));

    // Then
    assertEquals(exec.getAttemptCount(), 2);
    assertEquals(exec.getExecutionCount(), 2);
    assertTrue(exec.isComplete());
    assertEquals(exec.getLastResult(), Boolean.TRUE);
    assertNull(exec.getLastFailure());
    verify(future).completeResult(ExecutionResult.success(true));
  }

  public void testGetAttemptCount() {
    // Given
    exec = new AsyncExecution(scheduler, future, executorFor(new RetryPolicy<>()));
    exec.inject(Functions.getPromise(ctx -> null, exec), true);

    // When
    exec.retryOn(e);
    exec.preExecute();
    exec.retryOn(e);

    // Then
    assertEquals(exec.getAttemptCount(), 2);
    assertEquals(exec.getExecutionCount(), 2);
  }

  public void testRetryForResult() {
    // Given rpRetry for null
    exec = new AsyncExecution(scheduler, future, executorFor(new RetryPolicy<>().handleResult(null)));
    exec.inject(Functions.getPromise(ctx -> null, exec), true);

    // When / Then
    assertFalse(exec.complete(null));
    exec.preExecute();
    assertTrue(exec.retryFor(null));
    exec.preExecute();
    assertFalse(exec.retryFor(1));

    // Then
    assertEquals(exec.getAttemptCount(), 3);
    assertEquals(exec.getExecutionCount(), 3);
    assertTrue(exec.isComplete());
    assertEquals(exec.getLastResult(), Integer.valueOf(1));
    assertNull(exec.getLastFailure());
    verifyScheduler(1);
    verify(future).completeResult(ExecutionResult.success(1));

    // Given 2 max retries
    exec = new AsyncExecution(scheduler, future, executorFor(new RetryPolicy<>().handleResult(null).withMaxRetries(2)));
    exec.inject(Functions.getPromise(ctx -> null, exec), true);

    // When / Then
    resetMocks();
    assertFalse(exec.complete(null));
    exec.preExecute();
    assertTrue(exec.retryFor(null));
    exec.preExecute();
    assertFalse(exec.retryFor(null));

    // Then
    assertEquals(exec.getAttemptCount(), 3);
    assertEquals(exec.getExecutionCount(), 3);
    assertTrue(exec.isComplete());
    assertNull(exec.getLastResult());
    assertNull(exec.getLastFailure());
    verifyScheduler(1);
    verify(future).completeResult(ExecutionResult.NONE);
  }

  public void testRetryForResultAndThrowable() {
    // Given rpRetry for null
    exec = new AsyncExecution(scheduler, future,
      executorFor(new RetryPolicy<>().withMaxAttempts(10).handleResult(null)));
    exec.inject(Functions.getPromise(ctx -> null, exec), true);

    // When / Then
    assertFalse(exec.complete(null));
    exec.preExecute();
    assertTrue(exec.retryFor(null, null));
    exec.preExecute();
    assertTrue(exec.retryFor(1, new IllegalArgumentException()));
    exec.preExecute();
    assertFalse(exec.retryFor(1, null));

    // Then
    assertEquals(exec.getAttemptCount(), 4);
    assertEquals(exec.getExecutionCount(), 4);
    assertTrue(exec.isComplete());
    assertEquals(exec.getLastResult(), Integer.valueOf(1));
    assertNull(exec.getLastFailure());
    verifyScheduler(2);
    verify(future).completeResult(ExecutionResult.success(1));

    // Given 2 max retries
    exec = new AsyncExecution(scheduler, future, executorFor(new RetryPolicy<>().handleResult(null).withMaxRetries(2)));
    exec.inject(Functions.getPromise(ctx -> null, exec), true);

    // When / Then
    resetMocks();
    assertFalse(exec.complete(null));
    exec.preExecute();
    assertTrue(exec.retryFor(null, e));
    exec.preExecute();
    assertFalse(exec.retryFor(null, e));

    // Then
    assertEquals(exec.getAttemptCount(), 3);
    assertEquals(exec.getExecutionCount(), 3);
    assertTrue(exec.isComplete());
    assertNull(exec.getLastResult());
    assertEquals(exec.getLastFailure(), e);
    verifyScheduler(1);
    verify(future).completeResult(ExecutionResult.failure(e));
  }

  public void testRetryOn() {
    // Given rpRetry on IllegalArgumentException
    exec = new AsyncExecution(scheduler, future,
      executorFor(new RetryPolicy<>().handle(IllegalArgumentException.class)));
    exec.inject(Functions.getPromise(ctx -> null, exec), true);

    // When / Then
    assertTrue(exec.retryOn(new IllegalArgumentException()));
    exec.preExecute();
    assertFalse(exec.retryOn(e));

    // Then
    assertEquals(exec.getAttemptCount(), 2);
    assertEquals(exec.getExecutionCount(), 2);
    assertTrue(exec.isComplete());
    assertNull(exec.getLastResult());
    assertEquals(exec.getLastFailure(), e);
    verifyScheduler(1);
    verify(future).completeResult(ExecutionResult.failure(e));

    // Given 2 max retries
    exec = new AsyncExecution(scheduler, future, executorFor(new RetryPolicy<>().withMaxRetries(1)));
    exec.inject(Functions.getPromise(ctx -> null, exec), true);

    // When / Then
    resetMocks();
    assertTrue(exec.retryOn(e));
    exec.preExecute();
    assertFalse(exec.retryOn(e));

    // Then
    assertEquals(exec.getAttemptCount(), 2);
    assertEquals(exec.getExecutionCount(), 2);
    assertTrue(exec.isComplete());
    assertNull(exec.getLastResult());
    assertEquals(exec.getLastFailure(), e);
    verifyScheduler(1);
    verify(future).completeResult(ExecutionResult.failure(e));
  }

  @Test(expectedExceptions = IllegalStateException.class)
  public void shouldThrowOnRetryWhenAlreadyComplete() {
    exec = new AsyncExecution(scheduler, future, executorFor(new RetryPolicy<>()));
    exec.complete();
    exec.preExecute();
    exec.retryOn(e);
  }

  public void testCompleteOrRetry() {
    // Given rpRetry on IllegalArgumentException
    exec = new AsyncExecution(scheduler, future, executorFor(new RetryPolicy<>()));
    exec.inject(Functions.getPromise(ctx -> null, exec), true);

    // When / Then
    exec.completeOrHandle(null, e);
    assertFalse(exec.isComplete());
    exec.preExecute();
    exec.completeOrHandle(null, null);

    // Then
    assertEquals(exec.getAttemptCount(), 2);
    assertEquals(exec.getExecutionCount(), 2);
    assertTrue(exec.isComplete());
    assertNull(exec.getLastResult());
    assertNull(exec.getLastFailure());
    verifyScheduler(1);
    verify(future).completeResult(ExecutionResult.NONE);
  }

  public void testExecutor() {
    Stats rpStats = new Stats();
    RetryPolicy<Object> rp = withStats(new RetryPolicy<>(), rpStats, false);

    testAsyncFailure(Failsafe.with(rp).with(new SyncExecutor()), () -> {
      throw new IllegalStateException();
    }, e -> {
      assertEquals(e.getAttemptCount(), 3);
      assertEquals(e.getExecutionCount(), 3);
      assertEquals(rpStats.failedAttemptCount, 3);
      assertEquals(rpStats.retryCount, 2);
    }, IllegalStateException.class);
  }

  @SuppressWarnings("unchecked")
  private void resetMocks() {
    reset(scheduler);
    reset(future);
    reset(callable);
  }

  private static <T> FailsafeExecutor<T> executorFor(RetryPolicy<T> retryPolicy) {
    return new FailsafeExecutor<>(Arrays.asList(retryPolicy));
  }

  private void verifyScheduler(int executions) {
    verify(scheduler, times(executions)).schedule(any(Callable.class), any(Long.class), any(TimeUnit.class));
  }
}