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

import net.jodah.concurrentunit.Waiter;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static net.jodah.failsafe.Testing.failures;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

@Test
public class FailsafeConfigTest {
  private Service service = mock(Service.class);
  ExecutorService executor;

  ListenerCounter abort;
  ListenerCounter complete;
  ListenerCounter failedAttempt;
  ListenerCounter failure;
  ListenerCounter retriesExceeded;
  ListenerCounter retry;
  ListenerCounter success;

  static class ListenerCounter {
    Waiter waiter = new Waiter();
    int asyncListeners;
    /** Per listener invocations */
    Map<Object, AtomicInteger> invocations = new ConcurrentHashMap<>();

    /** Records a sync invocation of the {@code listener}. */
    void sync(Object listener) {
      invocations.computeIfAbsent(listener, l -> new AtomicInteger()).incrementAndGet();
    }

    /** Records a sync invocation of the {@code listener} and asserts the {@code context}'s execution count. */
    void sync(Object listener, ExecutionContext context) {
      waiter.assertEquals(context.getExecutions(),
          invocations.computeIfAbsent(listener, l -> new AtomicInteger()).incrementAndGet());
    }

    /** Records an async invocation of the {@code listener}. */
    synchronized void async(Object listener) {
      sync(listener);
      waiter.resume();
    }

    /** Records an async invocation of the {@code listener} and asserts the {@code context}'s execution count. */
    synchronized void async(Object listener, ExecutionContext context) {
      sync(listener, context);
      waiter.resume();
    }

    /** Waits for the expected async invocations and asserts the expected {@code expectedInvocations}. */
    void assertEquals(int expectedInvocations) throws Throwable {
      if (expectedInvocations > 0)
        waiter.await(1000, asyncListeners * expectedInvocations);
      if (invocations.isEmpty())
        Assert.assertEquals(expectedInvocations, 0);
      for (AtomicInteger counter : invocations.values())
        Assert.assertEquals(counter.get(), expectedInvocations);
    }
  }

  public interface Service {
    boolean connect();
  }

  @BeforeMethod
  void beforeMethod() {
    executor = Executors.newFixedThreadPool(2);
    reset(service);

    abort = new ListenerCounter();
    complete = new ListenerCounter();
    failedAttempt = new ListenerCounter();
    failure = new ListenerCounter();
    retriesExceeded = new ListenerCounter();
    retry = new ListenerCounter();
    success = new ListenerCounter();
  }

  @AfterMethod
  void afterMethod() throws Throwable {
    executor.shutdownNow();
    executor.awaitTermination(5, TimeUnit.SECONDS);
  }

  <T> SyncFailsafe<T> registerListeners(SyncFailsafe<T> failsafe) {
    failsafe.onAbort(e -> abort.sync(1));
    failsafe.onAbort((r, e) -> abort.sync(2));
    failsafe.onAbort((r, e, c) -> abort.sync(3));
    failsafe.onAbortAsync(e -> abort.async(4), executor);
    failsafe.onAbortAsync((r, e) -> abort.async(5), executor);
    failsafe.onAbortAsync((r, e, c) -> abort.async(6), executor);
    abort.asyncListeners = 3;

    failsafe.onComplete((r, f) -> complete.sync(1));
    failsafe.onComplete((r, f, s) -> complete.sync(2));
    failsafe.onCompleteAsync((r, f) -> complete.async(3), executor);
    failsafe.onCompleteAsync((r, f, c) -> complete.async(4), executor);
    complete.asyncListeners = 2;

    failsafe.onFailedAttempt(e -> failedAttempt.sync(1));
    failsafe.onFailedAttempt((r, f) -> failedAttempt.sync(2));
    failsafe.onFailedAttempt((r, f, c) -> failedAttempt.sync(3, c));
    failsafe.onFailedAttemptAsync(e -> failedAttempt.async(4), executor);
    failsafe.onFailedAttemptAsync((r, f) -> failedAttempt.async(5), executor);
    failsafe.onFailedAttemptAsync((r, f, c) -> failedAttempt.async(6, c), executor);
    failedAttempt.asyncListeners = 3;

    failsafe.onFailure(e -> failure.sync(1));
    failsafe.onFailure((r, f) -> failure.sync(2));
    failsafe.onFailure((r, f, s) -> failure.sync(3));
    failsafe.onFailureAsync(e -> failure.async(4), executor);
    failsafe.onFailureAsync((r, f) -> failure.async(5), executor);
    failsafe.onFailureAsync((r, f, s) -> failure.async(6), executor);
    failure.asyncListeners = 3;

    failsafe.onRetriesExceeded(e -> retriesExceeded.sync(1));
    failsafe.onRetriesExceeded((r, f) -> retriesExceeded.sync(2));
    failsafe.onRetriesExceededAsync(e -> retriesExceeded.async(3), executor);
    failsafe.onRetriesExceededAsync((r, f) -> retriesExceeded.async(4), executor);
    retriesExceeded.asyncListeners = 2;

    failsafe.onRetry(e -> retry.sync(1));
    failsafe.onRetry((r, f) -> retry.sync(2));
    failsafe.onRetry((r, f, s) -> retry.sync(3, s));
    failsafe.onRetryAsync(e -> retry.async(4), executor);
    failsafe.onRetryAsync((r, f) -> retry.async(5), executor);
    failsafe.onRetryAsync((r, f, s) -> retry.async(6), executor);
    retry.asyncListeners = 3;

    failsafe.onSuccess((r) -> success.sync(1));
    failsafe.onSuccess((r, s) -> success.sync(2));
    failsafe.onSuccessAsync((r) -> success.async(3), executor);
    failsafe.onSuccessAsync((r, s) -> success.async(4), executor);
    success.asyncListeners = 2;

    return failsafe;
  }

  /**
   * Asserts that listeners are called the expected number of times for a successful completion.
   */
  public void testListenersForSuccess() throws Throwable {
    Callable<Boolean> callable = () -> service.connect();

    // Given - Fail 4 times then succeed
    when(service.connect()).thenThrow(failures(2, new IllegalStateException())).thenReturn(false, false, true);
    RetryPolicy retryPolicy = new RetryPolicy().retryWhen(false);

    // When
    registerListeners(Failsafe.with(retryPolicy)).get(callable);

    // Then
    abort.assertEquals(0);
    complete.assertEquals(1);
    failedAttempt.assertEquals(4);
    failure.assertEquals(0);
    retriesExceeded.assertEquals(0);
    retry.assertEquals(4);
    success.assertEquals(1);
  }

  /**
   * Asserts that listeners are called the expected number of times for an unhandled failure.
   */
  public void testListenersForUnhandledFailure() throws Throwable {
    Callable<Boolean> callable = () -> service.connect();

    // Given - Fail 2 times then don't match policy
    when(service.connect()).thenThrow(failures(2, new IllegalStateException()))
        .thenThrow(IllegalArgumentException.class);
    RetryPolicy retryPolicy = new RetryPolicy().retryOn(IllegalStateException.class).withMaxRetries(10);

    // When
    Asserts.assertThrows(() -> registerListeners(Failsafe.with(retryPolicy)).get(callable),
        IllegalArgumentException.class);

    // Then
    abort.assertEquals(0);
    complete.assertEquals(1);
    failedAttempt.assertEquals(3);
    failure.assertEquals(1);
    retriesExceeded.assertEquals(0);
    retry.assertEquals(2);
    success.assertEquals(0);
  }

  /**
   * Asserts that listeners are called the expected number of times when retries are exceeded.
   */
  public void testListenersForRetriesExceeded() throws Throwable {
    Callable<Boolean> callable = () -> service.connect();

    // Given - Fail 4 times and exceed retries
    when(service.connect()).thenThrow(failures(10, new IllegalStateException()));
    RetryPolicy retryPolicy = new RetryPolicy().abortOn(IllegalArgumentException.class).withMaxRetries(3);

    // When
    Asserts.assertThrows(() -> registerListeners(Failsafe.with(retryPolicy)).get(callable),
        IllegalStateException.class);

    // Then
    abort.assertEquals(0);
    complete.assertEquals(1);
    failedAttempt.assertEquals(4);
    failure.assertEquals(1);
    retriesExceeded.assertEquals(1);
    retry.assertEquals(3);
    success.assertEquals(0);
  }

  /**
   * Asserts that listeners are called the expected number of times for an aborted execution.
   */
  public void testListenersForAbort() throws Throwable {
    Callable<Boolean> callable = () -> service.connect();

    // Given - Fail twice then abort
    when(service.connect()).thenThrow(failures(3, new IllegalStateException()))
        .thenThrow(new IllegalArgumentException());
    RetryPolicy retryPolicy = new RetryPolicy().abortOn(IllegalArgumentException.class).withMaxRetries(3);

    // When
    Asserts.assertThrows(() -> registerListeners(Failsafe.with(retryPolicy)).get(callable),
        IllegalArgumentException.class);

    // Then
    abort.assertEquals(1);
    complete.assertEquals(1);
    failedAttempt.assertEquals(4);
    failure.assertEquals(1);
    retriesExceeded.assertEquals(0);
    retry.assertEquals(3);
    success.assertEquals(0);
  }

  /**
   * Asserts that a failure listener is not called on an abort.
   */
  public void testFailureListenerCalledOnAbort() {
    // Given
    RetryPolicy retryPolicy = new RetryPolicy().abortOn(IllegalArgumentException.class);
    AtomicBoolean called = new AtomicBoolean();

    // When
    try {
      Failsafe.with(retryPolicy).onFailure(e -> {
        called.set(true);
      }).run(() -> {
        throw new IllegalArgumentException();
      });

      fail("Expected exception");
    } catch (Exception expected) {
    }

    assertTrue(called.get());
  }
}
