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

import net.jodah.failsafe.function.CheckedSupplier;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static net.jodah.failsafe.Testing.failures;
import static org.mockito.Mockito.*;

/**
 * Tests event listener capabilities of FailsafeExecutor and Policy implementations.
 */
@Test
public class ListenersTest {
  private Service service = mock(Service.class);
  CheckedSupplier<Boolean> supplier = () -> service.connect();

  // RetryPolicy listener counters
  volatile ListenerCounter rpAbort;
  volatile ListenerCounter rpFailedAttempt;
  volatile ListenerCounter rpRetriesExceeded;
  volatile ListenerCounter rpRetry;
  volatile ListenerCounter rpSuccess;
  volatile ListenerCounter rpFailure;

  // CircuitBreaker listener counters
  volatile ListenerCounter cbSuccess;
  volatile ListenerCounter cbFailure;
  volatile ListenerCounter cbOpen;
  volatile ListenerCounter cbHalfOpen;
  volatile ListenerCounter cbClose;

  // Executor listener counters
  volatile ListenerCounter complete;
  volatile ListenerCounter success;
  volatile ListenerCounter failure;

  static class ListenerCounter {
    /** Per listener invocations */
    AtomicInteger invocations = new AtomicInteger();

    /** Records a sync invocation of the {@code listener}. */
    void sync() {
      invocations.incrementAndGet();
    }

    /** Waits for the expected async invocations and asserts the expected {@code expectedInvocations}. */
    void assertEquals(int expectedInvocations) {
      Assert.assertEquals(invocations.get(), expectedInvocations);
    }
  }

  public interface Service {
    boolean connect();
  }

  @BeforeMethod
  void beforeMethod() {
    reset(service);

    rpAbort = new ListenerCounter();
    rpFailedAttempt = new ListenerCounter();
    rpRetriesExceeded = new ListenerCounter();
    rpRetry = new ListenerCounter();
    rpSuccess = new ListenerCounter();
    rpFailure = new ListenerCounter();

    cbSuccess = new ListenerCounter();
    cbFailure = new ListenerCounter();
    cbOpen = new ListenerCounter();
    cbHalfOpen = new ListenerCounter();
    cbClose = new ListenerCounter();

    complete = new ListenerCounter();
    success = new ListenerCounter();
    failure = new ListenerCounter();
  }

  private <T> FailsafeExecutor<T> registerListeners(RetryPolicy<T> retryPolicy, CircuitBreaker<T> circuitBreaker) {
    FailsafeExecutor<T> failsafe = Failsafe.with(retryPolicy, circuitBreaker);

    retryPolicy.onAbort(e -> rpAbort.sync());
    retryPolicy.onFailedAttempt(e -> rpFailedAttempt.sync());
    retryPolicy.onRetriesExceeded(e -> rpRetriesExceeded.sync());
    retryPolicy.onRetry(e -> rpRetry.sync());
    retryPolicy.onSuccess(e -> rpSuccess.sync());
    retryPolicy.onFailure(e -> rpFailure.sync());

    circuitBreaker.onOpen(() -> cbOpen.sync());
    circuitBreaker.onHalfOpen(() -> cbHalfOpen.sync());
    circuitBreaker.onClose(() -> cbClose.sync());
    circuitBreaker.onSuccess(e -> cbSuccess.sync());
    circuitBreaker.onFailure(e -> cbFailure.sync());

    failsafe.onComplete(e -> complete.sync());
    failsafe.onSuccess(e -> success.sync());
    failsafe.onFailure(e -> failure.sync());

    return failsafe;
  }

  /**
   * Asserts that listeners are called the expected number of times for a successful completion.
   */
  private void assertListenersForSuccess(boolean sync) throws Throwable {
    // Given - Fail 4 times then succeed
    when(service.connect()).thenThrow(failures(2, new IllegalStateException())).thenReturn(false, false, true);
    RetryPolicy<Boolean> retryPolicy = new RetryPolicy<Boolean>().handleResult(false);
    CircuitBreaker<Boolean> circuitBreaker = new CircuitBreaker<Boolean>().handleResult(false)
        .withDelay(0, TimeUnit.MILLISECONDS);

    // When
    FailsafeExecutor<Boolean> executor = registerListeners(retryPolicy, circuitBreaker);
    if (sync)
      executor.get(supplier);
    else
      executor.getAsync(supplier).get();

    // Then
    rpAbort.assertEquals(0);
    rpFailedAttempt.assertEquals(4);
    rpRetriesExceeded.assertEquals(0);
    rpRetry.assertEquals(4);
    rpSuccess.assertEquals(1);
    rpFailure.assertEquals(4);

    cbOpen.assertEquals(4);
    cbHalfOpen.assertEquals(4);
    cbClose.assertEquals(1);
    cbSuccess.assertEquals(1);
    cbFailure.assertEquals(4);

    complete.assertEquals(1);
    success.assertEquals(1);
    failure.assertEquals(0);
  }

  public void testListenersForSuccessSync() throws Throwable {
    assertListenersForSuccess(true);
  }

  public void testListenersForSuccessAsync() throws Throwable {
    assertListenersForSuccess(false);
  }

  /**
   * Asserts that listeners are called the expected number of times for an unhandled failure.
   */
  private void assertListenersForUnhandledFailure(boolean sync) {
    // Given - Fail 2 times then don't match policy
    when(service.connect()).thenThrow(failures(2, new IllegalStateException()))
        .thenThrow(IllegalArgumentException.class);
    RetryPolicy<Object> retryPolicy = new RetryPolicy<>().handle(IllegalStateException.class).withMaxRetries(10);
    CircuitBreaker<Object> circuitBreaker = new CircuitBreaker<>().withDelay(0, TimeUnit.MILLISECONDS);

    // When
    FailsafeExecutor<Object> executor = registerListeners(retryPolicy, circuitBreaker);
    if (sync)
      Asserts.assertThrows(() -> executor.get(supplier), IllegalArgumentException.class);
    else
      Asserts.assertThrows(() -> executor.getAsync(supplier).get(), ExecutionException.class,
          IllegalArgumentException.class);

    // Then
    rpAbort.assertEquals(0);
    rpFailedAttempt.assertEquals(2);
    rpRetriesExceeded.assertEquals(0);
    rpRetry.assertEquals(2);
    rpSuccess.assertEquals(1);
    rpFailure.assertEquals(2);

    cbOpen.assertEquals(3);
    cbHalfOpen.assertEquals(2);
    cbClose.assertEquals(0);
    cbSuccess.assertEquals(0);
    cbFailure.assertEquals(3);

    complete.assertEquals(1);
    failure.assertEquals(1);
    success.assertEquals(0);
  }

  public void testListenersForUnhandledFailureSync() {
    assertListenersForUnhandledFailure(true);
  }

  public void testListenersForUnhandledFailureAsync() {
    assertListenersForUnhandledFailure(false);
  }

  /**
   * Asserts that listeners aree called the expected number of times when retries are exceeded.
   */
  private void assertListenersForRetriesExceeded(boolean sync) {
    // Given - Fail 4 times and exceed retries
    when(service.connect()).thenThrow(failures(10, new IllegalStateException()));
    RetryPolicy<Object> retryPolicy = new RetryPolicy<>().abortOn(IllegalArgumentException.class).withMaxRetries(3);
    CircuitBreaker<Object> circuitBreaker = new CircuitBreaker<>().withDelay(0, TimeUnit.MILLISECONDS);

    // When
    FailsafeExecutor<Object> executor = registerListeners(retryPolicy, circuitBreaker);
    if (sync)
      Asserts.assertThrows(() -> executor.get(supplier), IllegalStateException.class);
    else
      Asserts.assertThrows(() -> executor.getAsync(supplier).get(), ExecutionException.class,
          IllegalStateException.class);

    // Then
    rpAbort.assertEquals(0);
    rpFailedAttempt.assertEquals(4);
    rpRetriesExceeded.assertEquals(1);
    rpRetry.assertEquals(3);
    rpSuccess.assertEquals(0);
    rpFailure.assertEquals(4);

    cbOpen.assertEquals(4);
    cbHalfOpen.assertEquals(3);
    cbClose.assertEquals(0);
    cbSuccess.assertEquals(0);
    cbFailure.assertEquals(4);

    complete.assertEquals(1);
    success.assertEquals(0);
    failure.assertEquals(1);
  }

  public void testListenersForRetriesExceededSync() {
    assertListenersForRetriesExceeded(true);
  }

  public void testListenersForRetriesExceededAsync() {
    assertListenersForRetriesExceeded(false);
  }

  /**
   * Asserts that listeners are called the expected number of times for an aborted execution.
   */
  private void assertListenersForAbort(boolean sync) {
    // Given - Fail twice then abort
    when(service.connect()).thenThrow(failures(3, new IllegalStateException()))
        .thenThrow(new IllegalArgumentException());
    RetryPolicy<Object> retryPolicy = new RetryPolicy<>().abortOn(IllegalArgumentException.class).withMaxRetries(3);
    CircuitBreaker<Object> circuitBreaker = new CircuitBreaker<>().withDelay(0, TimeUnit.MILLISECONDS);

    // When
    FailsafeExecutor<Object> executor = registerListeners(retryPolicy, circuitBreaker);
    if (sync)
      Asserts.assertThrows(() -> executor.get(supplier), IllegalArgumentException.class);
    else
      Asserts.assertThrows(() -> executor.getAsync(supplier).get(), ExecutionException.class,
          IllegalArgumentException.class);

    // Then
    rpAbort.assertEquals(1);
    rpFailedAttempt.assertEquals(4);
    rpRetriesExceeded.assertEquals(0);
    rpRetry.assertEquals(3);
    rpSuccess.assertEquals(0);
    rpFailure.assertEquals(4);

    cbOpen.assertEquals(4);
    cbHalfOpen.assertEquals(3);
    cbClose.assertEquals(0);
    cbSuccess.assertEquals(0);
    cbFailure.assertEquals(4);

    complete.assertEquals(1);
    success.assertEquals(0);
    failure.assertEquals(1);
  }

  public void testListenersForAbortSync() {
    assertListenersForAbort(true);
  }

  public void testListenersForAbortAsync() {
    assertListenersForAbort(false);
  }
}
