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
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.lang.reflect.Method;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import static net.jodah.failsafe.Asserts.assertThrows;
import static net.jodah.failsafe.Testing.failures;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;

@Test
public abstract class AbstractFailsafeTest {
  RetryPolicy<Boolean> retryAlways = new RetryPolicy<Boolean>().withMaxRetries(-1);
  RetryPolicy<Boolean> retryNever = new RetryPolicy<Boolean>().withMaxRetries(0);
  RetryPolicy<Boolean> retryTwice = new RetryPolicy<Boolean>().withMaxRetries(2);
  Service service = mock(Service.class);
  AtomicInteger counter;

  public interface FastService extends Service {
  }

  abstract ScheduledExecutorService getExecutor();

  @BeforeMethod
  void beforeMethod(Method method) {
    System.out.println("Testing " + method);
  }

  /**
   * Does a failsafe getAsync with an optional executor.
   */
  <T> T failsafeGet(RetryPolicy<T> retryPolicy, CheckedSupplier<T> supplier) {
    ScheduledExecutorService executor = getExecutor();
    return unwrapExceptions(() -> executor == null ? Failsafe.with(retryPolicy).get(supplier)
        : Failsafe.with(retryPolicy).with(executor).getAsync(supplier).get());
  }

  /**
   * Does a failsafe getAsync with an optional executor.
   */
  <T> T failsafeGet(CircuitBreaker<T> circuitBreaker, CheckedSupplier<T> supplier) {
    ScheduledExecutorService executor = getExecutor();
    return unwrapExceptions(() -> executor == null ? Failsafe.with(circuitBreaker).get(supplier)
        : Failsafe.with(circuitBreaker).with(executor).getAsync(supplier).get());
  }

  /**
   * Does a failsafe runAsync with an optional executor.
   */
  void failsafeRun(CircuitBreaker<?> breaker, CheckedRunnable runnable) {
    ScheduledExecutorService executor = getExecutor();
    if (executor == null)
      Failsafe.with(breaker).run(runnable);
    else
      Failsafe.with(breaker).with(executor).runAsync(runnable);
  }

  /**
   * Does a failsafe get with an optional executor.
   */
  <T> T failsafeGet(CircuitBreaker<T> breaker, CheckedFunction<ExecutionAttemptedEvent<? extends T>, T> fallback, CheckedSupplier<T> supplier) {
    ScheduledExecutorService executor = getExecutor();
    return unwrapExceptions(() -> executor == null ? Failsafe.with(Fallback.of(fallback), breaker).get(supplier)
        : Failsafe.with(Fallback.ofAsync(fallback), breaker).with(executor).getAsync(supplier).get());
  }

  /**
   * Does a failsafe get with an optional executor.
   */
  <T> T failsafeGet(RetryPolicy<T> retryPolicy, CheckedFunction<ExecutionAttemptedEvent<? extends T>, T> fallback, CheckedSupplier<T> supplier) {
    ScheduledExecutorService executor = getExecutor();
    return unwrapExceptions(() -> executor == null ? Failsafe.with(Fallback.of(fallback), retryPolicy).get(supplier)
        : Failsafe.with(Fallback.ofAsync(fallback), retryPolicy).with(executor).getAsync(supplier).get());
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

  /**
   * Should throw CircuitBreakerOpenException when max half-open executions are occurring.
   */
  public void shouldRejectExcessiveExecutionsThroughHalfOpenCircuit() throws Throwable {
    // Given
    CircuitBreaker<Object> breaker = new CircuitBreaker<>().withSuccessThreshold(3);
    breaker.halfOpen();
    Waiter waiter = new Waiter();
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
    Waiter waiter = new Waiter();

    // When / Then
    assertEquals(failsafeGet(retryPolicy, e -> {
      waiter.assertNull(e.getLastResult());
      waiter.assertEquals(failure, e.getLastFailure());
      return false;
    }, () -> service.connect()), Boolean.FALSE);
    verify(service, times(3)).connect();

    // Given
    reset(service);
    when(service.connect()).thenThrow(failures(3, failure));

    // When / Then
    assertThrows(() -> failsafeGet(retryPolicy, e -> {
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
    Waiter waiter = new Waiter();

    // When / Then
    assertEquals(failsafeGet(breaker, e -> {
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
    assertThrows(() -> failsafeGet(breaker, e -> {
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
    Waiter waiter = new Waiter();

    // When / Then
    assertEquals(failsafeGet(breaker, e -> {
      waiter.assertNull(e.getLastResult());
      waiter.assertTrue(e.getLastFailure() instanceof CircuitBreakerOpenException);
      return false;
    }, service::connect), Boolean.FALSE);
    verify(service, times(0)).connect();
  }

  private <T> T unwrapExceptions(CheckedSupplier<T> supplier) {
    try {
      return supplier.get();
    } catch (ExecutionException e) {
      throw (RuntimeException) e.getCause();
    } catch (FailsafeException e) {
      RuntimeException cause = (RuntimeException) e.getCause();
      throw cause == null ? e : cause;
    } catch (Exception e) {
      throw (RuntimeException) e;
    }
  }
}
