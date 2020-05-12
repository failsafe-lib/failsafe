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
import net.jodah.failsafe.Testing.Service;
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
  ListenerCounter rpHandle = new ListenerCounter();
  ListenerCounter rpAbort = new ListenerCounter();
  ListenerCounter rpFailedAttempt = new ListenerCounter();
  ListenerCounter rpRetriesExceeded = new ListenerCounter();
  ListenerCounter rpRetry = new ListenerCounter();
  ListenerCounter rpSuccess = new ListenerCounter();
  ListenerCounter rpFailure = new ListenerCounter();

  // CircuitBreaker listener counters
  ListenerCounter cbOpen = new ListenerCounter();
  ListenerCounter cbHalfOpen = new ListenerCounter();
  ListenerCounter cbClose = new ListenerCounter();
  ListenerCounter cbSuccess = new ListenerCounter();
  ListenerCounter cbFailure = new ListenerCounter();

  // Fallback listener counters
  ListenerCounter fbFailedAttempt = new ListenerCounter();
  ListenerCounter fbSuccess = new ListenerCounter();
  ListenerCounter fbFailure = new ListenerCounter();

  // Executor listener counters
  ListenerCounter complete = new ListenerCounter();
  ListenerCounter success = new ListenerCounter();
  ListenerCounter failure = new ListenerCounter();

  static class ListenerCounter {
    /** Per listener invocations */
    AtomicInteger invocations = new AtomicInteger();

    /** Records an invocation of the {@code listener}. */
    void record() {
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

    cbOpen.reset();
    cbHalfOpen.reset();
    cbClose.reset();
    cbSuccess.reset();
    cbFailure.reset();

    fbFailedAttempt.reset();
    fbSuccess.reset();
    fbFailure.reset();

    complete.reset();
    success.reset();
    failure.reset();
  }

  private <T> FailsafeExecutor<T> registerListeners(RetryPolicy<T> retryPolicy, CircuitBreaker<T> circuitBreaker,
      Fallback<T> fallback) {
    FailsafeExecutor<T> failsafe = fallback == null ?
        Failsafe.with(retryPolicy, circuitBreaker) :
        Failsafe.with(fallback, retryPolicy, circuitBreaker);

    retryPolicy.onAbort(e -> rpAbort.record());
    retryPolicy.onFailedAttempt(e -> rpFailedAttempt.record());
    retryPolicy.onRetriesExceeded(e -> rpRetriesExceeded.record());
    retryPolicy.onRetry(e -> rpRetry.record());
    retryPolicy.onSuccess(e -> rpSuccess.record());
    retryPolicy.onFailure(e -> rpFailure.record());

    circuitBreaker.onOpen(() -> cbOpen.record());
    circuitBreaker.onHalfOpen(() -> cbHalfOpen.record());
    circuitBreaker.onClose(() -> cbClose.record());
    circuitBreaker.onSuccess(e -> cbSuccess.record());
    circuitBreaker.onFailure(e -> cbFailure.record());

    if (fallback != null) {
      fallback.onFailedAttempt(e -> fbFailedAttempt.record());
      fallback.onSuccess(e -> fbSuccess.record());
      fallback.onFailure(e -> fbFailure.record());
    }

    failsafe.onComplete(e -> {
      complete.record();
      waiter.resume();
    });
    failsafe.onSuccess(e -> success.record());
    failsafe.onFailure(e -> failure.record());

    return failsafe;
  }

  /**
   * Asserts that listeners are called the expected number of times for a successful completion.
   */
  private void assertForSuccess(boolean sync) throws Throwable {
    // Given - Fail 4 times then succeed
    when(service.connect()).thenThrow(failures(2, new IllegalStateException())).thenReturn(false, false, true);
    RetryPolicy<Boolean> retryPolicy = new RetryPolicy<Boolean>().withMaxAttempts(10).handleResult(false);
    CircuitBreaker<Boolean> circuitBreaker = new CircuitBreaker<Boolean>().handleResult(false).withDelay(Duration.ZERO);
    Fallback<Boolean> fallback = Fallback.of(true);
    FailsafeExecutor<Boolean> failsafe = registerListeners(retryPolicy, circuitBreaker, fallback);

    // When
    if (sync)
      failsafe.get(supplier);
    else
      failsafe.getAsync(supplier).get();

    // Then
    waiter.await(1000);
    rpAbort.assertEquals(0);
    rpFailedAttempt.assertEquals(4);
    rpRetriesExceeded.assertEquals(0);
    rpRetry.assertEquals(4);
    rpSuccess.assertEquals(1);
    rpFailure.assertEquals(0);

    cbOpen.assertEquals(4);
    cbHalfOpen.assertEquals(4);
    cbClose.assertEquals(1);
    cbSuccess.assertEquals(1);
    cbFailure.assertEquals(4);

    fbFailedAttempt.assertEquals(0);
    fbSuccess.assertEquals(1);
    fbFailure.assertEquals(0);

    complete.assertEquals(1);
    success.assertEquals(1);
    failure.assertEquals(0);
  }

  public void testForSuccessSync() throws Throwable {
    assertForSuccess(true);
  }

  public void testForSuccessAsync() throws Throwable {
    assertForSuccess(false);
  }

  /**
   * Asserts that listeners are called the expected number of times for an unhandled failure.
   */
  private void assertForUnhandledFailure(boolean sync) throws Throwable {
    // Given - Fail 2 times then don't match policy
    when(service.connect()).thenThrow(failures(2, new IllegalStateException()))
        .thenThrow(IllegalArgumentException.class);
    RetryPolicy<Object> retryPolicy = new RetryPolicy<>().handle(IllegalStateException.class);
    CircuitBreaker<Object> circuitBreaker = new CircuitBreaker<>().withDelay(Duration.ZERO);
    FailsafeExecutor<Object> failsafe = registerListeners(retryPolicy, circuitBreaker, null);

    // When
    if (sync)
      Asserts.assertThrows(() -> failsafe.get(supplier), IllegalArgumentException.class);
    else
      Asserts.assertThrows(() -> failsafe.getAsync(supplier).get(), ExecutionException.class,
          IllegalArgumentException.class);

    // Then
    waiter.await(1000);
    rpAbort.assertEquals(0);
    rpFailedAttempt.assertEquals(2);
    rpRetriesExceeded.assertEquals(0);
    rpRetry.assertEquals(2);
    rpSuccess.assertEquals(1);
    rpFailure.assertEquals(0);

    cbOpen.assertEquals(3);
    cbHalfOpen.assertEquals(2);
    cbClose.assertEquals(0);
    cbSuccess.assertEquals(0);
    cbFailure.assertEquals(3);

    complete.assertEquals(1);
    failure.assertEquals(1);
    success.assertEquals(0);
  }

  public void testForUnhandledFailureSync() throws Throwable {
    assertForUnhandledFailure(true);
  }

  public void testForUnhandledFailureAsync() throws Throwable {
    assertForUnhandledFailure(false);
  }

  /**
   * Asserts that listeners aree called the expected number of times when retries are exceeded.
   */
  private void assertForRetriesExceeded(boolean sync) throws Throwable {
    // Given - Fail 4 times and exceed retries
    when(service.connect()).thenThrow(failures(10, new IllegalStateException()));
    RetryPolicy<Object> retryPolicy = new RetryPolicy<>().abortOn(IllegalArgumentException.class).withMaxRetries(3);
    CircuitBreaker<Object> circuitBreaker = new CircuitBreaker<>().withDelay(Duration.ZERO);
    FailsafeExecutor<Object> failsafe = registerListeners(retryPolicy, circuitBreaker, null);

    // When
    if (sync)
      Asserts.assertThrows(() -> failsafe.get(supplier), IllegalStateException.class);
    else
      Asserts.assertThrows(() -> failsafe.getAsync(supplier).get(), ExecutionException.class,
          IllegalStateException.class);

    // Then
    waiter.await(1000);
    rpAbort.assertEquals(0);
    rpFailedAttempt.assertEquals(4);
    rpRetriesExceeded.assertEquals(1);
    rpRetry.assertEquals(3);
    rpSuccess.assertEquals(0);
    rpFailure.assertEquals(1);

    cbOpen.assertEquals(4);
    cbHalfOpen.assertEquals(3);
    cbClose.assertEquals(0);
    cbSuccess.assertEquals(0);
    cbFailure.assertEquals(4);

    complete.assertEquals(1);
    success.assertEquals(0);
    failure.assertEquals(1);
  }

  public void testForRetriesExceededSync() throws Throwable {
    assertForRetriesExceeded(true);
  }

  public void testForRetriesExceededAsync() throws Throwable {
    assertForRetriesExceeded(false);
  }

  /**
   * Asserts that listeners are called the expected number of times for an aborted execution.
   */
  private void assertForAbort(boolean sync) throws Throwable {
    // Given - Fail twice then abort
    when(service.connect()).thenThrow(failures(3, new IllegalStateException()))
        .thenThrow(new IllegalArgumentException());
    RetryPolicy<Object> retryPolicy = new RetryPolicy<>().abortOn(IllegalArgumentException.class).withMaxRetries(3);
    CircuitBreaker<Object> circuitBreaker = new CircuitBreaker<>().withDelay(Duration.ZERO);
    FailsafeExecutor<Object> failsafe = registerListeners(retryPolicy, circuitBreaker, null);

    // When
    if (sync)
      Asserts.assertThrows(() -> failsafe.get(supplier), IllegalArgumentException.class);
    else
      Asserts.assertThrows(() -> failsafe.getAsync(supplier).get(), ExecutionException.class,
          IllegalArgumentException.class);

    // Then
    waiter.await(1000);
    rpAbort.assertEquals(1);
    rpFailedAttempt.assertEquals(4);
    rpRetriesExceeded.assertEquals(0);
    rpRetry.assertEquals(3);
    rpSuccess.assertEquals(0);
    rpFailure.assertEquals(1);

    cbOpen.assertEquals(4);
    cbHalfOpen.assertEquals(3);
    cbClose.assertEquals(0);
    cbSuccess.assertEquals(0);
    cbFailure.assertEquals(4);

    complete.assertEquals(1);
    success.assertEquals(0);
    failure.assertEquals(1);
  }

  public void testForAbortSync() throws Throwable {
    assertForAbort(true);
  }

  public void testForAbortAsync() throws Throwable {
    assertForAbort(false);
  }

  private void assertForFailingRetryPolicy(boolean sync) throws Throwable {
    when(service.connect()).thenThrow(failures(10, new IllegalStateException()));

    // Given failing RetryPolicy
    RetryPolicy<Object> retryPolicy = new RetryPolicy<>().withMaxRetries(2);
    // And successful CircuitBreaker and Fallback
    CircuitBreaker<Object> circuitBreaker = new CircuitBreaker<>().handle(NullPointerException.class)
        .withDelay(Duration.ZERO);
    Fallback<Object> fallback = Fallback.<Object>of(() -> true).handle(NullPointerException.class);
    FailsafeExecutor<Object> failsafe = registerListeners(retryPolicy, circuitBreaker, fallback);

    // When
    if (sync)
      Testing.ignoreExceptions(() -> failsafe.get(supplier));
    else
      Testing.ignoreExceptions(() -> failsafe.getAsync(supplier));

    // Then
    waiter.await(1000);
    rpSuccess.assertEquals(0);
    rpFailure.assertEquals(1);

    cbSuccess.assertEquals(3);
    cbFailure.assertEquals(0);

    fbFailedAttempt.assertEquals(0);
    fbSuccess.assertEquals(1);
    fbFailure.assertEquals(0);

    complete.assertEquals(1);
    success.assertEquals(0);
    failure.assertEquals(1);
  }

  public void testFailingRetryPolicySync() throws Throwable {
    assertForFailingRetryPolicy(true);
  }

  public void testFailingRetryPolicyAsync() throws Throwable {
    assertForFailingRetryPolicy(false);
  }

  private void assertForFailingCircuitBreaker(boolean sync) throws Throwable {
    when(service.connect()).thenThrow(failures(10, new IllegalStateException()));

    // Given successful RetryPolicy
    RetryPolicy<Object> retryPolicy = new RetryPolicy<>().handle(NullPointerException.class);
    // And failing CircuitBreaker
    CircuitBreaker<Object> circuitBreaker = new CircuitBreaker<>().withDelay(Duration.ZERO);
    // And successful Fallback
    Fallback<Object> fallback = Fallback.<Object>ofAsync(() -> true).handle(NullPointerException.class);
    FailsafeExecutor<Object> failsafe = registerListeners(retryPolicy, circuitBreaker, fallback);

    // When
    if (sync)
      Testing.ignoreExceptions(() -> failsafe.get(supplier));
    else
      Testing.ignoreExceptions(() -> failsafe.getAsync(supplier));

    // Then
    waiter.await(1000);
    rpSuccess.assertEquals(1);
    rpFailure.assertEquals(0);

    cbSuccess.assertEquals(0);
    cbFailure.assertEquals(1);

    fbFailedAttempt.assertEquals(0);
    fbSuccess.assertEquals(1);
    fbFailure.assertEquals(0);

    complete.assertEquals(1);
    success.assertEquals(0);
    failure.assertEquals(1);
  }

  public void testFailingCircuitBreakerSync() throws Throwable {
    assertForFailingCircuitBreaker(true);
  }

  public void testFailingCircuitBreakerAsync() throws Throwable {
    assertForFailingCircuitBreaker(false);
  }

  private void assertForFailingFallback(boolean sync) throws Throwable {
    when(service.connect()).thenThrow(failures(10, new IllegalStateException()));

    // Given successful RetryPolicy and CircuitBreaker
    RetryPolicy<Object> retryPolicy = new RetryPolicy<>().handle(NullPointerException.class);
    CircuitBreaker<Object> circuitBreaker = new CircuitBreaker<>().withDelay(Duration.ZERO)
        .handle(NullPointerException.class);
    // And failing Fallback
    Fallback<Object> fallback = Fallback.ofAsync(() -> { throw new Exception(); });
    FailsafeExecutor<Object> failsafe = registerListeners(retryPolicy, circuitBreaker, fallback);

    // When
    if (sync)
      Testing.ignoreExceptions(() -> failsafe.get(supplier));
    else
      Testing.ignoreExceptions(() -> failsafe.getAsync(supplier));

    // Then
    waiter.await(1000);
    rpSuccess.assertEquals(1);
    rpFailure.assertEquals(0);

    cbSuccess.assertEquals(1);
    cbFailure.assertEquals(0);

    fbFailedAttempt.assertEquals(1);
    fbSuccess.assertEquals(0);
    fbFailure.assertEquals(1);

    complete.assertEquals(1);
    success.assertEquals(0);
    failure.assertEquals(1);
  }

  public void testFailingFallbackSync() throws Throwable {
    assertForFailingFallback(true);
  }

  public void testFailingFallbackAsync() throws Throwable {
    assertForFailingFallback(false);
  }
}
