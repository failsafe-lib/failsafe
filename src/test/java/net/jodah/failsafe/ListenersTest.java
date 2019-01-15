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
import net.jodah.failsafe.function.CheckedSupplier;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
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
  Waiter waiter;

  // RetryPolicy listener counters
  ListenerCounter rpAbort = new ListenerCounter();
  ListenerCounter rpFailedAttempt = new ListenerCounter();
  ListenerCounter rpRetriesExceeded = new ListenerCounter();
  ListenerCounter rpRetry = new ListenerCounter();
  ListenerCounter rpSuccess = new ListenerCounter();
  ListenerCounter rpFailure = new ListenerCounter();

  // CircuitBreaker listener counters
  ListenerCounter cbSuccess = new ListenerCounter();
  ListenerCounter cbFailure = new ListenerCounter();
  ListenerCounter cbOpen = new ListenerCounter();
  ListenerCounter cbHalfOpen = new ListenerCounter();
  ListenerCounter cbClose = new ListenerCounter();

  // Executor listener counters
  ListenerCounter complete = new ListenerCounter();
  ListenerCounter success = new ListenerCounter();
  ListenerCounter failure = new ListenerCounter();

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

    void reset() {
      invocations.set(0);
    }
  }

  public interface Service {
    boolean connect();
  }

  @BeforeMethod
  void beforeMethod() {
    reset(service);
    waiter = new Waiter();

    rpAbort.reset();
    rpFailedAttempt.reset();
    rpRetriesExceeded.reset();
    rpRetry.reset();
    rpSuccess.reset();
    rpFailure.reset();

    cbSuccess.reset();
    cbFailure.reset();
    cbOpen.reset();
    cbHalfOpen.reset();
    cbClose.reset();

    complete.reset();
    success.reset();
    failure.reset();
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

    failsafe.onComplete(e -> {
      complete.sync();
      waiter.resume();
    });
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
    CircuitBreaker<Boolean> circuitBreaker = new CircuitBreaker<Boolean>().handleResult(false).withDelay(Duration.ZERO);

    // When
    FailsafeExecutor<Boolean> executor = registerListeners(retryPolicy, circuitBreaker);
    if (sync)
      executor.get(supplier);
    else
      executor.getAsync(supplier).get();

    // Then
    waiter.await(1000);
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
  private void assertListenersForUnhandledFailure(boolean sync) throws Throwable {
    // Given - Fail 2 times then don't match policy
    when(service.connect()).thenThrow(failures(2, new IllegalStateException()))
        .thenThrow(IllegalArgumentException.class);
    RetryPolicy<Object> retryPolicy = new RetryPolicy<>().handle(IllegalStateException.class).withMaxRetries(10);
    CircuitBreaker<Object> circuitBreaker = new CircuitBreaker<>().withDelay(Duration.ZERO);

    // When
    FailsafeExecutor<Object> executor = registerListeners(retryPolicy, circuitBreaker);
    if (sync)
      Asserts.assertThrows(() -> executor.get(supplier), IllegalArgumentException.class);
    else
      Asserts.assertThrows(() -> executor.getAsync(supplier).get(), ExecutionException.class,
          IllegalArgumentException.class);

    // Then
    waiter.await(1000);
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

  public void testListenersForUnhandledFailureSync() throws Throwable {
    assertListenersForUnhandledFailure(true);
  }

  public void testListenersForUnhandledFailureAsync() throws Throwable {
    assertListenersForUnhandledFailure(false);
  }

  /**
   * Asserts that listeners aree called the expected number of times when retries are exceeded.
   */
  private void assertListenersForRetriesExceeded(boolean sync) {
    // Given - Fail 4 times and exceed retries
    when(service.connect()).thenThrow(failures(10, new IllegalStateException()));
    RetryPolicy<Object> retryPolicy = new RetryPolicy<>().abortOn(IllegalArgumentException.class).withMaxRetries(3);
    CircuitBreaker<Object> circuitBreaker = new CircuitBreaker<>().withDelay(Duration.ZERO);

    // When
    FailsafeExecutor<Object> executor = registerListeners(retryPolicy, circuitBreaker);
    if (sync)
      Asserts.assertThrows(() -> executor.get(supplier), IllegalStateException.class);
    else
      Asserts.assertThrows(() -> executor.getAsync(supplier).get(), ExecutionException.class,
          IllegalStateException.class);

    // Then
    waiter.resume();
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
    CircuitBreaker<Object> circuitBreaker = new CircuitBreaker<>().withDelay(Duration.ZERO);

    // When
    FailsafeExecutor<Object> executor = registerListeners(retryPolicy, circuitBreaker);
    if (sync)
      Asserts.assertThrows(() -> executor.get(supplier), IllegalArgumentException.class);
    else
      Asserts.assertThrows(() -> executor.getAsync(supplier).get(), ExecutionException.class,
          IllegalArgumentException.class);

    // Then
    waiter.resume();
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
