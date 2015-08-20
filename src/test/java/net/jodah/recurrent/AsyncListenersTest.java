package net.jodah.recurrent;

import static net.jodah.recurrent.Testing.failures;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import java.net.SocketException;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import net.jodah.concurrentunit.Waiter;

@Test
public class AsyncListenersTest {
  Service service = mock(Service.class);
  Waiter waiter;
  AsyncListeners<Boolean> listeners = new AsyncListeners<Boolean>();
  ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
  AtomicInteger failedAttemptAsync;
  AtomicInteger failedAttemptStatsAsync;
  AtomicInteger retryAsync;
  AtomicInteger retryStatsAsync;

  public interface Service {
    boolean connect();
  }

  @BeforeClass
  void beforeClass() {
    listeners.whenFailedAttemptAsync((r, f) -> {
      failedAttemptAsync.incrementAndGet();
      waiter.resume();
    });
    listeners.whenFailedAttemptAsync((c, r, f) -> {
      failedAttemptStatsAsync.incrementAndGet();
      waiter.resume();
    });
    listeners.whenRetryAsync((r, f) -> {
      retryAsync.incrementAndGet();
      waiter.resume();
    });
    listeners.whenRetryAsync((c, r, f) -> {
      retryStatsAsync.incrementAndGet();
      waiter.resume();
    });
  }

  @BeforeMethod
  void beforeMethod() {
    reset(service);
    waiter = new Waiter();
    failedAttemptAsync = new AtomicInteger();
    failedAttemptStatsAsync = new AtomicInteger();
    retryAsync = new AtomicInteger();
    retryStatsAsync = new AtomicInteger();
  }

  /**
   * Asserts that listeners are called the expected number of times for a successful completion.
   */
  public void testListenersForSuccessfulCompletion() throws Throwable {
    Callable<Boolean> callable = () -> service.connect();

    // Given - Fail twice then succeed
    when(service.connect()).thenThrow(failures(2, SocketException.class)).thenReturn(false, false, true);

    // When
    Recurrent.get(callable, new RetryPolicy().retryFor(false), executor, listeners);
    waiter.await(1000, 16);

    // Then
    assertEquals(failedAttemptAsync.get(), 4);
    assertEquals(failedAttemptStatsAsync.get(), 4);
    assertEquals(retryAsync.get(), 4);
    assertEquals(retryStatsAsync.get(), 4);
  }

  /**
   * Asserts that listeners are called the expected number of times for a failure completion.
   */
  public void testListenersForFailureCompletion() throws Throwable {
    Callable<Boolean> callable = () -> service.connect();

    // Given - Fail twice then succeed
    when(service.connect()).thenThrow(failures(2, SocketException.class)).thenReturn(false, false, true);

    // When
    Recurrent.get(callable, new RetryPolicy().retryFor(false).withMaxRetries(3), executor, listeners);
    waiter.await(1000, 14);

    // Then
    assertEquals(failedAttemptAsync.get(), 4);
    assertEquals(failedAttemptStatsAsync.get(), 4);
    assertEquals(retryAsync.get(), 3);
    assertEquals(retryStatsAsync.get(), 3);
  }
}
