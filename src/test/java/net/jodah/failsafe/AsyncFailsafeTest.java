package net.jodah.failsafe;

import static net.jodah.failsafe.Asserts.assertThrows;
import static net.jodah.failsafe.Asserts.matches;
import static net.jodah.failsafe.Testing.failures;
import static net.jodah.failsafe.Testing.ignoreExceptions;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import net.jodah.concurrentunit.Waiter;
import net.jodah.failsafe.function.AsyncCallable;
import net.jodah.failsafe.function.AsyncRunnable;
import net.jodah.failsafe.function.CheckedRunnable;
import net.jodah.failsafe.function.ContextualCallable;
import net.jodah.failsafe.function.ContextualRunnable;

@Test
public class AsyncFailsafeTest extends AbstractFailsafeTest {
  private ScheduledExecutorService executor = Executors.newScheduledThreadPool(5);
  private Waiter waiter;

  // Results from a get against a future that wraps an asynchronous Failsafe call
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

  private void assertRunWithExecutor(Object runnable) throws Throwable {
    // Given - Fail twice then succeed
    when(service.connect()).thenThrow(failures(2, new ConnectException())).thenReturn(true);

    // When
    FailsafeFuture<?> future = run(Failsafe.with(retryAlways).with(executor), runnable);

    // Then
    future.onComplete((result, failure) -> {
      waiter.assertNull(result);
      waiter.assertNull(failure);
      waiter.resume();
    });
    assertNull(future.get());
    waiter.await(3000);
    verify(service, times(3)).connect();

    // Given - Fail three times
    reset(service);
    counter.set(0);
    when(service.connect()).thenThrow(failures(10, new ConnectException()));

    // When
    FailsafeFuture<?> future2 = run(Failsafe.with(retryTwice).with(executor), runnable);

    // Then
    future2.onComplete((result, failure) -> {
      waiter.assertNull(result);
      waiter.assertTrue(failure instanceof ConnectException);
      waiter.resume();
    });
    assertThrows(() -> future2.get(), futureAsyncThrowables);
    waiter.await(3000);
    verify(service, times(3)).connect();
  }

  public void shouldRunWithExecutor() throws Throwable {
    assertRunWithExecutor((CheckedRunnable) service::connect);
  }

  public void shouldRunContextualWithExecutor() throws Throwable {
    assertRunWithExecutor((ContextualRunnable) context -> {
      assertEquals(context.getExecutions(), counter.getAndIncrement());
      service.connect();
    });
  }

  public void shouldRunAsync() throws Throwable {
    assertRunWithExecutor((AsyncRunnable) exec -> {
      try {
        service.connect();
        exec.complete();
      } catch (Exception failure) {
        // Alternate between automatic and manual retries
        if (exec.getExecutions() % 2 == 0)
          throw failure;
        if (!exec.retryOn(failure))
          throw failure;
      }
    });
  }

  private void assertGetWithExecutor(Object callable) throws Throwable {
    // Given - Fail twice then succeed
    when(service.connect()).thenThrow(failures(2, new ConnectException())).thenReturn(false, false, true);
    RetryPolicy retryPolicy = new RetryPolicy().retryWhen(false);

    // When
    FailsafeFuture<Boolean> future = get(Failsafe.with(retryPolicy).with(executor), callable);

    // Then
    future.onComplete((result, failure) -> {
      waiter.assertTrue(result);
      waiter.assertNull(failure);
      waiter.resume();
    });
    assertTrue(future.get());
    waiter.await(3000);
    verify(service, times(5)).connect();

    // Given - Fail three times
    reset(service);
    counter.set(0);
    when(service.connect()).thenThrow(failures(10, new ConnectException()));

    // When
    FailsafeFuture<Boolean> future2 = get(Failsafe.with(retryTwice).with(executor), callable);

    // Then
    future2.onComplete((result, failure) -> {
      waiter.assertNull(result);
      waiter.assertTrue(failure instanceof ConnectException);
      waiter.resume();
    });
    assertThrows(() -> future2.get(), futureAsyncThrowables);
    waiter.await(3000);
    verify(service, times(3)).connect();
  }

  public void shouldGetWithExecutor() throws Throwable {
    assertGetWithExecutor((Callable<?>) service::connect);
  }

  public void shouldGetContextualWithExecutor() throws Throwable {
    assertGetWithExecutor((ContextualCallable<Boolean>) context -> {
      assertEquals(context.getExecutions(), counter.getAndIncrement());
      return service.connect();
    });
  }

  public void shouldGetAsync() throws Throwable {
    assertGetWithExecutor((AsyncCallable<?>) exec -> {
      try {
        boolean result = service.connect();
        if (!exec.complete(result))
          exec.retryFor(result);
        return result;
      } catch (Exception failure) {
        // Alternate between automatic and manual retries
        if (exec.getExecutions() % 2 == 0)
          throw failure;
        if (!exec.retryOn(failure))
          throw failure;
        return null;
      }
    });
  }

  private void assertGetFuture(Object callable) throws Throwable {
    // Given - Fail twice then succeed
    when(service.connect()).thenThrow(failures(2, new ConnectException())).thenReturn(false, false, true);
    RetryPolicy retryPolicy = new RetryPolicy().retryWhen(false);

    // When
    CompletableFuture<Boolean> future = future(Failsafe.with(retryPolicy).with(executor), callable);

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
    CompletableFuture<Boolean> future2 = future(Failsafe.with(retryTwice).with(executor), callable);

    // Then
    future2.whenComplete((result, failure) -> {
      waiter.assertNull(result);
      waiter.assertTrue(matches(failure, ConnectException.class));
      waiter.resume();
    });
    assertThrows(() -> future2.get(), futureAsyncThrowables);
    waiter.await(3000);
    verify(service, times(3)).connect();
  }

  public void testFuture() throws Throwable {
    assertGetFuture((Callable<?>) () -> CompletableFuture.supplyAsync(service::connect));
  }

  public void testFutureContextual() throws Throwable {
    assertGetFuture((ContextualCallable<?>) context -> CompletableFuture.supplyAsync(service::connect));
  }

  public void testFutureAsync() throws Throwable {
    assertGetFuture((AsyncCallable<?>) exec -> CompletableFuture.supplyAsync(() -> {
      try {
        boolean result = service.connect();
        if (!exec.complete(result))
          exec.retryFor(result);
        return result;
      } catch (Exception failure) {
        // Alternate between automatic and manual retries
        if (exec.getExecutions() % 2 == 0)
          throw failure;
        if (!exec.retryOn(failure))
          throw failure;
        return null;
      }
    }));
  }

  public void shouldCancelFuture() throws Throwable {
    FailsafeFuture<?> future = Failsafe.with(retryAlways)
        .with(executor)
        .run(() -> ignoreExceptions(() -> Thread.sleep(10000)));
    future.cancel(true);
    assertTrue(future.isCancelled());
  }

  public void shouldManuallyRetryAndComplete() throws Throwable {
    Failsafe.with(retryAlways).with(executor).getAsync(exec -> {
      if (exec.getExecutions() < 2)
        exec.retryOn(new ConnectException());
      else
        exec.complete(true);
      return true;
    }).onComplete((result, failure) -> {
      waiter.assertTrue(result);
      waiter.assertNull(failure);
      waiter.resume();
    });
    waiter.await(3000);
  }

  /**
   * Assert handles a callable that throws instead of returning a future.
   */
  public void shouldHandleThrowingFutureCallable() {
    assertThrows(() -> Failsafe.with(retryTwice).with(executor).future(() -> {
      throw new IllegalArgumentException();
    }).get(), ExecutionException.class, IllegalArgumentException.class);

    assertThrows(() -> Failsafe.with(retryTwice).with(executor).future(context -> {
      throw new IllegalArgumentException();
    }).get(), ExecutionException.class, IllegalArgumentException.class);

    assertThrows(() -> Failsafe.with(retryTwice).with(executor).futureAsync(exec -> {
      throw new IllegalArgumentException();
    }).get(), ExecutionException.class, IllegalArgumentException.class);
  }

  /**
   * Asserts that asynchronous completion via an execution is supported.
   */
  public void shouldCompleteAsync() throws Throwable {
    Waiter waiter = new Waiter();
    Failsafe.with(retryAlways).with(executor).runAsync(exec -> executor.schedule(() -> {
      try {
        exec.complete();
        waiter.resume();
      } catch (Exception e) {
        waiter.fail(e);
      }
    } , 100, TimeUnit.MILLISECONDS));

    waiter.await(5000);
  }

  public void shouldOpenCircuitWhenTimeoutExceeded() throws Throwable {
    // Given
    CircuitBreaker breaker = new CircuitBreaker().withTimeout(10, TimeUnit.MILLISECONDS);
    assertTrue(breaker.isClosed());

    // When
    Failsafe.with(breaker).with(executor).runAsync(exec -> {
      Thread.sleep(20);
      exec.complete();
      waiter.resume();
    });

    // Then
    waiter.await(1000);
    assertTrue(breaker.isOpen());
  }

  /**
   * Asserts that Failsafe handles an initial scheduling failure.
   */
  public void shouldHandleInitialSchedulingFailure() throws Throwable {
    // Given
    ScheduledExecutorService executor = Executors.newScheduledThreadPool(0);
    executor.shutdownNow();

    Waiter waiter = new Waiter();

    // When
    @SuppressWarnings("unchecked")
    FailsafeFuture<Void> future = Failsafe.with(new RetryPolicy().retryWhen(null).retryOn(Exception.class))
        .with(executor)
        .run(() -> waiter.fail("Should not execute callable since executor has been shutdown"));

    assertThrows(() -> future.get(), ExecutionException.class, RejectedExecutionException.class);
  }

  /**
   * Asserts that Failsafe handles a retry scheduling failure.
   */
  public void shouldHandleRejectedRetryExecution() throws Throwable {
    // Given
    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    AtomicInteger counter = new AtomicInteger();

    // When
    @SuppressWarnings("unchecked")
    FailsafeFuture<String> future = Failsafe.with(new RetryPolicy().retryWhen(null).retryOn(Exception.class))
        .with(executor)
        .get(() -> {
          counter.incrementAndGet();
          Thread.sleep(200);
          return null;
        });

    Thread.sleep(150);
    executor.shutdownNow();
    assertThrows(() -> future.get(), ExecutionException.class, RejectedExecutionException.class);
    assertEquals(counter.get(), 1, "Callable should have been executed before executor was shutdown");
  }

  @SuppressWarnings("unused")
  public void shouldSupportCovariance() {
    FastService fastService = mock(FastService.class);
    FailsafeFuture<Service> future = Failsafe.with(new RetryPolicy()).with(executor).get(() -> fastService);
  }

  private FailsafeFuture<?> run(AsyncFailsafe failsafe, Object runnable) {
    if (runnable instanceof CheckedRunnable)
      return failsafe.run((CheckedRunnable) runnable);
    else if (runnable instanceof ContextualRunnable)
      return failsafe.run((ContextualRunnable) runnable);
    else
      return failsafe.runAsync((AsyncRunnable) runnable);
  }

  @SuppressWarnings("unchecked")
  private <T> FailsafeFuture<T> get(AsyncFailsafe failsafe, Object callable) {
    if (callable instanceof Callable)
      return failsafe.get((Callable<T>) callable);
    else if (callable instanceof ContextualCallable)
      return failsafe.get((ContextualCallable<T>) callable);
    else
      return failsafe.getAsync((AsyncCallable<T>) callable);
  }

  @SuppressWarnings("unchecked")
  private <T> CompletableFuture<T> future(AsyncFailsafe failsafe, Object callable) {
    if (callable instanceof Callable)
      return failsafe.future((Callable<CompletableFuture<T>>) callable);
    else if (callable instanceof ContextualCallable)
      return failsafe.future((ContextualCallable<CompletableFuture<T>>) callable);
    else
      return failsafe.futureAsync((AsyncCallable<CompletableFuture<T>>) callable);
  }
}
