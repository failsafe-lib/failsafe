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
import net.jodah.failsafe.Testing.ConnectException;
import net.jodah.failsafe.Testing.Service;
import net.jodah.failsafe.event.ExecutionAttemptedEvent;
import net.jodah.failsafe.function.CheckedFunction;
import net.jodah.failsafe.function.CheckedRunnable;
import net.jodah.failsafe.function.CheckedSupplier;
import net.jodah.failsafe.function.ContextualSupplier;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static net.jodah.failsafe.Asserts.assertThrows;
import static net.jodah.failsafe.Testing.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;

@Test
public abstract class AbstractFailsafeTest {
  RetryPolicy<Boolean> retryAlways = new RetryPolicy<Boolean>().withMaxRetries(-1);
  RetryPolicy<Boolean> retryNever = new RetryPolicy<Boolean>().withMaxRetries(0);
  RetryPolicy<Boolean> retryTwice = new RetryPolicy<Boolean>().withMaxRetries(2);
  Service service = mock(Service.class);
  AtomicInteger counter;
  Waiter waiter;

  public interface FastService extends Service {
  }

  abstract ScheduledExecutorService getExecutor();

  @BeforeMethod
  void beforeMethod(Method method) {
    System.out.println("Testing " + method);
    waiter = new Waiter();
  }

  /**
   * Does a failsafe get with an optional executor.
   */
  <T> T failsafeGet(Policy<T> policy, CheckedSupplier<T> supplier) {
    return get(Failsafe.with(policy), supplier);
  }

  /**
   * Does a failsafe get with an optional executor.
   */
  <T> T get(FailsafeExecutor<T> failsafe, CheckedSupplier<T> supplier) {
    return unwrapExceptions(
      () -> getExecutor() == null ? failsafe.get(supplier) : failsafe.with(getExecutor()).getAsync(supplier).get());
  }

  /**
   * Does a contextual failsafe get with an optional executor.
   */
  <T> T failsafeGet(Policy<T> policy, ContextualSupplier<T> supplier) {
    return get(Failsafe.with(policy), supplier);
  }

  /**
   * Does a contextual failsafe get with an optional executor.
   */
  <T> T get(FailsafeExecutor<T> failsafe, ContextualSupplier<T> supplier) {
    return unwrapExceptions(
      () -> getExecutor() == null ? failsafe.get(supplier) : failsafe.with(getExecutor()).getAsync(supplier).get());
  }

  /**
   * Does a failsafe get with an optional executor.
   */
  <T> T failsafeGetWithFallback(Policy<T> policy, CheckedFunction<ExecutionAttemptedEvent<? extends T>, T> fallback,
    CheckedSupplier<T> supplier) {
    ScheduledExecutorService executor = getExecutor();
    return unwrapExceptions(() -> executor == null ?
      Failsafe.with(Fallback.of(fallback), policy).get(supplier) :
      Failsafe.with(Fallback.ofAsync(fallback), policy).with(executor).getAsync(supplier).get());
  }

  /**
   * Does a failsafe run with an optional executor.
   */
  void failsafeRun(Policy<?> policy, CheckedRunnable runnable) {
    ScheduledExecutorService executor = getExecutor();
    if (executor == null)
      Failsafe.with(policy).run(runnable);
    else
      Failsafe.with(policy).with(executor).runAsync(runnable);
  }

  /**
   * Asserts that retries are not attempted after a successful execution.
   */
  public void shouldSucceedWithoutRetries() {
    // Given retries not allowed
    reset(service);
    when(service.connect()).thenReturn(false);

    // When / Then
    assertEquals(failsafeGet(retryNever, service::connect), Boolean.FALSE);
    verify(service).connect();
  }

  /**
   * Asserts that retries are performed then a non-retryable failure is thrown.
   */
  @SuppressWarnings("unchecked")
  public void shouldThrowOnNonRetriableFailure() {
    // Given
    when(service.connect()).thenThrow(ConnectException.class, ConnectException.class, IllegalStateException.class);
    RetryPolicy retryPolicy = new RetryPolicy<>().handle(ConnectException.class);

    // When / Then
    assertThrows(() -> failsafeGet(retryPolicy, service::connect), IllegalStateException.class);
    verify(service, times(3)).connect();
  }

  public void shouldFallbackOfException() {
    Fallback<Object> fallback = Fallback.ofException(e -> new IllegalStateException(e.getLastFailure()));

    assertThrows(() -> Failsafe.with(fallback).run(() -> {
      throw new Exception();
    }), IllegalStateException.class);
  }

  /**
   * Should throw CircuitBreakerOpenException when max half-open executions are occurring.
   */
  public void shouldRejectExcessiveExecutionsThroughHalfOpenCircuit() throws Throwable {
    // Given
    CircuitBreaker<Object> breaker = new CircuitBreaker<>().withSuccessThreshold(3);
    breaker.halfOpen();
    for (int i = 0; i < 3; i++)
      Testing.runInThread(() -> failsafeRun(breaker, () -> {
        waiter.resume();
        Thread.sleep(1000);
      }));

    // When / Then
    waiter.await(10000, 3);
    for (int i = 0; i < 5; i++)
      assertThrows(() -> failsafeGet(breaker, () -> null), CircuitBreakerOpenException.class);
  }

  /**
   * Asserts that fallback works as expected after retries.
   */
  public void shouldFallbackAfterFailureWithRetries() {
    // Given
    RetryPolicy<Object> retryPolicy = new RetryPolicy<>().withMaxRetries(2);
    Exception failure = new ConnectException();
    when(service.connect()).thenThrow(failures(3, failure));

    // When / Then
    assertEquals(failsafeGetWithFallback(retryPolicy, e -> {
      waiter.assertNull(e.getLastResult());
      waiter.assertEquals(failure, e.getLastFailure());
      return false;
    }, () -> service.connect()), Boolean.FALSE);
    verify(service, times(3)).connect();

    // Given
    reset(service);
    when(service.connect()).thenThrow(failures(3, failure));

    // When / Then
    assertThrows(() -> failsafeGetWithFallback(retryPolicy, e -> {
      waiter.assertNull(e.getLastResult());
      waiter.assertEquals(failure, e.getLastFailure());
      throw new RuntimeException(e.getLastFailure());
    }, () -> service.connect()), RuntimeException.class, ConnectException.class);
    verify(service, times(3)).connect();
  }

  /**
   * Asserts that fallback works after a failure with a breaker configured.
   */
  public void shouldFallbackAfterFailureWithCircuitBreaker() {
    // Given
    CircuitBreaker<Object> breaker = new CircuitBreaker<>().withSuccessThreshold(3);
    Exception failure = new ConnectException();
    when(service.connect()).thenThrow(failure);

    // When / Then
    assertEquals(failsafeGetWithFallback(breaker, e -> {
      waiter.assertNull(e.getLastResult());
      waiter.assertEquals(failure, e.getLastFailure());
      return false;
    }, () -> service.connect()), Boolean.FALSE);
    verify(service).connect();

    // Given
    reset(service);
    breaker.close();
    when(service.connect()).thenThrow(failure);

    // When / Then
    assertThrows(() -> failsafeGetWithFallback(breaker, e -> {
      waiter.assertNull(e.getLastResult());
      waiter.assertEquals(failure, e.getLastFailure());
      throw new RuntimeException(e.getLastFailure());
    }, () -> service.connect()), RuntimeException.class, ConnectException.class);
    verify(service).connect();
  }

  /**
   * Asserts that fallback works when a circuit breaker is open.
   */
  public void shouldFallbackWhenCircuitBreakerIsOpen() {
    // Given
    CircuitBreaker<Object> breaker = new CircuitBreaker<>().withSuccessThreshold(3);
    breaker.open();
    Exception failure = new ConnectException();
    when(service.connect()).thenThrow(failure);

    // When / Then
    assertEquals(failsafeGetWithFallback(breaker, e -> {
      waiter.assertNull(e.getLastResult());
      waiter.assertTrue(e.getLastFailure() instanceof CircuitBreakerOpenException);
      return false;
    }, service::connect), Boolean.FALSE);
    verify(service, times(0)).connect();
  }

  public void shouldNotTimeout() throws Throwable {
    // Given
    Timeout<Object> timeout = Timeout.of(Duration.ofSeconds(1));
    CheckedSupplier supplier = () -> "foo";

    // When / Then
    FailsafeExecutor<Object> failsafe = Failsafe.with(timeout).onSuccess(f -> {
      waiter.assertEquals("foo", f.getResult());
      waiter.resume();
    });
    assertEquals(get(failsafe, supplier), "foo");
    waiter.await(1, TimeUnit.SECONDS);
  }

  /**
   * Times out twice then completes successfully.
   */
  public void shouldTimeout() throws Throwable {
    // Given
    RetryPolicy<Object> rp = new RetryPolicy<>().onFailedAttempt(
      e -> waiter.assertTrue(e.getLastFailure() instanceof TimeoutExceededException)).withMaxRetries(2);
    Timeout<Object> timeout = Timeout.of(Duration.ofMillis(1)).onFailure(e -> {
      waiter.assertTrue(e.getFailure() instanceof TimeoutExceededException);
      waiter.resume();
    }).onSuccess(e -> {
      waiter.assertEquals(e.getResult(), "foo2");
      waiter.resume();
    });
    ContextualSupplier supplier = ctx -> {
      if (ctx.getAttemptCount() != 2)
        Thread.sleep(100);
      return "foo" + ctx.getAttemptCount();
    };

    // When / Then
    FailsafeExecutor<Object> failsafe = Failsafe.with(rp, timeout).onSuccess(e -> {
      waiter.assertEquals(e.getAttemptCount(), 3);
      waiter.assertEquals("foo2", e.getResult());
      waiter.assertNull(e.getFailure());
      waiter.resume();
    });
    assertEquals(get(failsafe, supplier), "foo2");
    waiter.await(1, TimeUnit.SECONDS, 4);
  }

  /**
   * Times out then is cancelled without interruption twice then completes successfully.
   */
  public void shouldTimeoutAndCancel() throws Throwable {
    // Given
    RetryPolicy<Object> rp = new RetryPolicy<>().onFailedAttempt(
      e -> waiter.assertTrue(e.getLastFailure() instanceof TimeoutExceededException)).withMaxRetries(2);
    Timeout<Object> timeout = Timeout.of(Duration.ofMillis(1)).withCancel(false);
    ContextualSupplier supplier = ctx -> {
      if (ctx.getAttemptCount() != 2) {
        Thread.sleep(100);
        waiter.assertTrue(ctx.isCancelled());
      } else
        waiter.assertFalse(ctx.isCancelled()); // Cancellation should be cleared on last attempt
      return "foo" + ctx.getAttemptCount();
    };

    // When / Then
    FailsafeExecutor<Object> failsafe = Failsafe.with(rp, timeout).onSuccess(e -> {
      waiter.assertEquals(e.getAttemptCount(), 3);
      waiter.assertEquals("foo2", e.getResult());
      waiter.assertNull(e.getFailure());
      waiter.resume();
    });
    assertEquals(get(failsafe, supplier), "foo2");
    waiter.await(1, TimeUnit.SECONDS);
  }

  /**
   * Times out then is cancelled with interruption 3 times.
   */
  public void shouldTimeoutAndCancelAndInterrupt() throws Throwable {
    // Given
    RetryPolicy<Object> rp = new RetryPolicy<>().withMaxRetries(2);
    Timeout<Object> timeout = Timeout.of(Duration.ofMillis(100)).withCancel(true).onFailure(e -> {
      waiter.assertTrue(e.getFailure() instanceof TimeoutExceededException);
      waiter.resume();
    });
    ContextualSupplier supplier = ctx -> {
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        waiter.assertTrue(ctx.isCancelled());
        waiter.resume();
        throw e;
      }
      return "foo";
    };

    // When / Then
    FailsafeExecutor<Object> failsafe = Failsafe.with(rp, timeout).onFailure(e -> {
      waiter.assertEquals(e.getAttemptCount(), 3);
      waiter.assertNull(e.getResult());
      waiter.assertTrue(e.getFailure() instanceof TimeoutExceededException);
      waiter.resume();
    });
    assertThrows(() -> get(failsafe, supplier), TimeoutExceededException.class);
    waiter.await(1, TimeUnit.SECONDS, 7);
  }

  public void shouldFallbackWhenTimeoutExceeded() {
    // Given
    Timeout<Object> timeout = Timeout.of(Duration.ofMillis(10));
    CheckedSupplier supplier = () -> {
      Thread.sleep(100);
      return "foo";
    };

    // When / Then
    assertEquals(failsafeGetWithFallback(timeout, e -> {
      waiter.assertNull(e.getLastResult());
      waiter.assertTrue(e.getLastFailure() instanceof TimeoutExceededException);
      return false;
    }, supplier), Boolean.FALSE);
  }

  public void shouldGetLastResult() {
    // Given
    RetryPolicy<Integer> retryPolicy = new RetryPolicy<Integer>().withMaxAttempts(5).handleResultIf(r -> true);

    // When / Then
    int result = failsafeGet(retryPolicy, ctx -> ctx.getLastResult(10) + 1);
    assertEquals(result, 15);
  }
}
