package net.jodah.failsafe;

import static net.jodah.failsafe.Testing.failures;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import net.jodah.concurrentunit.Waiter;

@Test
public class AsyncListenerBindingsTest {
  Service service = mock(Service.class);
  Waiter waiter;
  ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);

  AtomicInteger asyncFailedAttempt;
  AtomicInteger asyncFailedAttemptResult;
  AtomicInteger asyncCtxFailedAttempt;

  AtomicInteger asyncRetry;
  AtomicInteger asyncRetryResult;
  AtomicInteger asyncCtxRetry;

  AtomicInteger asyncComplete;
  AtomicInteger asyncCtxComplete;

  AtomicInteger asyncFailure;
  AtomicInteger asyncFailureResult;
  AtomicInteger asyncCtxFailure;

  AtomicInteger asyncSuccess;
  AtomicInteger asyncCtxSuccess;

  public interface Service {
    boolean connect();
  }

  @BeforeMethod
  void beforeMethod() {
    reset(service);
    waiter = new Waiter();

    asyncFailedAttempt = new AtomicInteger();
    asyncFailedAttemptResult = new AtomicInteger();
    asyncCtxFailedAttempt = new AtomicInteger();

    asyncRetry = new AtomicInteger();
    asyncRetryResult = new AtomicInteger();
    asyncCtxRetry = new AtomicInteger();

    asyncComplete = new AtomicInteger();
    asyncCtxComplete = new AtomicInteger();

    asyncFailure = new AtomicInteger();
    asyncFailureResult = new AtomicInteger();
    asyncCtxFailure = new AtomicInteger();

    asyncSuccess = new AtomicInteger();
    asyncCtxSuccess = new AtomicInteger();
  }

  <T> AsyncFailsafe<T> bindListeners(AsyncFailsafe<T> failsafe) {
    failsafe.onFailedAttemptAsync(e -> asyncFailedAttempt.incrementAndGet());
    failsafe.onFailedAttemptAsync((r, e) -> asyncFailedAttemptResult.incrementAndGet());
    failsafe.onFailedAttemptAsync(
        (r, e, c) -> waiter.assertEquals(asyncCtxFailedAttempt.incrementAndGet(), c.getExecutions()));

    failsafe.onRetryAsync(e -> asyncRetry.incrementAndGet());
    failsafe.onRetryAsync((r, e) -> asyncRetryResult.incrementAndGet());
    failsafe.onRetryAsync((r, e, c) -> waiter.assertEquals(asyncCtxRetry.incrementAndGet(), c.getExecutions()));

    failsafe.onCompleteAsync((e, r) -> {
      asyncComplete.incrementAndGet();
      waiter.resume();
    });
    failsafe.onCompleteAsync((e, r, c) -> {
      asyncCtxComplete.incrementAndGet();
      waiter.resume();
    });

    failsafe.onFailureAsync(e -> asyncFailure.incrementAndGet());
    failsafe.onFailureAsync((r, e) -> asyncFailureResult.incrementAndGet());
    failsafe.onFailureAsync((r, e, c) -> asyncCtxFailure.incrementAndGet());

    failsafe.onSuccessAsync(r -> asyncSuccess.incrementAndGet());
    failsafe.onSuccessAsync((r, c) -> asyncCtxSuccess.incrementAndGet());

    return failsafe;
  }

  /**
   * Asserts that listeners are called the expected number of times for a successful completion.
   */
  public void testListenersForSuccessfulCompletion() throws Throwable {
    Callable<Boolean> callable = () -> service.connect();

    // Given - Fail twice then succeed
    when(service.connect()).thenThrow(failures(2, new IllegalStateException())).thenReturn(false, false, true);

    // When
    bindListeners(Failsafe.with(new RetryPolicy().retryWhen(false)).with(executor)).get(callable);
    waiter.await(1000, 2);

    // Then
    assertEquals(asyncFailedAttempt.get(), 4);
    assertEquals(asyncFailedAttemptResult.get(), 4);
    assertEquals(asyncCtxFailedAttempt.get(), 4);

    assertEquals(asyncRetry.get(), 4);
    assertEquals(asyncRetryResult.get(), 4);
    assertEquals(asyncCtxRetry.get(), 4);

    assertEquals(asyncComplete.get(), 1);
    assertEquals(asyncCtxComplete.get(), 1);

    assertEquals(asyncFailure.get(), 0);
    assertEquals(asyncFailureResult.get(), 0);
    assertEquals(asyncCtxFailure.get(), 0);

    assertEquals(asyncSuccess.get(), 1);
    assertEquals(asyncCtxSuccess.get(), 1);
  }

  /**
   * Asserts that listeners are called the expected number of times for a failure completion.
   */
  public void testListenersForFailureCompletion() throws Throwable {
    Callable<Boolean> callable = () -> service.connect();

    // Given - Fail twice then succeed
    when(service.connect()).thenThrow(failures(2, new IllegalStateException())).thenReturn(false, false, true);

    // When
    bindListeners(Failsafe.with(new RetryPolicy().retryWhen(false).withMaxRetries(3)).with(executor)).get(callable);
    waiter.await(1000, 2);

    // Then
    assertEquals(asyncFailedAttempt.get(), 4);
    assertEquals(asyncFailedAttemptResult.get(), 4);
    assertEquals(asyncCtxFailedAttempt.get(), 4);

    assertEquals(asyncRetry.get(), 3);
    assertEquals(asyncRetryResult.get(), 3);
    assertEquals(asyncCtxRetry.get(), 3);

    assertEquals(asyncComplete.get(), 1);
    assertEquals(asyncCtxComplete.get(), 1);

    assertEquals(asyncFailure.get(), 1);
    assertEquals(asyncFailureResult.get(), 1);
    assertEquals(asyncCtxFailure.get(), 1);

    assertEquals(asyncSuccess.get(), 0);
    assertEquals(asyncCtxSuccess.get(), 0);
  }
}
