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

import net.jodah.failsafe.Testing.ConnectException;
import net.jodah.failsafe.Testing.Service;
import net.jodah.failsafe.function.*;
import net.jodah.failsafe.util.concurrent.Scheduler;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static net.jodah.failsafe.Asserts.assertThrows;
import static net.jodah.failsafe.Asserts.matches;
import static net.jodah.failsafe.Testing.failures;
import static net.jodah.failsafe.Testing.unwrapExceptions;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

@Test
public class AsyncFailsafeTest extends AbstractFailsafeTest {
  private ExecutorService executor = Executors.newFixedThreadPool(5);

  // Results from a getAsync against a future that wraps an asynchronous Failsafe call
  private @SuppressWarnings("unchecked") Class<? extends Throwable>[] futureAsyncThrowables = new Class[] {
    ExecutionException.class, ConnectException.class };

  @BeforeMethod
  protected void beforeMethod() {
    reset(service);
    counter = new AtomicInteger();
  }

  @AfterClass
  protected void afterClass() {
    executor.shutdownNow();
  }

  @Override
  ExecutorService getExecutor() {
    return executor;
  }

  private void assertRunAsync(Object runnable) throws Throwable {
    // Given - Fail twice then succeed
    when(service.connect()).thenThrow(failures(2, new ConnectException())).thenReturn(true);
    AtomicInteger expectedExecutions = new AtomicInteger(3);

    // When / Then
    Future<?> future = runAsync(Failsafe.with(retryAlways).with(executor).onComplete(e -> {
      waiter.assertEquals(expectedExecutions.get(), e.getAttemptCount());
      waiter.assertEquals(expectedExecutions.get(), e.getExecutionCount());
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
      waiter.assertEquals(expectedExecutions.get(), e.getExecutionCount());
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
      assertEquals(context.getAttemptCount(), counter.get());
      assertEquals(context.getExecutionCount(), counter.get());
      counter.incrementAndGet();
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
      waiter.assertEquals(expectedExecutions.get(), e.getExecutionCount());
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
      waiter.assertEquals(expectedExecutions.get(), e.getExecutionCount());
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
    assertGetAsync((ContextualSupplier<Boolean, Boolean>) context -> {
      waiter.assertEquals(context.getAttemptCount(), counter.get());
      waiter.assertEquals(context.getExecutionCount(), counter.get());
      counter.incrementAndGet();
      return service.connect();
    });
  }

  public void shouldGetAsyncExecution() throws Throwable {
    assertGetAsync((AsyncRunnable<?>) exec -> {
      try {
        boolean result = service.connect();
        if (!exec.complete(result))
          exec.retry();
      } catch (Exception failure) {
        // Alternate between automatic and manual retries
        if (exec.getAttemptCount() % 2 == 0)
          throw failure;
        if (!exec.retryOn(failure))
          throw failure;
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
    assertGetStage((ContextualSupplier<?, ?>) context -> CompletableFuture.supplyAsync(service::connect));
  }

  public void shouldGetStageAsyncExecution() throws Throwable {
    assertGetStage((AsyncSupplier<?, ?>) exec -> CompletableFuture.supplyAsync(() -> {
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

  /**
   * Asserts that an externally completed FailsafeFuture causes completion listeners to be called.
   */
  public void shouldCompleteFutureExternally() throws Throwable {
    // Given
    CompletableFuture<Boolean> future1 = Failsafe.with(retryNever).onSuccess(e -> {
      waiter.assertFalse(e.getResult());
      waiter.resume();
    }).getAsync(() -> {
      waiter.resume();
      Thread.sleep(500);
      return true;
    });
    waiter.await(1000);

    // When completed
    future1.complete(false);

    // Then
    assertFalse(future1.get());
    waiter.await(1000);

    // Given
    CompletableFuture<Boolean> future2 = Failsafe.with(retryNever).onFailure(e -> {
      waiter.assertTrue(e.getFailure() instanceof IllegalArgumentException);
      waiter.resume();
    }).getAsync(() -> {
      waiter.resume();
      Thread.sleep(500);
      return true;
    });
    waiter.await(1000);

    // When completed exceptionally
    future2.completeExceptionally(new IllegalArgumentException());

    // Then
    assertThrows(() -> unwrapExceptions(future2::get), IllegalArgumentException.class);
    waiter.await(1000);
  }

  /**
   * Tests a scenario where three timeouts should cause all delegates to be cancelled with interrupts.
   */
  public void shouldCancelNestedTimeoutsWithInterrupt() throws Throwable {
    // Given
    RetryPolicy<Boolean> rp = new RetryPolicy<Boolean>().onRetry(e -> System.out.println("Retrying"));
    Timeout<Boolean> timeout1 = Timeout.of(Duration.ofMillis(1000));
    Timeout<Boolean> timeout2 = Timeout.<Boolean>of(Duration.ofMillis(200)).withInterrupt(true);
    AtomicReference<FailsafeFuture<Boolean>> futureRef = new AtomicReference<>();
    CountDownLatch futureLatch = new CountDownLatch(1);

    // When
    FailsafeFuture<Boolean> future = (FailsafeFuture<Boolean>) Failsafe.with(rp, timeout2, timeout1).onComplete(e -> {
      waiter.assertNull(e.getResult());
      waiter.assertTrue(e.getFailure() instanceof TimeoutExceededException);
      waiter.resume();
    }).getAsync(ctx -> {
      // Wait for futureRef to be set
      futureLatch.await();
      waiter.assertTrue(ctx.getLastFailure() == null || ctx.getLastFailure() instanceof TimeoutExceededException);

      try {
        // Assert not cancelled
        waiter.assertFalse(ctx.isCancelled());
        if (!futureRef.get().getTimeoutDelegates().isEmpty())
          waiter.assertFalse(futureRef.get().getTimeoutDelegates().stream().allMatch(Future::isCancelled));
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        // Assert cancelled
        waiter.assertTrue(ctx.isCancelled());
        waiter.resume();
        throw e;
      }
      waiter.fail("Expected interruption");
      return false;
    });
    futureRef.set(future);
    futureLatch.countDown();

    // Then
    waiter.await(1000, 4);
    assertFalse(future.isCancelled());
    assertTrue(future.isDone());
    assertThrows(future::get, ExecutionException.class, TimeoutExceededException.class);
  }

  private void assertCancel(Function<FailsafeExecutor<?>, Future<?>> executorCallable, Policy<?> policy)
    throws Throwable {
    // Given
    FailsafeFuture<?> future = (FailsafeFuture<?>) executorCallable.apply(
      Failsafe.with(policy).with(executor).onComplete(e -> {
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
    assertTrue(
      future.getTimeoutDelegates() == null || future.getTimeoutDelegates().stream().allMatch(Future::isCancelled));
    assertTrue(future.isDone());
    assertThrows(future::get, CancellationException.class);
  }

  public void shouldCancelOnGetAsync() throws Throwable {
    assertCancel(executor -> getAsync(executor, (ContextualSupplier<?, ?>) ctx -> {
      try {
        waiter.assertFalse(ctx.isCancelled());
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        waiter.assertTrue(ctx.isCancelled());
        throw e;
      }
      return "test";
    }), retryAlways);
  }

  public void shouldCancelOnGetAsyncWithTimeout() throws Throwable {
    assertCancel(executor -> getAsync(executor, (CheckedSupplier<?>) () -> {
      Thread.sleep(1000);
      return "test";
    }), Timeout.of(Duration.ofMinutes(1)));
  }

  public void shouldCancelOnGetAsyncExecution() throws Throwable {
    assertCancel(executor -> getAsync(executor, (AsyncRunnable<?>) (e) -> {
      Thread.sleep(1000);
      e.complete();
    }), retryAlways);
  }

  public void shouldCancelOnRunAsync() throws Throwable {
    assertCancel(executor -> runAsync(executor, (CheckedRunnable) () -> {
      Thread.sleep(1000);
    }), retryAlways);
  }

  public void shouldCancelOnRunAsyncExecution() throws Throwable {
    assertCancel(executor -> runAsync(executor, (AsyncRunnable) (e) -> {
      Thread.sleep(1000);
      e.complete();
    }), retryAlways);
  }

  public void shouldCancelOnGetStageAsync() throws Throwable {
    assertCancel(executor -> getStageAsync(executor, (CheckedSupplier<?>) () -> {
      Thread.sleep(1000);
      return CompletableFuture.completedFuture("test");
    }), retryAlways);
  }

  public void shouldCancelOnGetStageAsyncExecution() throws Throwable {
    assertCancel(executor -> getStageAsync(executor, (AsyncSupplier<?, ?>) (e) -> {
      Thread.sleep(1000);
      CompletableFuture<?> result = CompletableFuture.completedFuture("test");
      e.complete(result);
      return result;
    }), retryAlways);
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
   * Assert handles a supplier that returns an exceptionally completed future.
   */
  public void shouldHandleCompletedExceptionallyGetStageAsync() {
    CompletableFuture<Boolean> failedFuture = new CompletableFuture<>();
    failedFuture.completeExceptionally(new IllegalArgumentException());
    assertThrows(() -> Failsafe.with(retryTwice).getStageAsync(() -> failedFuture).get(), ExecutionException.class,
      IllegalArgumentException.class);

    assertThrows(() -> Failsafe.with(retryTwice).getStageAsync(context -> failedFuture).get(), ExecutionException.class,
      IllegalArgumentException.class);

    assertThrows(() -> Failsafe.with(retryTwice).getStageAsyncExecution(exec -> failedFuture).get(),
      ExecutionException.class, IllegalArgumentException.class);
  }

  /**
   * Asserts that asynchronous completion via an execution is supported.
   */
  public void shouldCompleteAsync() throws Throwable {
    Failsafe.with(retryAlways).runAsyncExecution(exec -> Scheduler.DEFAULT.schedule(() -> {
      try {
        exec.complete();
        waiter.resume();
      } catch (Exception e) {
        waiter.fail(e);
      }
      return null;
    }, 100, TimeUnit.MILLISECONDS));

    waiter.await(5000);
  }

  public void shouldTimeoutAndRetry() throws Throwable {
    // Given
    RetryPolicy<Boolean> rp = new RetryPolicy<Boolean>().withMaxRetries(2).handleResult(false);
    Timeout<Boolean> timeout = Timeout.of(Duration.ofMillis(1));

    // When / Then
    Failsafe.with(rp, timeout).onComplete(e -> {
      assertNull(e.getResult());
      assertTrue(e.getFailure() instanceof TimeoutExceededException);
      waiter.resume();
    }).getAsyncExecution(exec -> {
      Thread.sleep(100);
      if (!exec.complete(false))
        exec.retry();
    });

    waiter.await(1000);
  }

  public void shouldOpenCircuitWhenTimeoutExceeded() throws Throwable {
    // Given
    Timeout<Object> timeout = Timeout.of(Duration.ofMillis(10));
    CircuitBreaker<Object> breaker = new CircuitBreaker<>();
    assertTrue(breaker.isClosed());

    // When
    Failsafe.with(breaker, timeout).with(executor).runAsyncExecution(exec -> {
      Thread.sleep(100);
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

  public void testRetryPolicyScheduledDelayIsZero() throws Throwable {
    RetryPolicy<Object> rp = new RetryPolicy<>().onRetryScheduled(e -> {
      assertEquals(e.getDelay().toMillis(), 0);
      System.out.println(e.getDelay().toMillis());
      waiter.resume();
    });

    Failsafe.with(rp).runAsync(() -> {
      throw new IllegalStateException();
    });

    waiter.await(1000);
  }

  private void assertInterruptedExceptionOnCancel(FailsafeExecutor<Boolean> failsafe) throws Throwable {
    CompletableFuture<Void> future = failsafe.runAsync(() -> {
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

  public void shouldInterruptExecutionOnCancelWithForkJoinPool() throws Throwable {
    assertInterruptedExceptionOnCancel(Failsafe.with(retryAlways));
  }

  public void shouldInterruptExecutionOnCancelWithScheduledExecutorService() throws Throwable {
    ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
    assertInterruptedExceptionOnCancel(Failsafe.with(retryAlways).with(executorService));
    executorService.shutdownNow();
  }

  public void shouldInterruptExecutionOnCancelWithExecutorService() throws Throwable {
    assertInterruptedExceptionOnCancel(Failsafe.with(retryAlways).with(executor));
  }

  @SuppressWarnings("unused")
  public void shouldSupportCovariance() {
    FastService fastService = mock(FastService.class);
    CompletionStage<Service> stage = Failsafe.with(new RetryPolicy<Service>())
      .with(executor)
      .getAsync(() -> fastService);
  }

  @SuppressWarnings("unchecked")
  private Future<?> runAsync(FailsafeExecutor<?> failsafe, Object runnable) {
    if (runnable instanceof CheckedRunnable)
      return failsafe.runAsync((CheckedRunnable) runnable);
    else if (runnable instanceof ContextualRunnable)
      return failsafe.runAsync((ContextualRunnable) runnable);
    else
      return failsafe.runAsyncExecution((AsyncRunnable<Void>) runnable);
  }

  @SuppressWarnings("unchecked")
  private <T> Future<T> getAsync(FailsafeExecutor<T> failsafe, Object supplier) {
    if (supplier instanceof CheckedSupplier)
      return failsafe.getAsync((CheckedSupplier<T>) supplier);
    else if (supplier instanceof ContextualSupplier)
      return failsafe.getAsync((ContextualSupplier<T, T>) supplier);
    else
      return failsafe.getAsyncExecution((AsyncRunnable<T>) supplier);
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  private <T> CompletableFuture<T> getStageAsync(FailsafeExecutor<T> failsafe, Object supplier) {
    if (supplier instanceof CheckedSupplier)
      return failsafe.getStageAsync((CheckedSupplier) supplier);
    else if (supplier instanceof ContextualSupplier)
      return failsafe.getStageAsync((ContextualSupplier) supplier);
    else
      return failsafe.getStageAsyncExecution((AsyncSupplier) supplier);
  }
}
