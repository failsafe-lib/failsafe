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

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import net.jodah.concurrentunit.Waiter;

@Test
public class AsyncListenersTest {
  Service service = mock(Service.class);
  Waiter waiter;
  AsyncListeners<Boolean> listeners;
  ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
  AtomicInteger complete;
  AtomicInteger completeStats;
  AtomicInteger failedAttempt;
  AtomicInteger failedAttemptStats;
  AtomicInteger failure;
  AtomicInteger failureStats;
  AtomicInteger retry;
  AtomicInteger retryStats;
  AtomicInteger success;
  AtomicInteger successStats;
  AtomicInteger failedAttemptAsync;
  AtomicInteger failedAttemptStatsAsync;
  AtomicInteger retryAsync;
  AtomicInteger retryStatsAsync;

  public interface Service {
    boolean connect();
  }

  @BeforeMethod
  void beforeMethod() {
    reset(service);
    waiter = new Waiter();
    complete = new AtomicInteger();
    completeStats = new AtomicInteger();
    failedAttempt = new AtomicInteger();
    failedAttemptStats = new AtomicInteger();
    failure = new AtomicInteger();
    failureStats = new AtomicInteger();
    retry = new AtomicInteger();
    retryStats = new AtomicInteger();
    success = new AtomicInteger();
    successStats = new AtomicInteger();
    failedAttemptAsync = new AtomicInteger();
    failedAttemptStatsAsync = new AtomicInteger();
    retryAsync = new AtomicInteger();
    retryStatsAsync = new AtomicInteger();
    listeners = new AsyncListeners<Boolean>() {
      public void onComplete(Boolean result, Throwable failure, InvocationStats stats) {
        completeStats.incrementAndGet();
        waiter.resume();
      }

      public void onComplete(Boolean result, Throwable failure) {
        complete.incrementAndGet();
        waiter.resume();
      }

      public void onFailedAttempt(Boolean result, Throwable failure, InvocationStats stats) {
        waiter.assertEquals(failedAttemptStats.incrementAndGet(), stats.getAttemptCount());
      }

      public void onFailedAttempt(Boolean result, Throwable failure) {
        failedAttempt.incrementAndGet();
      }

      public void onFailure(Boolean result, Throwable f, InvocationStats stats) {
        failureStats.incrementAndGet();
      }

      public void onFailure(Boolean result, Throwable f) {
        failure.incrementAndGet();
      }

      public void onRetry(Boolean result, Throwable failure, InvocationStats stats) {
        waiter.assertEquals(retryStats.incrementAndGet(), stats.getAttemptCount());
      }

      public void onRetry(Boolean result, Throwable failure) {
        retry.incrementAndGet();
      }

      public void onSuccess(Boolean result, InvocationStats stats) {
        successStats.incrementAndGet();
      }

      public void onSuccess(Boolean result) {
        success.incrementAndGet();
      }
    };
    listeners.whenFailedAttemptAsync((r, f) -> {
      failedAttemptAsync.incrementAndGet();
      waiter.resume();
    });
    listeners.whenFailedAttemptAsync((r, f, s) -> {
      waiter.assertEquals(failedAttemptStatsAsync.incrementAndGet(), s.getAttemptCount());
      waiter.resume();
    });
    listeners.whenRetryAsync((r, f) -> {
      retryAsync.incrementAndGet();
      waiter.resume();
    });
    listeners.whenRetryAsync((r, f, s) -> {
      waiter.assertEquals(retryStatsAsync.incrementAndGet(), s.getAttemptCount());
      waiter.resume();
    });
  }

  /**
   * Asserts that listeners are called the expected number of times for a successful completion.
   */
  public void testListenersForSuccessfulCompletion() throws Throwable {
    Callable<Boolean> callable = () -> service.connect();

    // Given - Fail twice then succeed
    when(service.connect()).thenThrow(failures(2, SocketException.class)).thenReturn(false, false, true);

    // When
    Recurrent.with(new RetryPolicy().retryWhen(false), executor).with(listeners).get(callable);
    waiter.await(1000, 18);

    // Then
    assertEquals(complete.get(), 1);
    assertEquals(completeStats.get(), 1);
    assertEquals(failedAttempt.get(), 4);
    assertEquals(failedAttemptStats.get(), 4);
    assertEquals(failure.get(), 0);
    assertEquals(failureStats.get(), 0);
    assertEquals(retry.get(), 4);
    assertEquals(retryStats.get(), 4);
    assertEquals(success.get(), 1);
    assertEquals(successStats.get(), 1);

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
    Recurrent.with(new RetryPolicy().retryWhen(false).withMaxRetries(3), executor).with(listeners).get(callable);
    waiter.await(1000, 16);

    // Then
    assertEquals(complete.get(), 1);
    assertEquals(completeStats.get(), 1);
    assertEquals(failedAttempt.get(), 4);
    assertEquals(failedAttemptStats.get(), 4);
    assertEquals(failure.get(), 1);
    assertEquals(failureStats.get(), 1);
    assertEquals(retry.get(), 3);
    assertEquals(retryStats.get(), 3);
    assertEquals(success.get(), 0);
    assertEquals(successStats.get(), 0);

    assertEquals(failedAttemptAsync.get(), 4);
    assertEquals(failedAttemptStatsAsync.get(), 4);
    assertEquals(retryAsync.get(), 3);
    assertEquals(retryStatsAsync.get(), 3);
  }
}
