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
import net.jodah.failsafe.function.*;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static net.jodah.failsafe.Asserts.assertThrows;
import static net.jodah.failsafe.Asserts.matches;
import static net.jodah.failsafe.Testing.failures;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

@Test
public class AsyncFailsafeTest extends AbstractFailsafeTest {
  private ScheduledExecutorService executor = Executors.newScheduledThreadPool(5);
  private Waiter waiter;

  // Results from a getAsync against a future that wraps an asynchronous Failsafe call
  private @SuppressWarnings("unchecked") Class<? extends Throwable>[] futureAsyncThrowables = new Class[] {
      ExecutionException.class, ConnectException.class };

  @BeforeMethod
  protected void beforeMethod() {
    reset(service);
    waiter = new Waiter();
    counter = new AtomicInteger();
  }

  @Override
  ScheduledExecutorService getExecutor() {
    return executor;
  }

  private void assertRunAsync(Object runnable) throws Throwable {
    // Given - Fail twice then succeed
    when(service.connect()).thenThrow(failures(2, new ConnectException())).thenReturn(true);
    AtomicInteger expectedExecutions = new AtomicInteger(3);

    // When / Then
    Future<?> future = runAsync(Failsafe.with(retryAlways).with(executor).onComplete(e -> {
      waiter.assertEquals(expectedExecutions.get(), e.getAttemptCount());
      waiter.assertNull(e.getResult());
      waiter.assertNull(e.getFailure());
      waiter.resume();
    }), runnable);
    assertNull(future.get());
    waiter.await(3000);
    verify(service, times(3)).connect();

    // Given - Fail three times
    reset(service);
    counter.set(0);
    when(service.connect()).thenThrow(failures(10, new ConnectException()));

    // When
    Future<?> future2 = runAsync(Failsafe.with(retryTwice).with(executor).onComplete(e -> {
      waiter.assertEquals(expectedExecutions.get(), e.getAttemptCount());
      waiter.assertNull(e.getResult());
      waiter.assertTrue(e.getFailure() instanceof ConnectException);
      waiter.resume();
    }), runnable);

    // Then
    assertThrows(future2::get, futureAsyncThrowables);
    waiter.await(3000);
    verify(service, times(3)).connect();
  }

  public void shouldRunAsync() throws Throwable {
    assertRunAsync((CheckedRunnable) service::connect);
  }

  public void shouldRunAsyncContextual() throws Throwable {
    assertRunAsync((ContextualRunnable) context -> {
      assertEquals(context.getAttemptCount(), counter.getAndIncrement());
      service.connect();
    });
  }

  public void shouldRunAsyncExecution() throws Throwable {
    assertRunAsync((AsyncRunnable) exec -> {
      try {
        service.connect();
        exec.complete();
      } catch (Exception failure) {
        // Alternate between automatic and manual retries
        if (exec.getAttemptCount() % 2 == 0)
          throw failure;
        if (!exec.retryOn(failure))
          throw failure;
      }
    });
  }

  private void assertGetAsync(Object supplier) throws Throwable {
    // Given - Fail twice then succeed
    when(service.connect()).thenThrow(failures(2, new ConnectException())).thenReturn(false, false, true);
    RetryPolicy<Boolean> retryPolicy = new RetryPolicy<Boolean>().handleResult(false).withMaxAttempts(10);
    AtomicInteger expectedExecutions = new AtomicInteger(5);

    // When / Then
    Future<Boolean> future = getAsync(Failsafe.with(retryPolicy).with(executor).onComplete(e -> {
      waiter.assertEquals(expectedExecutions.get(), e.getAttemptCount());
      waiter.assertTrue(e.getResult());
      waiter.assertNull(e.getFailure());
      waiter.resume();
    }), supplier);

    assertTrue(future.get());
    waiter.await(3000);
    verify(service, times(expectedExecutions.get())).connect();

    // Given - Fail three times
    reset(service);
    counter.set(0);
    when(service.connect()).thenThrow(failures(10, new ConnectException()));
    expectedExecutions.set(3);

    // When / Then
    Future<Boolean> future2 = getAsync(Failsafe.with(retryTwice).with(executor).onComplete(e -> {
      waiter.assertEquals(expectedExecutions.get(), e.getAttemptCount());
      waiter.assertNull(e.getResult());
      waiter.assertTrue(e.getFailure() instanceof ConnectException);
      waiter.resume();
    }), supplier);
    assertThrows(future2::get, futureAsyncThrowables);
    waiter.await(3000);
    verify(service, times(expectedExecutions.get())).connect();
  }

  public void shouldGetAsync() throws Throwable {
    assertGetAsync((CheckedSupplier<?>) service::connect);
  }

  public void shouldGetAsyncContextual() throws Throwable {
    assertGetAsync((ContextualSupplier<Boolean>) context -> {
      assertEquals(context.getAttemptCount(), counter.getAndIncrement());
      return service.connect();
    });
  }

  public void shouldGetAsyncExecution() throws Throwable {
    assertGetAsync((AsyncSupplier<?>) exec -> {
      try {
        boolean result = service.connect();
        if (!exec.complete(result))
          exec.retry();
        return result;
      } catch (Exception failure) {
        // Alternate between automatic and manual retries
        if (exec.getAttemptCount() % 2 == 0)
          throw failure;
        if (!exec.retryOn(failure))
          throw failure;
        return null;
      }
    });
  }

  private void assertGetStage(Object supplier) throws Throwable {
    // Given - Fail twice then succeed
    when(service.connect()).thenThrow(failures(2, new ConnectException())).thenReturn(false, false, true);
    RetryPolicy<Boolean> retryPolicy = new RetryPolicy<Boolean>().handleResult(false).withMaxAttempts(10);

    // When
    CompletableFuture<Boolean> future = getStageAsync(Failsafe.with(retryPolicy).with(executor), supplier);

    // Then
    future.whenComplete((result, failure) -> {
      waiter.assertTrue(result);
      waiter.assertNull(failure);
      waiter.resume();
    });
    assertTrue(future.get());
    waiter.await(3000);
    verify(service, times(5)).connect();

    // Given - Fail three times
    reset(service);
    when(service.connect()).thenThrow(failures(10, new ConnectException()));

    // When
    CompletableFuture<Boolean> future2 = getStageAsync(Failsafe.with(retryTwice).with(executor), supplier);

    // Then
    future2.whenComplete((result, failure) -> {
      waiter.assertNull(result);
      waiter.assertTrue(matches(failure, ConnectException.class));
      waiter.resume();
    });
    assertThrows(future2::get, futureAsyncThrowables);
    waiter.await(3000);
    verify(service, times(3)).connect();
  }

  public void shouldGetStageAsync() throws Throwable {
    assertGetStage((CheckedSupplier<?>) () -> CompletableFuture.supplyAsync(service::connect));
  }

  public void shouldGetStageAsyncContextual() throws Throwable {
    assertGetStage((ContextualSupplier<?>) context -> CompletableFuture.supplyAsync(service::connect));
  }

  public void shouldGetStageAsyncExecution() throws Throwable {
    assertGetStage((AsyncSupplier<?>) exec -> CompletableFuture.supplyAsync(() -> {
      try {
        boolean result = service.connect();
        if (!exec.complete(result))
          exec.retryFor(result);
        return result;
      } catch (Exception failure) {
        // Alternate between automatic and manual retries
        if (exec.getAttemptCount() % 2 == 0)
          throw failure;
        if (!exec.retryOn(failure))
          throw failure;
        return null;
      }
    }));
  }

  private void assertCancel(Function<FailsafeExecutor<?>, Future<?>> executorCallable) throws Throwable {
    // Given
    Waiter waiter = new Waiter();
    Future<?> future = executorCallable.apply(Failsafe.with(retryAlways).with(executor).onComplete(e -> {
      waiter.assertNull(e.getResult());
      waiter.assertTrue(e.getFailure() instanceof CancellationException);
      waiter.resume();
    }));

    Testing.sleep(300);

    // When
    assertTrue(future.cancel(true));
    waiter.await(1000);

    // Then
    assertTrue(future.isCancelled());
    assertTrue(future.isDone());
    Asserts.assertThrows(future::get, CancellationException.class);
  }

  public void shouldCancelOnGetAsync() throws Throwable {
    assertCancel(executor -> getAsync(executor, (CheckedSupplier<?>) () -> {
      Thread.sleep(1000);
      return "test";
    }));
  }

  public void shouldCancelOnGetAsyncExecution() throws Throwable {
    assertCancel(executor -> getAsync(executor, (AsyncSupplier<?>) (e) -> {
      Thread.sleep(1000);
      e.complete();
      return null;
    }));
  }

  public void shouldCancelOnRunAsync() throws Throwable {
    assertCancel(executor -> runAsync(executor, (CheckedRunnable) () -> {
      Thread.sleep(1000);
    }));
  }

  public void shouldCancelOnRunAsyncExecution() throws Throwable {
    assertCancel(executor -> runAsync(executor, (AsyncRunnable) (e) -> {
      Thread.sleep(1000);
      e.complete();
    }));
  }

  public void shouldCancelOnGetStageAsync() throws Throwable {
    assertCancel(executor -> getStageAsync(executor, (CheckedSupplier<?>) () -> {
      Thread.sleep(1000);
      return CompletableFuture.completedFuture("test");
    }));
  }

  public void shouldCancelOnGetStageAsyncExecution() throws Throwable {
    assertCancel(executor -> getStageAsync(executor, (AsyncSupplier<?>) (e) -> {
      Thread.sleep(1000);
      CompletableFuture<?> result = CompletableFuture.completedFuture("test");
      e.complete(result);
      return result;
    }));
  }

  public void shouldManuallyRetryAndComplete() throws Throwable {
    Failsafe.with(retryAlways).with(executor).onComplete(e -> {
      waiter.assertTrue(e.getResult());
      waiter.assertNull(e.getFailure());
      waiter.resume();
    }).getAsyncExecution(exec -> {
      if (exec.getAttemptCount() < 2)
        exec.retryOn(new ConnectException());
      else
        exec.complete(true);
      return true;
    });
    waiter.await(3000);
  }

  /**
   * Assert handles a supplier that throws instead of returning a future.
   */
  public void shouldHandleThrowingGetStageAsync() {
    assertThrows(() -> Failsafe.with(retryTwice).getStageAsync(() -> {
      throw new IllegalArgumentException();
    }).get(), ExecutionException.class, IllegalArgumentException.class);

    assertThrows(() -> Failsafe.with(retryTwice).getStageAsync(context -> {
      throw new IllegalArgumentException();
    }).get(), ExecutionException.class, IllegalArgumentException.class);

    assertThrows(() -> Failsafe.with(retryTwice).getStageAsyncExecution(exec -> {
      throw new IllegalArgumentException();
    }).get(), ExecutionException.class, IllegalArgumentException.class);
  }

  /**
   * Asserts that asynchronous completion via an execution is supported.
   */
  public void shouldCompleteAsync() throws Throwable {
    Waiter waiter = new Waiter();
    Failsafe.with(retryAlways).runAsyncExecution(exec -> executor.schedule(() -> {
      try {
        exec.complete();
        waiter.resume();
      } catch (Exception e) {
        waiter.fail(e);
      }
    }, 100, TimeUnit.MILLISECONDS));

    waiter.await(5000);
  }

  public void shouldOpenCircuitWhenTimeoutExceeded() throws Throwable {
    // Given
    CircuitBreaker<Object> breaker = new CircuitBreaker<>().withTimeout(Duration.ofMillis(10));
    assertTrue(breaker.isClosed());

    // When
    Failsafe.with(breaker).with(executor).runAsyncExecution(exec -> {
      Thread.sleep(20);
      exec.complete();
      waiter.resume();
    });

    // Then
    waiter.await(1000);
    assertTrue(breaker.isOpen());
  }

  public void shouldSkipExecutionWhenCircuitOpen() {
    // Given
    CircuitBreaker<Object> breaker = new CircuitBreaker<>().withDelay(Duration.ofMillis(10));
    breaker.open();
    AtomicBoolean executed = new AtomicBoolean();

    // When
    Future future = Failsafe.with(breaker).with(executor).runAsync(() -> executed.set(true));

    // Then
    assertFalse(executed.get());
    assertThrows(future::get, ExecutionException.class, CircuitBreakerOpenException.class);
  }

  /**
   * Asserts that Failsafe handles an initial scheduling failure.
   */
  public void shouldHandleInitialSchedulingFailure() {
    // Given
    ScheduledExecutorService executor = Executors.newScheduledThreadPool(0);
    executor.shutdownNow();

    Waiter waiter = new Waiter();

    // When
    Future future = Failsafe.with(Fallback.of(false), new RetryPolicy<>(), new CircuitBreaker<>())
        .with(executor)
        .runAsync(() -> waiter.fail("Should not execute supplier since executor has been shutdown"));

    assertThrows(future::get, ExecutionException.class, RejectedExecutionException.class);
  }

  /**
   * Asserts that Failsafe handles a rpRetry scheduling failure.
   */
  public void shouldHandleRejectedRetryExecution() throws Throwable {
    // Given
    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    AtomicInteger counter = new AtomicInteger();

    // When
    Future future = Failsafe.with(new RetryPolicy<>().handleResult(null).handle(Exception.class))
        .with(executor)
        .getAsync(() -> {
          counter.incrementAndGet();
          Thread.sleep(200);
          return null;
        });

    Thread.sleep(150);
    executor.shutdownNow();
    assertThrows(future::get, ExecutionException.class, RejectedExecutionException.class);
    assertEquals(counter.get(), 1, "Supplier should have been executed before executor was shutdown");
  }

  public void shouldInterruptExecutionOnCancelWithScheduledExecutorService() throws Throwable {
    Waiter waiter = new Waiter();
    CompletableFuture<Void> future = Failsafe.with(retryAlways).with(executor).runAsync(()->{
      try {
        Thread.sleep(1000);
        waiter.fail("Expected to be cancelled");
      } catch (InterruptedException e) {
        waiter.resume();
      }
    });

    Thread.sleep(100);
    assertTrue(future.cancel(true));
    waiter.await(1000);
  }

  public void shouldInterruptExecutionOnCancelWithExecutorService() throws Throwable {
    Waiter waiter = new Waiter();
    ExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    CompletableFuture<Void> future = Failsafe.with(retryAlways).with(executorService).runAsync(()->{
      try {
        Thread.sleep(1000);
        waiter.fail("Expected to be cancelled");
      } catch (InterruptedException e) {
        waiter.resume();
      }
    });

    Thread.sleep(100);
    assertTrue(future.cancel(true));
    waiter.await(1000);
  }

  @SuppressWarnings("unused")
  public void shouldSupportCovariance() {
    FastService fastService = mock(FastService.class);
    CompletionStage<Service> stage = Failsafe.with(new RetryPolicy<Service>())
        .with(executor)
        .getAsync(() -> fastService);
  }

  private Future<?> runAsync(FailsafeExecutor<?> failsafe, Object runnable) {
    if (runnable instanceof CheckedRunnable)
      return failsafe.runAsync((CheckedRunnable) runnable);
    else if (runnable instanceof ContextualRunnable)
      return failsafe.runAsync((ContextualRunnable) runnable);
    else
      return failsafe.runAsyncExecution((AsyncRunnable) runnable);
  }

  @SuppressWarnings("unchecked")
  private <T> Future<T> getAsync(FailsafeExecutor<T> failsafe, Object supplier) {
    if (supplier instanceof CheckedSupplier)
      return failsafe.getAsync((CheckedSupplier<T>) supplier);
    else if (supplier instanceof ContextualSupplier)
      return failsafe.getAsync((ContextualSupplier<T>) supplier);
    else
      return failsafe.getAsyncExecution((AsyncSupplier<T>) supplier);
  }

  @SuppressWarnings("unchecked")
  private <T> CompletableFuture<T> getStageAsync(FailsafeExecutor<T> failsafe, Object supplier) {
    if (supplier instanceof CheckedSupplier)
      return failsafe.getStageAsync((CheckedSupplier<CompletableFuture<T>>) supplier);
    else if (supplier instanceof ContextualSupplier)
      return failsafe.getStageAsync((ContextualSupplier<CompletableFuture<T>>) supplier);
    else
      return failsafe.getStageAsyncExecution((AsyncSupplier<CompletableFuture<T>>) supplier);
  }
}
