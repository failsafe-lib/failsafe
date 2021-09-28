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

import net.jodah.failsafe.spi.*;
import net.jodah.failsafe.testing.Testing;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

@Test
public class AsyncExecutionTest extends Testing {
  Function<AsyncExecutionInternal<Object>, CompletableFuture<ExecutionResult<Object>>> innerFn = Functions.getPromise(
    ctx -> null, null);
  ConnectException e = new ConnectException();
  AsyncExecutionInternal<Object> exec;
  FailsafeFuture<Object> future;
  Callable<Object> callable;
  Scheduler scheduler;

  @BeforeMethod
  @SuppressWarnings("unchecked")
  void beforeMethod() {
    scheduler = mock(Scheduler.class);
    when(scheduler.schedule(any(Callable.class), anyLong(), any(TimeUnit.class))).thenReturn(
      new DefaultScheduledFuture<>());
    future = mock(FailsafeFuture.class);
    callable = mock(Callable.class);
  }

  public void testCompleteForNoResult() {
    // Given
    exec = new AsyncExecutionImpl<>(Arrays.asList(new RetryPolicy<>()), scheduler, future, true, innerFn);

    // When
    exec.preExecute();
    exec.complete();

    // Then
    assertEquals(exec.getAttemptCount(), 1);
    assertEquals(exec.getExecutionCount(), 1);
    assertTrue(exec.isComplete());
    assertNull(exec.getLastResult());
    assertNull(exec.getLastFailure());
    verify(future).completeResult(ExecutionResult.none());
  }

  public void testRetryForResult() {
    // Given retry for null
    exec = new AsyncExecutionImpl<>(Arrays.asList(new RetryPolicy<>().handleResult(null)), scheduler, future, true,
      innerFn);

    // When / Then
    exec.preExecute();
    exec.recordResult(null);
    assertFalse(exec.isComplete());
    exec = exec.copy();
    exec.preExecute();
    exec.recordResult(null);
    assertFalse(exec.isComplete());
    exec = exec.copy();
    exec.preExecute();
    exec.recordResult(1);
    assertTrue(exec.isComplete());

    // Then
    assertEquals(exec.getAttemptCount(), 3);
    assertEquals(exec.getExecutionCount(), 3);
    assertTrue(exec.isComplete());
    assertEquals(exec.getLastResult(), 1);
    assertNull(exec.getLastFailure());
    verifyScheduler(2);
    verify(future).completeResult(ExecutionResult.success(1));
  }

  public void testRetryForThrowable() {
    // Given retry on IllegalArgumentException
    exec = new AsyncExecutionImpl<>(Arrays.asList(new RetryPolicy<>().handle(IllegalArgumentException.class)),
      scheduler, future, true, innerFn);

    // When / Then
    exec.preExecute();
    exec.recordFailure(new IllegalArgumentException());
    assertFalse(exec.isComplete());
    exec = exec.copy();
    exec.preExecute();
    exec.recordFailure(e);
    assertTrue(exec.isComplete());

    // Then
    assertEquals(exec.getAttemptCount(), 2);
    assertEquals(exec.getExecutionCount(), 2);
    assertTrue(exec.isComplete());
    assertNull(exec.getLastResult());
    assertEquals(exec.getLastFailure(), e);
    verifyScheduler(1);
    verify(future).completeResult(ExecutionResult.failure(e));
  }

  public void testRetryForResultAndThrowable() {
    // Given retry for null
    exec = new AsyncExecutionImpl<>(Arrays.asList(new RetryPolicy<>().withMaxAttempts(10).handleResult(null)),
      scheduler, future, true, innerFn);

    // When / Then
    exec.preExecute();
    exec.recordResult(null);
    assertFalse(exec.isComplete());
    exec = exec.copy();
    exec.preExecute();
    exec.record(null, null);
    assertFalse(exec.isComplete());
    exec = exec.copy();
    exec.preExecute();
    exec.record(1, new IllegalArgumentException());
    assertFalse(exec.isComplete());
    exec = exec.copy();
    exec.preExecute();
    exec.record(1, null);
    assertTrue(exec.isComplete());

    // Then
    assertEquals(exec.getAttemptCount(), 4);
    assertEquals(exec.getExecutionCount(), 4);
    assertTrue(exec.isComplete());
    assertEquals(exec.getLastResult(), 1);
    assertNull(exec.getLastFailure());
    verifyScheduler(3);
    verify(future).completeResult(ExecutionResult.success(1));
  }

  public void testGetAttemptCount() {
    // Given
    exec = new AsyncExecutionImpl<>(Arrays.asList(new RetryPolicy<>()), scheduler, future, true, innerFn);

    // When
    exec.preExecute();
    exec.recordFailure(e);
    exec = exec.copy();
    exec.preExecute();
    exec.recordFailure(e);

    // Then
    assertEquals(exec.getAttemptCount(), 2);
    assertEquals(exec.getExecutionCount(), 2);
  }

  @Test(expectedExceptions = IllegalStateException.class)
  public void shouldThrowOnRetryWhenAlreadyComplete() {
    exec = new AsyncExecutionImpl<>(Arrays.asList(new RetryPolicy<>()), scheduler, future, true, innerFn);
    exec.complete();
    exec.preExecute();
    exec.recordFailure(e);
  }

  public void testCompleteOrRetry() {
    // Given retry on IllegalArgumentException
    exec = new AsyncExecutionImpl<>(Arrays.asList(new RetryPolicy<>()), scheduler, future, true, innerFn);

    // When / Then
    exec.preExecute();
    exec.record(null, e);
    assertFalse(exec.isComplete());
    exec = exec.copy();
    exec.preExecute();
    exec.record(null, null);

    // Then
    assertEquals(exec.getAttemptCount(), 2);
    assertEquals(exec.getExecutionCount(), 2);
    assertTrue(exec.isComplete());
    assertNull(exec.getLastResult());
    assertNull(exec.getLastFailure());
    verifyScheduler(1);
    verify(future).completeResult(ExecutionResult.none());
  }


  private void verifyScheduler(int executions) {
    verify(scheduler, times(executions)).schedule(any(Callable.class), any(Long.class), any(TimeUnit.class));
  }
}