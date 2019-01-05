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
public class FailsafeExecutorTest {
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

    /** Waits for the expected async invocations and asserts the expected {@code expectedInvocations}. */
    void assertEquals(int expectedInvocations) throws Throwable {
      if (expectedInvocations > 0 && asyncListeners > 0)
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

  <T> FailsafeExecutor<T> registerListeners(RetryPolicy<T> retryPolicy) {
    FailsafeExecutor<T> failsafe = Failsafe.with(retryPolicy);
    retryPolicy.onAbort(e -> abort.sync(1));
    failsafe.onComplete(e -> complete.sync(1));
    retryPolicy.onFailedAttempt(e -> failedAttempt.sync(1));
    failsafe.onFailure(e -> failure.sync(1));
    retryPolicy.onRetriesExceeded(e -> retriesExceeded.sync(1));
    retryPolicy.onRetry(e -> retry.sync(1));
    failsafe.onSuccess(e -> success.sync(1));
    return failsafe;
  }

  /**
   * Asserts that listeners are called the expected number of times for a successful completion.
   */
  public void testListenersForSuccess() throws Throwable {
    Callable<Boolean> callable = () -> service.connect();

    // Given - Fail 4 times then succeed
    when(service.connect()).thenThrow(failures(2, new IllegalStateException())).thenReturn(false, false, true);
    RetryPolicy<Boolean> retryPolicy = new RetryPolicy<Boolean>().handleResult(false);

    // When
    registerListeners(retryPolicy).get(callable);

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
    RetryPolicy<Object> retryPolicy = new RetryPolicy<>().handle(IllegalStateException.class).withMaxRetries(10);

    // When
    Asserts.assertThrows(() -> registerListeners(retryPolicy).get(callable),
        IllegalArgumentException.class);

    // Then
    abort.assertEquals(0);
    complete.assertEquals(1);
    failedAttempt.assertEquals(2);
    failure.assertEquals(0);
    retriesExceeded.assertEquals(0);
    retry.assertEquals(2);
    success.assertEquals(1);
  }

  /**
   * Asserts that listeners are called the expected number of times when retries are exceeded.
   */
  public void testListenersForRetriesExceeded() throws Throwable {
    Callable<Boolean> callable = () -> service.connect();

    // Given - Fail 4 times and exceed retries
    when(service.connect()).thenThrow(failures(10, new IllegalStateException()));
    RetryPolicy<Object> retryPolicy = new RetryPolicy<>().abortOn(IllegalArgumentException.class).withMaxRetries(3);

    // When
    Asserts.assertThrows(() -> registerListeners(retryPolicy).get(callable),
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
    RetryPolicy<Object> retryPolicy = new RetryPolicy<>().abortOn(IllegalArgumentException.class).withMaxRetries(3);

    // When
    Asserts.assertThrows(() -> registerListeners(retryPolicy).get(callable),
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
    RetryPolicy<Object> retryPolicy = new RetryPolicy<>().abortOn(IllegalArgumentException.class);
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
