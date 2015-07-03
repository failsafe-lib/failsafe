package net.jodah.recurrent;

import static net.jodah.recurrent.Asserts.assertThrows;
import static net.jodah.recurrent.Asserts.getThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.net.SocketException;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test
public class RecurrentTest {
  private ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
  private RetryPolicy retryTwice = new RetryPolicy().withMaxRetries(2);
  private Service service = mock(Service.class);

  public interface Service {
    boolean connect();
  }

  @BeforeMethod
  protected void beforeMethod() {
    reset(service);
  }

  @SuppressWarnings("unchecked")
  private <T> Class<? extends Exception>[] failures(int numFailures, Class<? extends Exception> failureType) {
    Class<? extends Exception>[] failures = new Class[numFailures];
    for (int i = 0; i < numFailures; i++)
      failures[i] = failureType;
    return failures;
  }

  private void assertRunWithExecutor(Object runnable) throws Throwable {
    // Fail twice then succeed
    when(service.connect()).thenThrow(failures(2, SocketException.class)).thenReturn(true);
    if (runnable instanceof Runnable)
      Recurrent.run((Runnable) runnable, new RetryPolicy(), executor).get();
    else
      Recurrent.run((ContextualRunnable) runnable, new RetryPolicy(), executor).get();
    verify(service, times(3)).connect();

    // Fail three times
    reset(service);
    when(service.connect()).thenThrow(failures(10, SocketException.class)).thenReturn(true);
    if (runnable instanceof Runnable)
      assertThrows(SocketException.class, () -> Recurrent.run((Runnable) runnable, retryTwice, executor).get());
    else
      assertThrows(SocketException.class,
          () -> Recurrent.run((ContextualRunnable) runnable, retryTwice, executor).get());
    verify(service, times(3)).connect();
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
    assertThrows(SocketException.class, () -> Recurrent.run(runnable, retryTwice));
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
    assertThrows(SocketException.class, () -> Recurrent.get(callable, retryTwice));
    verify(service, times(3)).connect();
  }

  @SuppressWarnings({ "rawtypes", "unchecked" })
  private void assertGetWithExecutor(Object callable) throws Throwable {
    // Fail twice then succeed
    when(service.connect()).thenThrow(failures(2, SocketException.class)).thenReturn(true);
    boolean result = callable instanceof Callable
        ? Recurrent.get((Callable<Boolean>) callable, new RetryPolicy(), executor).get()
        : Recurrent.get((ContextualCallable<Boolean>) callable, new RetryPolicy(), executor).get();
    assertEquals(result, true);
    verify(service, times(3)).connect();

    // Fail three times
    reset(service);
    when(service.connect()).thenThrow(failures(10, SocketException.class)).thenReturn(true);
    Throwable failure = callable instanceof Callable
        ? getThrowable(() -> Recurrent.get((Callable) callable, retryTwice, executor).get())
        : getThrowable(() -> Recurrent.get((ContextualCallable) callable, retryTwice, executor).get());
    assertTrue(SocketException.class.isAssignableFrom(failure.getClass()));
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
    // Fail twice then succeed
    when(service.connect()).thenThrow(failures(2, SocketException.class)).thenReturn(true);
    boolean result = (boolean) (callable instanceof Callable
        ? Recurrent.future((Callable) callable, new RetryPolicy(), executor).get()
        : Recurrent.future((ContextualCallable) callable, new RetryPolicy(), executor).get());
    assertEquals(result, true);
    verify(service, times(3)).connect();

    // Fail three times
    reset(service);
    when(service.connect()).thenThrow(failures(10, SocketException.class)).thenReturn(true);
    Throwable failure = callable instanceof Callable
        ? getThrowable(() -> Recurrent.future((Callable) callable, retryTwice, executor).get())
        : getThrowable(() -> Recurrent.future((ContextualCallable) callable, retryTwice, executor).get());
    assertTrue(SocketException.class.isAssignableFrom(failure.getClass()));
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
        if (ctx.getRetryCount() % 2 == 0)
          throw failure;
        if (!ctx.retry(failure))
          throw failure;
        return null;
      }
    }));
  }
}
