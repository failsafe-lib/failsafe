package net.jodah.recurrent;

import static net.jodah.recurrent.Asserts.assertThrows;
import static net.jodah.recurrent.Asserts.matches;
import static net.jodah.recurrent.Testing.failures;
import static net.jodah.recurrent.Testing.ignoreExceptions;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.net.ConnectException;
import java.net.SocketException;
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

@Test
public class AsyncRecurrentTest extends AbstractRecurrentTest {
  private ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
  private Waiter waiter;

  // Results from a get against a future that wraps an asynchronous Recurrent call
  private @SuppressWarnings("unchecked") Class<? extends Throwable>[] futureAsyncThrowables = new Class[] {
      ExecutionException.class, SocketException.class };

  @BeforeMethod
  protected void beforeMethod() {
    reset(service);
    waiter = new Waiter();
    counter = new AtomicInteger();
  }

  private void assertRunWithExecutor(Object runnable) throws Throwable {
    // Given - Fail twice then succeed
    when(service.connect()).thenThrow(failures(2, SocketException.class)).thenReturn(true);

    // When
    RecurrentFuture<?> future = run(Recurrent.with(retryAlways, executor), runnable);

    // Then
    future.whenComplete((result, failure) -> {
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
    when(service.connect()).thenThrow(failures(10, SocketException.class));

    // When
    RecurrentFuture<?> future2 = run(Recurrent.with(retryTwice, executor), runnable);

    // Then
    future2.whenComplete((result, failure) -> {
      waiter.assertNull(result);
      waiter.assertTrue(failure instanceof SocketException);
      waiter.resume();
    });
    assertThrows(() -> future2.get(), futureAsyncThrowables);
    waiter.await(3000);
    verify(service, times(3)).connect();
  }

  public void shouldRunWithExecutor() throws Throwable {
    assertRunWithExecutor((CheckedRunnable) () -> service.connect());
  }

  public void shouldRunContextualWithExecutor() throws Throwable {
    assertRunWithExecutor((ContextualRunnable) stats -> {
      assertEquals(stats.getAttemptCount(), counter.getAndIncrement());
      service.connect();
    });
  }

  public void shouldRunAsync() throws Throwable {
    assertRunWithExecutor((AsyncRunnable) inv -> {
      try {
        service.connect();
        inv.complete();
      } catch (Exception failure) {
        // Alternate between automatic and manual retries
        if (inv.getAttemptCount() % 2 == 0)
          throw failure;
        if (!inv.retryOn(failure))
          throw failure;
      }
    });
  }

  private void assertGetWithExecutor(Object callable) throws Throwable {
    // Given - Fail twice then succeed
    when(service.connect()).thenThrow(failures(2, SocketException.class)).thenReturn(false, false, true);
    RetryPolicy retryPolicy = new RetryPolicy().retryWhen(false);

    // When
    RecurrentFuture<Boolean> future = get(Recurrent.with(retryPolicy, executor), callable);

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
    counter.set(0);
    when(service.connect()).thenThrow(failures(10, SocketException.class));

    // When
    RecurrentFuture<Boolean> future2 = get(Recurrent.with(retryTwice, executor), callable);

    // Then
    future2.whenComplete((result, failure) -> {
      waiter.assertNull(result);
      waiter.assertTrue(failure instanceof SocketException);
      waiter.resume();
    });
    assertThrows(() -> future2.get(), futureAsyncThrowables);
    waiter.await(3000);
    verify(service, times(3)).connect();
  }

  public void shouldGetWithExecutor() throws Throwable {
    assertGetWithExecutor((Callable<?>) () -> service.connect());
  }

  public void shouldGetContextualWithExecutor() throws Throwable {
    assertGetWithExecutor((ContextualCallable<Boolean>) stats -> {
      assertEquals(stats.getAttemptCount(), counter.getAndIncrement());
      return service.connect();
    });
  }

  public void shouldGetAsync() throws Throwable {
    assertGetWithExecutor((AsyncCallable<?>) inv -> {
      try {
        boolean result = service.connect();
        if (!inv.complete(result))
          inv.retryFor(result);
        return result;
      } catch (Exception failure) {
        // Alternate between automatic and manual retries
        if (inv.getAttemptCount() % 2 == 0)
          throw failure;
        if (!inv.retryOn(failure))
          throw failure;
        return null;
      }
    });
  }

  private void assertGetFuture(Object callable) throws Throwable {
    // Given - Fail twice then succeed
    when(service.connect()).thenThrow(failures(2, SocketException.class)).thenReturn(false, false, true);
    RetryPolicy retryPolicy = new RetryPolicy().retryWhen(false);

    // When
    CompletableFuture<Boolean> future = future(Recurrent.with(retryPolicy, executor), callable);

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
    when(service.connect()).thenThrow(failures(10, SocketException.class));

    // When
    CompletableFuture<Boolean> future2 = future(Recurrent.with(retryTwice, executor), callable);

    // Then
    future2.whenComplete((result, failure) -> {
      waiter.assertNull(result);
      waiter.assertTrue(matches(failure, SocketException.class));
      waiter.resume();
    });
    assertThrows(() -> future2.get(), futureAsyncThrowables);
    waiter.await(3000);
    verify(service, times(3)).connect();
  }

  public void testFuture() throws Throwable {
    assertGetFuture((Callable<?>) () -> CompletableFuture.supplyAsync(() -> service.connect()));
  }

  public void testFutureContextual() throws Throwable {
    assertGetFuture((ContextualCallable<?>) stats -> CompletableFuture.supplyAsync(() -> service.connect()));
  }

  public void testFutureAsync() throws Throwable {
    assertGetFuture((AsyncCallable<?>) inv -> CompletableFuture.supplyAsync(() -> {
      try {
        boolean result = service.connect();
        if (!inv.complete(result))
          inv.retryFor(result);
        return result;
      } catch (Exception failure) {
        // Alternate between automatic and manual retries
        if (inv.getAttemptCount() % 2 == 0)
          throw failure;
        if (!inv.retryOn(failure))
          throw failure;
        return null;
      }
    }));
  }

  public void shouldCancelFuture() throws Throwable {
    RecurrentFuture<?> future = Recurrent.with(retryAlways, executor)
        .run(() -> ignoreExceptions(() -> Thread.sleep(10000)));
    future.cancel(true);
    assertTrue(future.isCancelled());
  }

  public void shouldManuallyRetryAndComplete() throws Throwable {
    Recurrent.with(retryAlways, executor).getAsync(inv -> {
      if (inv.getAttemptCount() < 2)
        inv.retryOn(new ConnectException());
      else
        inv.complete(true);
      return true;
    }).whenComplete((result, failure) -> {
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
    assertThrows(() -> Recurrent.with(retryTwice, executor).future(() -> {
      throw new IllegalArgumentException();
    }).get(), ExecutionException.class, IllegalArgumentException.class);

    assertThrows(() -> Recurrent.with(retryTwice, executor).future(stats -> {
      throw new IllegalArgumentException();
    }).get(), ExecutionException.class, IllegalArgumentException.class);

    assertThrows(() -> Recurrent.with(retryTwice, executor).futureAsync(inv -> {
      throw new IllegalArgumentException();
    }).get(), ExecutionException.class, IllegalArgumentException.class);
  }

  /**
   * Asserts that asynchronous completion via an execution is supported.
   */
  public void shouldCompleteAsync() throws Throwable {
    Waiter waiter = new Waiter();
    Recurrent.with(retryAlways, executor).runAsync(inv -> executor.schedule(() -> {
      try {
        inv.complete();
        waiter.resume();
      } catch (Exception e) {
        waiter.fail(e);
      }
    } , 100, TimeUnit.MILLISECONDS));

    waiter.await(5000);
  }

  /**
   * Asserts that retries are performed then a non-retryable failure is thrown.
   */
  @SuppressWarnings("unchecked")
  public void shouldThrowOnNonRetriableFailure() throws Throwable {
    // Given
    when(service.connect()).thenThrow(ConnectException.class, ConnectException.class, IllegalStateException.class);
    RetryPolicy retryPolicy = new RetryPolicy().retryOn(ConnectException.class);

    // When / Then
    assertThrows(() -> Recurrent.with(retryPolicy, executor).get(() -> service.connect()).get(),
        ExecutionException.class, IllegalStateException.class);
    verify(service, times(3)).connect();
  }

  /**
   * Asserts that retries are not attempted after a successful execution.
   */
  public void shouldSucceedWithoutRetries() throws Throwable {
    // Given retries not allowed
    reset(service);
    when(service.connect()).thenReturn(false);

    // When / Then
    assertEquals(Recurrent.with(retryNever, executor).get(() -> service.connect()).get(), Boolean.FALSE);
    verify(service).connect();
  }

  /**
   * Asserts that Recurrent handles an initial scheduling failure.
   */
  public void shouldHandleInitialSchedulingFailure() throws Throwable {
    // Given
    ScheduledExecutorService executor = Executors.newScheduledThreadPool(0);
    executor.shutdownNow();

    Waiter waiter = new Waiter();

    // When
    @SuppressWarnings("unchecked")
    RecurrentFuture<Void> future = Recurrent.with(new RetryPolicy().retryWhen(null).retryOn(Exception.class), executor)
        .run(() -> {
          waiter.fail("Should not execute callable since executor has been shutdown");
        });

    assertThrows(() -> future.get(), ExecutionException.class, RejectedExecutionException.class);
  }

  /**
   * Asserts that Recurrent handles a retry scheduling failure.
   */
  public void shouldHandleRejectedRetryExecution() throws Throwable {
    // Given
    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    AtomicInteger counter = new AtomicInteger();

    // When
    @SuppressWarnings("unchecked")
    RecurrentFuture<String> future = Recurrent
        .with(new RetryPolicy().retryWhen(null).retryOn(Exception.class), executor).get(() -> {
          counter.incrementAndGet();
          Thread.sleep(200);
          return null;
        });

    Thread.sleep(150);
    executor.shutdownNow();
    assertThrows(() -> future.get(), ExecutionException.class, RejectedExecutionException.class);
    assertEquals(counter.get(), 1, "Callable should have been executed before executor was shutdown");
  }

  private RecurrentFuture<?> run(AsyncRecurrent recurrent, Object runnable) {
    if (runnable instanceof CheckedRunnable)
      return recurrent.run((CheckedRunnable) runnable);
    else if (runnable instanceof ContextualRunnable)
      return recurrent.run((ContextualRunnable) runnable);
    else
      return recurrent.runAsync((AsyncRunnable) runnable);
  }

  @SuppressWarnings("unchecked")
  private <T> RecurrentFuture<T> get(AsyncRecurrent recurrent, Object callable) {
    if (callable instanceof Callable)
      return recurrent.get((Callable<T>) callable);
    else if (callable instanceof ContextualCallable)
      return recurrent.get((ContextualCallable<T>) callable);
    else
      return recurrent.getAsync((AsyncCallable<T>) callable);
  }

  @SuppressWarnings("unchecked")
  private <T> CompletableFuture<T> future(AsyncRecurrent recurrent, Object callable) {
    if (callable instanceof Callable)
      return recurrent.future((Callable<CompletableFuture<T>>) callable);
    else if (callable instanceof ContextualCallable)
      return recurrent.future((ContextualCallable<CompletableFuture<T>>) callable);
    else
      return recurrent.futureAsync((AsyncCallable<CompletableFuture<T>>) callable);
  }

}
