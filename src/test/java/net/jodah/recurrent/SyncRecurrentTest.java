package net.jodah.recurrent;

import static net.jodah.recurrent.Asserts.assertThrows;
import static net.jodah.recurrent.Testing.failures;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.net.ConnectException;
import java.net.SocketException;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test
public class SyncRecurrentTest extends AbstractRecurrentTest {
  // Results from a synchronous Recurrent call
  private @SuppressWarnings("unchecked") Class<? extends Throwable>[] syncThrowables = new Class[] {
      RecurrentException.class, SocketException.class };
  // Results from a get against a future that wraps a synchronous Recurrent call
  private @SuppressWarnings("unchecked") Class<? extends Throwable>[] futureSyncThrowables = new Class[] {
      ExecutionException.class, RecurrentException.class, SocketException.class };

  public interface Service {
    boolean connect();

    boolean disconnect();
  }

  @BeforeMethod
  protected void beforeMethod() {
    reset(service);
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
   * Asserts that retries are not attempted after a successful execution.
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
}
