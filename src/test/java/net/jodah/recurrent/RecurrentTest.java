package net.jodah.recurrent;

import static net.jodah.recurrent.Asserts.assertThrows;
import static net.jodah.recurrent.Testing.ignoreExceptions;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.net.SocketException;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import net.jodah.concurrentunit.Waiter;

@Test
public class RecurrentTest {
  private ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
  private RetryPolicy retryTwice = new RetryPolicy().withMaxRetries(2);
  private RetryPolicy retryAlways = new RetryPolicy();
  private Service service = mock(Service.class);
  private Waiter waiter;

  // Results from a synchronous Recurrent call
  @SuppressWarnings("unchecked") Class<? extends Throwable>[] syncThrowables = new Class[] {
      RuntimeException.class, SocketException.class };
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

  @SuppressWarnings("unchecked")
  private <T> Class<? extends Exception>[] failures(int numFailures,
      Class<? extends Exception> failureType) {
    Class<? extends Exception>[] failures = new Class[numFailures];
    for (int i = 0; i < numFailures; i++)
      failures[i] = failureType;
    return failures;
  }

  public void shouldRun() throws Throwable {
    Runnable runnable = () -> service.connect();

    // Fail twice then succeed
    when(service.connect()).thenThrow(failures(2, SocketException.class)).thenReturn(true);
    Recurrent.run(runnable, new RetryPolicy());
    verify(service, times(3)).connect();

    // Fail three times
    reset(service);
    when(service.connect()).thenThrow(failures(10, SocketException.class)).thenReturn(true);
    assertThrows(() -> Recurrent.run(runnable, retryTwice), syncThrowables);
    verify(service, times(3)).connect();
  }

  private void assertRunWithExecutor(Object runnable) throws Throwable {
    // Given - Fail twice then succeed
    when(service.connect()).thenThrow(failures(2, SocketException.class)).thenReturn(true);

    // When
    RecurrentFuture<?> future = runnable instanceof Runnable
        ? Recurrent.run((Runnable) runnable, new RetryPolicy(), executor)
        : Recurrent.run((ContextualRunnable) runnable, new RetryPolicy(), executor);

    // Then
    waiter.expectResume();
    future.whenComplete((result, failure) -> {
      waiter.assertNull(result);
      waiter.assertNull(failure);
      waiter.resume();
    });
    assertNull(future.get());
    waiter.await();
    verify(service, times(3)).connect();

    // Given - Fail three times
    reset(service);
    when(service.connect()).thenThrow(failures(10, SocketException.class)).thenReturn(true);

    // When
    RecurrentFuture<?> future2 = runnable instanceof Runnable
        ? Recurrent.run((Runnable) runnable, retryTwice, executor)
        : Recurrent.run((ContextualRunnable) runnable, retryTwice, executor);

    // Then
    waiter.expectResume();
    future2.whenComplete((result, failure) -> {
      waiter.assertNull(result);
      waiter.assertTrue(failure instanceof SocketException);
      waiter.resume();
    });
    assertThrows(() -> future2.get(), futureAsyncThrowables);
    waiter.await();
    verify(service, times(3)).connect();
  }

  public void shouldRunWithExecutor() throws Throwable {
    assertRunWithExecutor((Runnable) () -> service.connect());
  }

  public void shouldRunContextualWithExecutor() throws Throwable {
    assertRunWithExecutor((ContextualRunnable) (ctx) -> {
      try {
        service.connect();
      } catch (Exception failure) {
        // Alternate between automatic and manual retries
        if (ctx.getRetryCount() % 2 == 0)
          throw failure;
        if (!ctx.retry(failure))
          throw failure;
      }
    });
  }

  public void shouldGet() throws Throwable {
    Callable<Boolean> callable = () -> service.connect();

    // Fail twice then succeed
    when(service.connect()).thenThrow(failures(2, SocketException.class)).thenReturn(true);
    assertEquals(Recurrent.get(callable, new RetryPolicy()), Boolean.TRUE);
    verify(service, times(3)).connect();

    // Fail three times
    reset(service);
    when(service.connect()).thenThrow(failures(10, SocketException.class)).thenReturn(true);
    assertThrows(() -> Recurrent.get(callable, retryTwice), syncThrowables);
    verify(service, times(3)).connect();
  }

  @SuppressWarnings({ "rawtypes", "unchecked" })
  private void assertGetWithExecutor(Object callable) throws Throwable {
    // Given - Fail twice then succeed
    when(service.connect()).thenThrow(failures(2, SocketException.class)).thenReturn(true);

    // When
    RecurrentFuture<Boolean> future = callable instanceof Callable
        ? Recurrent.get((Callable<Boolean>) callable, new RetryPolicy(), executor)
        : Recurrent.get((ContextualCallable<Boolean>) callable, new RetryPolicy(), executor);

    // Then
    waiter.expectResume();
    future.whenComplete((result, failure) -> {
      waiter.assertTrue(result);
      waiter.assertNull(failure);
      waiter.resume();
    });
    assertTrue(future.get());
    waiter.await();
    verify(service, times(3)).connect();

    // Given - Fail three times
    reset(service);
    when(service.connect()).thenThrow(failures(10, SocketException.class)).thenReturn(true);

    // When
    RecurrentFuture<Boolean> future2 = callable instanceof Callable
        ? Recurrent.get((Callable) callable, retryTwice, executor)
        : Recurrent.get((ContextualCallable) callable, retryTwice, executor);

    // Then
    waiter.expectResume();
    future2.whenComplete((result, failure) -> {
      waiter.assertNull(result);
      waiter.assertTrue(failure instanceof SocketException);
      waiter.resume();
    });
    assertThrows(() -> future2.get(), futureAsyncThrowables);
    waiter.await();
    verify(service, times(3)).connect();
  }

  public void shouldGetWithExecutor() throws Throwable {
    assertGetWithExecutor((Callable<?>) () -> service.connect());
  }

  public void shouldGetContextualWithExecutor() throws Throwable {
    assertGetWithExecutor((ContextualCallable<?>) (ctx) -> {
      try {
        return service.connect();
      } catch (Exception failure) {
        // Alternate between automatic and manual retries
        if (ctx.getRetryCount() % 2 == 0)
          throw failure;
        if (!ctx.retry(failure))
          throw failure;
        return null;
      }
    });
  }

  @SuppressWarnings({ "rawtypes", "unchecked" })
  private void assertGetFuture(Object callable) throws Throwable {
    // Given - Fail twice then succeed
    when(service.connect()).thenThrow(failures(2, SocketException.class)).thenReturn(true);

    // When
    CompletableFuture<Boolean> future = callable instanceof Callable
        ? Recurrent.future((Callable) callable, new RetryPolicy(), executor)
        : Recurrent.future((ContextualCallable) callable, new RetryPolicy(), executor);

    // Then
    waiter.expectResume();
    future.whenComplete((result, failure) -> {
      waiter.assertTrue(result);
      waiter.assertNull(failure);
      waiter.resume();
    });
    assertTrue(future.get());
    waiter.await();
    verify(service, times(3)).connect();

    // Given - Fail three times
    reset(service);
    when(service.connect()).thenThrow(failures(10, SocketException.class)).thenReturn(true);

    // When
    CompletableFuture<Boolean> future2 = callable instanceof Callable
        ? Recurrent.future((Callable) callable, retryTwice, executor)
        : Recurrent.future((ContextualCallable) callable, retryTwice, executor);

    // Then
    waiter.expectResume();
    future2.whenComplete((result, failure) -> {
      waiter.assertNull(result);
      waiter.assertTrue(failure instanceof SocketException);
      waiter.resume();
    });
    assertThrows(() -> future2.get(), futureAsyncThrowables);
    waiter.await();
    verify(service, times(3)).connect();
  }

  public void testFuture() throws Throwable {
    assertGetFuture((Callable<?>) () -> CompletableFuture.supplyAsync(() -> service.connect()));
  }

  public void testFutureContextual() throws Throwable {
    assertGetFuture((ContextualCallable<?>) (ctx) -> CompletableFuture.supplyAsync(() -> {
      try {
        return service.connect();
      } catch (Exception failure) {
        // Alternate between automatic and manual retries
        if (ctx.getRetryCount() % 2 == 0)
          throw failure;
        if (!ctx.retry(failure))
          throw failure;
        return null;
      }
    }));
  }

  public void testPerStageRetries() throws Throwable {
    // Fail twice then succeed
    when(service.connect()).thenThrow(failures(2, SocketException.class)).thenReturn(true);
    when(service.disconnect()).thenThrow(failures(2, SocketException.class)).thenReturn(true);
    CompletableFuture.supplyAsync(() -> Recurrent.get(() -> service.connect(), retryAlways))
        .thenRun(() -> Recurrent.get(() -> service.disconnect(), retryAlways))
        .get();
    verify(service, times(3)).connect();
    verify(service, times(3)).disconnect();

    // Fail three times
    reset(service);
    when(service.connect()).thenThrow(failures(10, SocketException.class)).thenReturn(true);
    assertThrows(
        () -> CompletableFuture
            .supplyAsync(() -> Recurrent.get(() -> service.connect(), retryTwice)).get(),
        futureSyncThrowables);
    verify(service, times(3)).connect();
  }

  public void shouldCancelFuture() throws Throwable {
    RecurrentFuture<?> future = Recurrent.run(() -> ignoreExceptions(() -> Thread.sleep(10000)),
        retryAlways, executor);
    future.cancel(true);
    assertTrue(future.isCancelled());
  }
}
