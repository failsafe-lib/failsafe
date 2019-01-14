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

import net.jodah.failsafe.function.CheckedRunnable;
import net.jodah.failsafe.function.CheckedSupplier;
import net.jodah.failsafe.function.ContextualSupplier;
import net.jodah.failsafe.function.ContextualRunnable;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static net.jodah.failsafe.Asserts.assertThrows;
import static net.jodah.failsafe.Testing.failures;
import static net.jodah.failsafe.Testing.ignoreExceptions;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

@Test
public class SyncFailsafeTest extends AbstractFailsafeTest {
  // Results from a synchronous Failsafe call
  private @SuppressWarnings("unchecked") Class<? extends Throwable>[] syncThrowables = new Class[] {
      ConnectException.class };
  // Results from a get against a future that wraps a synchronous Failsafe call
  private @SuppressWarnings("unchecked") Class<? extends Throwable>[] futureSyncThrowables = new Class[] {
      ExecutionException.class, ConnectException.class };

  @BeforeMethod
  protected void beforeMethod() {
    reset(service);
    counter = new AtomicInteger();
  }

  @Override
  ScheduledExecutorService getExecutor() {
    return null;
  }

  private void assertRun(Object runnable) {
    // Given - Fail twice then succeed
    when(service.connect()).thenThrow(failures(2, new ConnectException())).thenReturn(true);

    // When
    run(Failsafe.with(retryAlways), runnable);

    // Then
    verify(service, times(3)).connect();

    // Given - Fail three times
    reset(service);
    counter.set(0);
    when(service.connect()).thenThrow(failures(10, new ConnectException()));

    // When / Then
    assertThrows(() -> run(Failsafe.with(retryTwice), runnable), syncThrowables);
    verify(service, times(3)).connect();
  }

  public void shouldRun() {
    assertRun((CheckedRunnable) () -> service.connect());
  }

  public void shouldRunContextual() {
    assertRun((ContextualRunnable) context -> {
      assertEquals(context.getExecutions(), counter.getAndIncrement());
      service.connect();
    });
  }

  private void assertGet(Object supplier) {
    // Given - Fail twice then succeed
    when(service.connect()).thenThrow(failures(2, new ConnectException())).thenReturn(false, false, true);
    RetryPolicy<Object> retryPolicy = new RetryPolicy<>().handleResult(false);

    assertEquals(get(Failsafe.with(retryPolicy), supplier), Boolean.TRUE);
    verify(service, times(5)).connect();

    // Given - Fail three times
    reset(service);
    counter.set(0);
    when(service.connect()).thenThrow(failures(10, new ConnectException()));

    // When / Then
    assertThrows(() -> get(Failsafe.with(retryTwice), supplier), syncThrowables);
    verify(service, times(3)).connect();
  }

  public void shouldGet() {
    assertGet((CheckedSupplier<Boolean>) () -> service.connect());
  }

  public void shouldGetContextual() {
    assertGet((ContextualSupplier<Boolean>) context -> {
      assertEquals(context.getExecutions(), counter.getAndIncrement());
      return service.connect();
    });
  }

  public void testPerStageRetries() throws Throwable {
    // Given - Fail twice then succeed
    when(service.connect()).thenThrow(failures(2, new ConnectException())).thenReturn(false, true);
    when(service.disconnect()).thenThrow(failures(2, new ConnectException())).thenReturn(false, true);
    RetryPolicy<Object> retryPolicy = new RetryPolicy<>().handleResult(false);

    // When
    CompletableFuture.supplyAsync(() -> Failsafe.with(retryPolicy).get(() -> service.connect()))
        .thenRun(() -> Failsafe.with(retryPolicy).get(() -> service.disconnect()))
        .get();

    // Then
    verify(service, times(4)).connect();
    verify(service, times(4)).disconnect();

    // Given - Fail three times
    reset(service);
    when(service.connect()).thenThrow(failures(10, new ConnectException()));

    // When / Then
    assertThrows(
        () -> CompletableFuture.supplyAsync(() -> Failsafe.with(retryTwice).get(() -> service.connect())).get(),
        futureSyncThrowables);
    verify(service, times(3)).connect();
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
    assertThrows(() -> Failsafe.with(retryPolicy).get(() -> service.connect()), IllegalStateException.class);
    verify(service, times(3)).connect();
  }

  public void shouldOpenCircuitWhenTimeoutExceeded() {
    // Given
    CircuitBreaker<Object> breaker = new CircuitBreaker<>().withTimeout(10, TimeUnit.MILLISECONDS);
    assertTrue(breaker.isClosed());

    // When
    Failsafe.with(breaker).run(() -> {
      Thread.sleep(20);
    });

    // Then
    assertTrue(breaker.isOpen());
  }

  /**
   * Asserts that Failsafe throws when interrupting a waiting thread.
   */
  public void shouldThrowWhenInterruptedDuringSynchronousDelay() {
    Thread mainThread = Thread.currentThread();
    new Thread(() -> {
      try {
        Thread.sleep(100);
        mainThread.interrupt();
      } catch (Exception e) {
      }
    }).start();

    try {
      Failsafe.with(new RetryPolicy<>().withDelay(5, TimeUnit.SECONDS)).run(() -> {
        throw new Exception();
      });
    } catch (Exception e) {
      assertTrue(e instanceof FailsafeException);
      assertTrue(e.getCause() instanceof InterruptedException);
      // Clear interrupt flag
      Thread.interrupted();
    }
  }

  public void shouldRetryAndOpenCircuit() {
    CircuitBreaker<Boolean> circuit = new CircuitBreaker<Boolean>().withFailureThreshold(3).withDelay(10, TimeUnit.MINUTES);

    // Given - Fail twice then succeed
    when(service.connect()).thenThrow(failures(20, new ConnectException())).thenReturn(true);

    // When
    assertThrows(
        () -> Failsafe.with(circuit).with(retryAlways.handle(ConnectException.class)).run(() -> service.connect()),
        CircuitBreakerOpenException.class);

    // Then
    verify(service, times(3)).connect();
  }

  public void shouldThrowCircuitBreakerOpenExceptionAfterFailuresExceeded() {
    // Given
    CircuitBreaker<Object> breaker = new CircuitBreaker<>().withFailureThreshold(2).withDelay(10, TimeUnit.SECONDS);
    AtomicInteger counter = new AtomicInteger();
    CheckedRunnable runnable = () -> Failsafe.with(breaker).run(() -> {
      counter.incrementAndGet();
      throw new Exception();
    });

    // When
    ignoreExceptions(runnable);
    ignoreExceptions(runnable);

    // Then
    assertThrows(runnable, CircuitBreakerOpenException.class);
    assertEquals(counter.get(), 2);
  }

  /**
   * Asserts that an execution is failed when the max duration is exceeded.
   */
  public void shouldCompleteWhenMaxDurationExceeded() {
    when(service.connect()).thenReturn(false);
    RetryPolicy<Object> retryPolicy = new RetryPolicy<>().handleResult(false).withMaxDuration(100, TimeUnit.MILLISECONDS);

    assertEquals(Failsafe.with(retryPolicy).onFailure(e -> {
      assertEquals(e.result, Boolean.FALSE);
      assertNull(e.failure);
    }).get(() -> {
      Testing.sleep(120);
      return service.connect();
    }), Boolean.FALSE);
    verify(service).connect();
  }

  /**
   * Tests the handling of a fallback with no conditions.
   */
  public void testCircuitBreakerWithoutConditions() {
    CircuitBreaker<Object> circuitBreaker = new CircuitBreaker<>().withDelay(1, TimeUnit.MILLISECONDS);

    Asserts.assertThrows(() -> Failsafe.with(circuitBreaker).get(() -> {
      throw new IllegalStateException();
    }), IllegalStateException.class);
    assertTrue(circuitBreaker.isOpen());

    RetryPolicy<Object> retryPolicy = new RetryPolicy<>().withMaxRetries(5);
    AtomicInteger counter = new AtomicInteger();
    assertTrue(Failsafe.with(retryPolicy).with(circuitBreaker).get(() -> {
      if (counter.incrementAndGet() < 3)
        throw new ConnectException();
      return true;
    }));
    assertTrue(circuitBreaker.isClosed());
  }

  /**
   * Tests the handling of a fallback with no conditions.
   */
  public void testFallbackWithoutConditions() {
    Fallback<Object> fallback = Fallback.of(true);

    assertTrue(Failsafe.with(fallback).get(() -> {
      throw new ConnectException();
    }));

    RetryPolicy<Object> retryPolicy = new RetryPolicy<>().withMaxRetries(2);
    assertTrue(Failsafe.with(retryPolicy).with(fallback).get(() -> {
      throw new ConnectException();
    }));
  }

  /**
   * Tests the handling of a fallback with conditions.
   */
  public void testFallbackWithConditions() {
    Fallback<Boolean> fallback = Fallback.of(true).handle(ConnectException.class);
    Asserts.assertThrows(() -> Failsafe.with(fallback).get(() -> {
      throw new IllegalStateException();
    }), IllegalStateException.class);

    assertTrue(Failsafe.with(fallback).get(() -> {
      throw new ConnectException();
    }));
  }

  public void shouldWrapCheckedExceptions() {
    assertThrows(() -> Failsafe.with(new RetryPolicy<>().withMaxRetries(1)).run(() -> {
      throw new TimeoutException();
    }), FailsafeException.class, TimeoutException.class);
  }

  private void run(FailsafeExecutor<?> failsafe, Object runnable) {
    if (runnable instanceof CheckedRunnable)
      failsafe.run((CheckedRunnable) runnable);
    else if (runnable instanceof ContextualRunnable)
      failsafe.run((ContextualRunnable) runnable);
  }

  @SuppressWarnings("unchecked")
  private <T> T get(FailsafeExecutor<T> failsafe, Object supplier) {
    if (supplier instanceof CheckedSupplier)
      return failsafe.get((CheckedSupplier<T>) supplier);
    else
      return failsafe.get((ContextualSupplier<T>) supplier);
  }
}
