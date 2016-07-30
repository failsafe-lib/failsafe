package net.jodah.failsafe;

import static net.jodah.failsafe.Testing.failures;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import net.jodah.failsafe.FailsafeConfigTest.ListenerCounter;

@Test
public class AsyncFailsafeConfigTest {
  Service service = mock(Service.class);
  ScheduledExecutorService executor;

  ListenerCounter abort;
  ListenerCounter complete;
  ListenerCounter failedAttempt;
  ListenerCounter failure;
  ListenerCounter retriesExceeded;
  ListenerCounter retry;
  ListenerCounter success;

  public interface Service {
    boolean connect();
  }

  @BeforeMethod
  void beforeMethod() {
    executor = Executors.newScheduledThreadPool(2);
    reset(service);

    abort = new ListenerCounter();
    complete = new ListenerCounter();
    failedAttempt = new ListenerCounter();
    failure = new ListenerCounter();
    retriesExceeded = new ListenerCounter();
    retry = new ListenerCounter();
    success = new ListenerCounter();
  }

  @AfterMethod
  void afterMethod() throws Throwable {
    executor.shutdownNow();
    executor.awaitTermination(5, TimeUnit.SECONDS);
  }

  <T> AsyncFailsafe<T> registerListeners(AsyncFailsafe<T> failsafe) {
    failsafe.onAbortAsync(e -> abort.async(1));
    failsafe.onAbortAsync((r, e) -> abort.async(2));
    failsafe.onAbortAsync((r, e, c) -> abort.async(3));
    abort.asyncListeners = 3;

    failsafe.onCompleteAsync((e, r) -> complete.async(1));
    failsafe.onCompleteAsync((e, r, c) -> complete.async(2));
    complete.asyncListeners = 2;

    failsafe.onFailedAttemptAsync(e -> failedAttempt.async(1));
    failsafe.onFailedAttemptAsync((r, f) -> failedAttempt.async(2));
    failsafe.onFailedAttemptAsync((r, f, c) -> failedAttempt.async(3, c));
    failedAttempt.asyncListeners = 3;

    failsafe.onFailureAsync(e -> failure.async(1));
    failsafe.onFailureAsync((r, e) -> failure.async(2));
    failsafe.onFailureAsync((r, e, c) -> failure.async(3));
    failure.asyncListeners = 3;

    failsafe.onRetriesExceededAsync(e -> retriesExceeded.async(1));
    failsafe.onRetriesExceededAsync((r, f) -> retriesExceeded.async(2));
    retriesExceeded.asyncListeners = 2;

    failsafe.onRetryAsync(e -> retry.async(1));
    failsafe.onRetryAsync((r, f) -> retry.async(2));
    failsafe.onRetryAsync((r, f, c) -> retry.async(3, c));
    retry.asyncListeners = 3;

    failsafe.onSuccessAsync(r -> success.async(1));
    failsafe.onSuccessAsync((r, c) -> success.async(2));
    success.asyncListeners = 2;

    return failsafe;
  }

  /**
   * Asserts that listeners are called the expected number of times for a successful completion.
   */
  public void testListenersForSuccess() throws Throwable {
    Callable<Boolean> callable = () -> service.connect();

    // Given - Fail twice then succeed
    when(service.connect()).thenThrow(failures(2, new IllegalStateException())).thenReturn(false, false, true);
    RetryPolicy retryPolicy = new RetryPolicy().retryWhen(false);

    // When
    registerListeners(Failsafe.with(retryPolicy).with(executor)).get(callable);

    // Then
    abort.assertEquals(0);
    complete.assertEquals(1);
    failedAttempt.assertEquals(4);
    failure.assertEquals(0);
    retriesExceeded.assertEquals(0);
    retry.assertEquals(4);
    success.assertEquals(1);
  }

  /**
   * Asserts that listeners are called the expected number of times for an unhandled failure.
   */
  @SuppressWarnings("unchecked")
  public void testListenersForUnhandledFailure() throws Throwable {
    Callable<Boolean> callable = () -> service.connect();

    // Given - Fail 2 times then don't match policy
    when(service.connect()).thenThrow(failures(2, new IllegalStateException()))
        .thenThrow(IllegalArgumentException.class);
    RetryPolicy retryPolicy = new RetryPolicy().retryOn(IllegalStateException.class).withMaxRetries(10);

    // When
    registerListeners(Failsafe.with(retryPolicy).with(executor)).get(callable);

    // Then
    abort.assertEquals(0);
    complete.assertEquals(1);
    failedAttempt.assertEquals(3);
    failure.assertEquals(1);
    retriesExceeded.assertEquals(0);
    retry.assertEquals(2);
    success.assertEquals(0);
  }

  /**
   * Asserts that listeners are called the expected number of times when retries are exceeded.
   */
  public void testListenersForRetriesExceeded() throws Throwable {
    Callable<Boolean> callable = () -> service.connect();

    // Given - Fail 4 times and exceed retries
    when(service.connect()).thenThrow(failures(10, new IllegalStateException()));
    RetryPolicy retryPolicy = new RetryPolicy().retryWhen(false).withMaxRetries(3);

    // When
    registerListeners(Failsafe.with(retryPolicy).with(executor)).get(callable);

    // Then
    abort.assertEquals(0);
    complete.assertEquals(1);
    failedAttempt.assertEquals(4);
    failure.assertEquals(1);
    retriesExceeded.assertEquals(1);
    retry.assertEquals(3);
    success.assertEquals(0);
  }

  /**
   * Asserts that listeners are called the expected number of times for an aborted execution.
   */
  @SuppressWarnings("unchecked")
  public void testListenersForAbort() throws Throwable {
    Callable<Boolean> callable = () -> service.connect();

    // Given - Fail twice then abort
    when(service.connect()).thenThrow(failures(3, new IllegalStateException()))
        .thenThrow(new IllegalArgumentException());
    RetryPolicy retryPolicy = new RetryPolicy().abortOn(IllegalArgumentException.class).withMaxRetries(3);

    // When
    Asserts.assertThrows(() -> registerListeners(Failsafe.with(retryPolicy).with(executor)).get(callable).get(),
        ExecutionException.class, IllegalArgumentException.class);

    // Then
    abort.assertEquals(1);
    complete.assertEquals(0);
    failedAttempt.assertEquals(4);
    failure.assertEquals(0);
    retriesExceeded.assertEquals(0);
    retry.assertEquals(3);
    success.assertEquals(0);
  }
}
