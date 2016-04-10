package net.jodah.recurrent;

import static net.jodah.recurrent.Asserts.assertThrows;
import static net.jodah.recurrent.Asserts.matches;
import static net.jodah.recurrent.Testing.failures;
import static net.jodah.recurrent.Testing.ignoreExceptions;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

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
  private AtomicInteger counter;
  private Waiter waiter;

  // Results from a synchronous Recurrent call
  @SuppressWarnings("unchecked") Class<? extends Throwable>[] syncThrowables = new Class[] { RecurrentException.class,
      SocketException.class };
  // Results from a get against a future that wraps a synchronous Recurrent call
  @SuppressWarnings("unchecked") Class<? extends Throwable>[] futureSyncThrowables = new Class[] {
      ExecutionException.class, RecurrentException.class, SocketException.class };
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
    counter = new AtomicInteger();
  }

  private void assertRun(Object runnable) throws Throwable {
    // Given - Fail twice then succeed
    when(service.connect()).thenThrow(failures(2, SocketException.class)).thenReturn(true);

    // When
    run(Recurrent.with(retryAlways), runnable);

    // Then
    verify(service, times(3)).connect();

    // Given - Fail three times
    reset(service);
    counter.set(0);
    when(service.connect()).thenThrow(failures(10, SocketException.class));

    // When / Then
    assertThrows(() -> {
      run(Recurrent.with(retryTwice), runnable);
    } , syncThrowables);
    verify(service, times(3)).connect();
  }

  public void shouldRun() throws Throwable {
    assertRun((CheckedRunnable) () -> service.connect());
  }

  public void shouldRunContextual() throws Throwable {
    assertRun((ContextualRunnable) stats -> {
      assertEquals(stats.getAttemptCount(), counter.getAndIncrement());
      service.connect();
    });
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

  private void assertGet(Object callable) throws Throwable {
    // Given - Fail twice then succeed
    when(service.connect()).thenThrow(failures(2, SocketException.class)).thenReturn(false, false, true);
    RetryPolicy retryPolicy = new RetryPolicy().retryWhen(false);

    assertEquals(get(Recurrent.with(retryPolicy), callable), Boolean.TRUE);
    verify(service, times(5)).connect();

    // Given - Fail three times
    reset(service);
    counter.set(0);
    when(service.connect()).thenThrow(failures(10, SocketException.class));

    // When / Then
    assertThrows(() -> get(Recurrent.with(retryTwice), callable), syncThrowables);
    verify(service, times(3)).connect();
  }

  public void shouldGet() throws Throwable {
    assertGet((Callable<Boolean>) () -> service.connect());
  }

  public void shouldGetContextual() throws Throwable {
    assertGet((ContextualCallable<Boolean>) stats -> {
      assertEquals(stats.getAttemptCount(), counter.getAndIncrement());
      return service.connect();
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

  public void testPerStageRetries() throws Throwable {
    // Given - Fail twice then succeed
    when(service.connect()).thenThrow(failures(2, SocketException.class)).thenReturn(false, true);
    when(service.disconnect()).thenThrow(failures(2, SocketException.class)).thenReturn(false, true);
    RetryPolicy retryPolicy = new RetryPolicy().retryWhen(false);

    // When
    CompletableFuture.supplyAsync(() -> Recurrent.with(retryPolicy).get(() -> service.connect()))
        .thenRun(() -> Recurrent.with(retryPolicy).get(() -> service.disconnect()))
        .get();

    // Then
    verify(service, times(4)).connect();
    verify(service, times(4)).disconnect();

    // Given - Fail three times
    reset(service);
    when(service.connect()).thenThrow(failures(10, SocketException.class));

    // When / Then
    assertThrows(
        () -> CompletableFuture.supplyAsync(() -> Recurrent.with(retryTwice).get(() -> service.connect())).get(),
        futureSyncThrowables);
    verify(service, times(3)).connect();
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
   * Asserts that retries are performed then a non-retryable failure is thrown.
   */
  @SuppressWarnings("unchecked")
  public void shouldThrowOnNonRetriableFailure() throws Throwable {
    // Given
    when(service.connect()).thenThrow(ConnectException.class, ConnectException.class, IllegalStateException.class);
    RetryPolicy retryPolicy = new RetryPolicy().retryOn(ConnectException.class);

    // When / Then
    assertThrows(() -> Recurrent.with(retryPolicy).get(() -> service.connect()), RecurrentException.class,
        IllegalStateException.class);
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
    assertEquals(Recurrent.with(retryNever).get(() -> service.connect()), Boolean.FALSE);
    verify(service).connect();
  }

  /**
   * Asserts that asynchronous completion via an invocation is supported.
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
   * Asserts that Recurrent throws when interrupting a waiting thread.
   */
  public void shouldThrowWhenInterruptedDuringSynchronousDelay() throws Throwable {
    Thread mainThread = Thread.currentThread();
    new Thread(() -> {
      try {
        Thread.sleep(100);
        mainThread.interrupt();
      } catch (Exception e) {
      }
    }).start();

    try {
      Recurrent.with(new RetryPolicy().withDelay(5, TimeUnit.SECONDS)).run(() -> {
        throw new Exception();
      });
    } catch (Exception e) {
      assertTrue(e instanceof RecurrentException);
      assertTrue(e.getCause() instanceof InterruptedException);
    }
  }

  private void run(SyncRecurrent recurrent, Object runnable) {
    if (runnable instanceof CheckedRunnable)
      recurrent.run((CheckedRunnable) runnable);
    else if (runnable instanceof ContextualRunnable)
      recurrent.run((ContextualRunnable) runnable);
  }

  @SuppressWarnings("unchecked")
  private <T> T get(SyncRecurrent recurrent, Object callable) {
    if (callable instanceof Callable)
      return recurrent.get((Callable<T>) callable);
    else
      return recurrent.get((ContextualCallable<T>) callable);
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
