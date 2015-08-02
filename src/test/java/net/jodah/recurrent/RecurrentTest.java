package net.jodah.recurrent;

import static net.jodah.recurrent.Asserts.assertThrows;
import static net.jodah.recurrent.Asserts.matches;
import static net.jodah.recurrent.Testing.*;
import static org.mockito.Mockito.mock;
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
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import net.jodah.concurrentunit.Waiter;

@Test
public class RecurrentTest {
  private ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
  private RetryPolicy retryAlways = new RetryPolicy();
  private RetryPolicy retryNever = new RetryPolicy().withMaxRetries(0);
  private RetryPolicy retryTwice = new RetryPolicy().withMaxRetries(2);
  private Service service = mock(Service.class);
  private Waiter waiter;

  // Results from a synchronous Recurrent call
  @SuppressWarnings("unchecked") Class<? extends Throwable>[] syncThrowables = new Class[] { RuntimeException.class,
      SocketException.class };
  // Results from a get against a future that wraps a synchronous Recurrent call
  @SuppressWarnings("unchecked") Class<? extends Throwable>[] futureSyncThrowables = new Class[] {
      ExecutionException.class, RuntimeException.class, SocketException.class };
  // Results from a get against a future that wraps an asynchronous Recurrent call
  @SuppressWarnings("unchecked") Class<? extends Throwable>[] futureAsyncThrowables = new Class[] {
      ExecutionException.class, SocketException.class };

  public interface Service {
    boolean connect();

    boolean disconnect();
  }

  @BeforeMethod
  protected void beforeMethod() {
    reset(service);
    waiter = new Waiter();
  }

  public void shouldRun() throws Throwable {
    Runnable runnable = () -> service.connect();

    // Given - Fail twice then succeed
    when(service.connect()).thenThrow(failures(2, SocketException.class)).thenReturn(true);

    // When
    Recurrent.run(runnable, retryAlways);

    // Then
    verify(service, times(3)).connect();

    // Given - Fail three times
    reset(service);
    when(service.connect()).thenThrow(failures(10, SocketException.class));

    // When / Then
    assertThrows(() -> Recurrent.run(runnable, retryTwice), syncThrowables);
    verify(service, times(3)).connect();
  }

  private void assertRunWithExecutor(Object runnable) throws Throwable {
    // Given - Fail twice then succeed
    when(service.connect()).thenThrow(failures(2, SocketException.class)).thenReturn(true);

    // When
    RecurrentFuture<?> future = runnable instanceof Runnable ? Recurrent.run((Runnable) runnable, retryAlways, executor)
        : Recurrent.run((ContextualRunnable) runnable, retryAlways, executor);

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
    when(service.connect()).thenThrow(failures(10, SocketException.class));

    // When
    RecurrentFuture<?> future2 = runnable instanceof Runnable ? Recurrent.run((Runnable) runnable, retryTwice, executor)
        : Recurrent.run((ContextualRunnable) runnable, retryTwice, executor);

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
    assertRunWithExecutor((Runnable) () -> service.connect());
  }

  public void shouldRunContextualWithExecutor() throws Throwable {
    assertRunWithExecutor((ContextualRunnable) (ctx) -> {
      try {
        service.connect();
        ctx.complete();
      } catch (Exception failure) {
        // Alternate between automatic and manual retries
        if (ctx.getRetryCount() % 2 == 0)
          throw failure;
        if (!ctx.retryOn(failure))
          throw failure;
      }
    });
  }

  public void shouldGet() throws Throwable {
    Callable<Boolean> callable = () -> service.connect();

    // Given - Fail twice then succeed
    when(service.connect()).thenThrow(failures(2, SocketException.class)).thenReturn(false, false, true);
    RetryPolicy retryPolicy = new RetryPolicy().retryWhen(false);

    assertEquals(Recurrent.get(callable, retryPolicy), Boolean.TRUE);
    verify(service, times(5)).connect();

    // Given - Fail three times
    reset(service);
    when(service.connect()).thenThrow(failures(10, SocketException.class));

    // When / Then
    assertThrows(() -> Recurrent.get(callable, retryTwice), syncThrowables);
    verify(service, times(3)).connect();
  }

  @SuppressWarnings({ "rawtypes", "unchecked" })
  private void assertGetWithExecutor(Object callable) throws Throwable {
    // Given - Fail twice then succeed
    when(service.connect()).thenThrow(failures(2, SocketException.class)).thenReturn(false, false, true);
    RetryPolicy retryPolicy = new RetryPolicy().retryWhen(false);

    // When
    RecurrentFuture<Boolean> future = callable instanceof Callable
        ? Recurrent.get((Callable<Boolean>) callable, retryPolicy, executor)
        : Recurrent.get((ContextualCallable<Boolean>) callable, retryPolicy, executor);

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
    RecurrentFuture<Boolean> future2 = callable instanceof Callable
        ? Recurrent.get((Callable) callable, retryTwice, executor)
        : Recurrent.get((ContextualCallable) callable, retryTwice, executor);

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
    assertGetWithExecutor((ContextualCallable<?>) (ctx) -> {
      try {
        boolean result = service.connect();
        if (!ctx.complete(result))
          ctx.retryWhen(result);
        return result;
      } catch (Exception failure) {
        // Alternate between automatic and manual retries
        if (ctx.getRetryCount() % 2 == 0)
          throw failure;
        if (!ctx.retryOn(failure))
          throw failure;
        return null;
      }
    });
  }

  @SuppressWarnings({ "rawtypes", "unchecked" })
  private void assertGetFuture(Object callable) throws Throwable {
    // Given - Fail twice then succeed
    when(service.connect()).thenThrow(failures(2, SocketException.class)).thenReturn(false, false, true);
    RetryPolicy retryPolicy = new RetryPolicy().retryWhen(false);

    // When
    CompletableFuture<Boolean> future = callable instanceof Callable
        ? Recurrent.future((Callable) callable, retryPolicy, executor)
        : Recurrent.future((ContextualCallable) callable, retryPolicy, executor);

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
    CompletableFuture<Boolean> future2 = callable instanceof Callable
        ? Recurrent.future((Callable) callable, retryTwice, executor)
        : Recurrent.future((ContextualCallable) callable, retryTwice, executor);

    // Then
    future2.whenComplete((result, failure) -> {
      waiter.assertNull(result);
      waiter.assertTrue(matches(failure, CompletionException.class, SocketException.class));
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
    assertGetFuture((ContextualCallable<?>) (ctx) -> CompletableFuture.supplyAsync(() -> {
      try {        
        boolean result = service.connect();
        if (!ctx.complete(result))
          ctx.retryWhen(result);
        return result;
      } catch (Exception failure) {
        // Alternate between automatic and manual retries
        if (ctx.getRetryCount() % 2 == 0)
          throw failure;
        if (!ctx.retryOn(failure))
          throw failure;
        return null;
      }
    }));
  }

  public void testPerStageRetries() throws Throwable {
    // Given - Fail twice then succeed
    when(service.connect()).thenThrow(failures(2, SocketException.class)).thenReturn(false, true);
    when(service.disconnect()).thenThrow(failures(2, SocketException.class)).thenReturn(false, true);
    RetryPolicy retryPolicy = new RetryPolicy().retryWhen(false);

    // When
    CompletableFuture.supplyAsync(() -> Recurrent.get(() -> service.connect(), retryPolicy))
        .thenRun(() -> Recurrent.get(() -> service.disconnect(), retryPolicy))
        .get();

    // Then
    verify(service, times(4)).connect();
    verify(service, times(4)).disconnect();

    // Given - Fail three times
    reset(service);
    when(service.connect()).thenThrow(failures(10, SocketException.class));

    // When / Then
    assertThrows(() -> CompletableFuture.supplyAsync(() -> Recurrent.get(() -> service.connect(), retryTwice)).get(),
        futureSyncThrowables);
    verify(service, times(3)).connect();
  }

  public void shouldCancelFuture() throws Throwable {
    RecurrentFuture<?> future = Recurrent.run(() -> ignoreExceptions(() -> Thread.sleep(10000)), retryAlways, executor);
    future.cancel(true);
    assertTrue(future.isCancelled());
  }

  public void shouldManuallyRetryAndComplete() throws Throwable {
    Recurrent.get((ctx) -> {
      if (ctx.getRetryCount() < 2)
        ctx.retry();
      else
        ctx.complete(true);
      return true;
    } , retryAlways, executor).whenComplete((result, failure) -> {
      waiter.assertTrue(result);
      waiter.assertNull(failure);
      waiter.resume();
    });
    waiter.await();
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
    assertThrows(() -> Recurrent.get(() -> service.connect(), retryPolicy), IllegalStateException.class);
    verify(service, times(3)).connect();
  }

  /**
   * Asserts that retries are not attempted after a successful invocation.
   */
  public void shouldSucceedWithoutRetries() throws Throwable {
    // Given retries not allowed
    reset(service);
    when(service.connect()).thenReturn(false);

    // When / Then
    assertEquals(Recurrent.get(() -> service.connect(), retryNever), Boolean.FALSE);
    verify(service).connect();
  }

  /**
   * Asserts that asynchronous completion via an invocation is supported.
   */
  public void shouldCompleteAsync() throws Throwable {
    Waiter waiter = new Waiter();
    Recurrent.run(ctx -> executor.schedule(() -> {
      try {
        ctx.complete();
        waiter.resume();
      } catch (Exception e) {
        waiter.fail(e);
      }
    } , 100, TimeUnit.MILLISECONDS), retryAlways, executor);

    waiter.await(5000);
  }
}
