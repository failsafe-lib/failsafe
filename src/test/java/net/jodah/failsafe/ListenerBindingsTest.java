package net.jodah.failsafe;

import static net.jodah.failsafe.Testing.failures;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import net.jodah.concurrentunit.Waiter;

@Test
public class ListenerBindingsTest {
  private Service service = mock(Service.class);
  ExecutorService executor = Executors.newFixedThreadPool(2);
  Waiter waiter;

  AtomicInteger failedAttempt;
  AtomicInteger failedAttemptResult;
  AtomicInteger ctxFailedAttempt;
  AtomicInteger asyncFailedAttempt;
  AtomicInteger asyncFailedAttemptResult;
  AtomicInteger asyncCtxFailedAttempt;

  AtomicInteger retry;
  AtomicInteger retryResult;
  AtomicInteger ctxRetry;
  AtomicInteger asyncRetry;
  AtomicInteger asyncRetryResult;
  AtomicInteger asyncCtxRetry;

  AtomicInteger complete;
  AtomicInteger ctxComplete;
  AtomicInteger asyncComplete;
  AtomicInteger asyncCtxComplete;

  AtomicInteger abort;
  AtomicInteger abortResult;
  AtomicInteger ctxAbort;
  AtomicInteger asyncAbort;
  AtomicInteger asyncAbortResult;
  AtomicInteger asyncCtxAbort;

  AtomicInteger failure;
  AtomicInteger failureResult;
  AtomicInteger ctxFailure;
  AtomicInteger asyncFailure;
  AtomicInteger asyncFailureResult;
  AtomicInteger asyncCtxFailure;

  AtomicInteger success;
  AtomicInteger ctxSuccess;
  AtomicInteger asyncSuccess;
  AtomicInteger asyncCtxSuccess;

  public interface Service {
    boolean connect();
  }

  @BeforeMethod
  void beforeMethod() {
    reset(service);
    waiter = new Waiter();

    failedAttempt = new AtomicInteger();
    failedAttemptResult = new AtomicInteger();
    ctxFailedAttempt = new AtomicInteger();
    asyncFailedAttempt = new AtomicInteger();
    asyncFailedAttemptResult = new AtomicInteger();
    asyncCtxFailedAttempt = new AtomicInteger();

    retry = new AtomicInteger();
    retryResult = new AtomicInteger();
    ctxRetry = new AtomicInteger();
    asyncRetry = new AtomicInteger();
    asyncRetryResult = new AtomicInteger();
    asyncCtxRetry = new AtomicInteger();

    complete = new AtomicInteger();
    ctxComplete = new AtomicInteger();
    asyncComplete = new AtomicInteger();
    asyncCtxComplete = new AtomicInteger();

    abort = new AtomicInteger();
    abortResult = new AtomicInteger();
    ctxAbort = new AtomicInteger();
    asyncAbort = new AtomicInteger();
    asyncAbortResult = new AtomicInteger();
    asyncCtxAbort = new AtomicInteger();

    failure = new AtomicInteger();
    failureResult = new AtomicInteger();
    ctxFailure = new AtomicInteger();
    asyncFailure = new AtomicInteger();
    asyncFailureResult = new AtomicInteger();
    asyncCtxFailure = new AtomicInteger();

    success = new AtomicInteger();
    ctxSuccess = new AtomicInteger();
    asyncSuccess = new AtomicInteger();
    asyncCtxSuccess = new AtomicInteger();
  }

  <T> SyncFailsafe<T> bindListeners(SyncFailsafe<T> failsafe) {
    failsafe.onFailedAttempt(e -> failedAttempt.incrementAndGet());
    failsafe.onFailedAttempt((r, f) -> failedAttemptResult.incrementAndGet());
    failsafe.onFailedAttempt((r, f, s) -> assertEquals(ctxFailedAttempt.incrementAndGet(), s.getExecutions()));
    failsafe.onFailedAttemptAsync(e -> {
      asyncFailedAttempt.incrementAndGet();
      waiter.resume();
    } , executor);
    failsafe.onFailedAttemptAsync((r, f) -> {
      asyncFailedAttemptResult.incrementAndGet();
      waiter.resume();
    } , executor);
    failsafe.onFailedAttemptAsync((r, f, s) -> {
      waiter.assertEquals(asyncCtxFailedAttempt.incrementAndGet(), s.getExecutions());
      waiter.resume();
    } , executor);

    failsafe.onRetry(e -> retry.incrementAndGet());
    failsafe.onRetry((r, f) -> retryResult.incrementAndGet());
    failsafe.onRetry((r, f, s) -> assertEquals(ctxRetry.incrementAndGet(), s.getExecutions()));
    failsafe.onRetryAsync(e -> {
      asyncRetry.incrementAndGet();
      waiter.resume();
    } , executor);
    failsafe.onRetryAsync((r, f) -> {
      asyncRetryResult.incrementAndGet();
      waiter.resume();
    } , executor);
    failsafe.onRetryAsync((r, f, s) -> {
      waiter.assertEquals(asyncCtxRetry.incrementAndGet(), s.getExecutions());
      waiter.resume();
    } , executor);

    failsafe.onComplete((r, f) -> complete.incrementAndGet());
    failsafe.onComplete((r, f, s) -> ctxComplete.incrementAndGet());
    failsafe.onCompleteAsync((r, f) -> {
      asyncComplete.incrementAndGet();
      waiter.resume();
    } , executor);
    failsafe.onCompleteAsync((r, f, s) -> {
      asyncCtxComplete.incrementAndGet();
      waiter.resume();
    } , executor);

    failsafe.onAbort(e -> abort.incrementAndGet());
    failsafe.onAbort((r, e) -> abortResult.incrementAndGet());
    failsafe.onAbort((r, e, c) -> ctxAbort.incrementAndGet());
    failsafe.onAbortAsync(e -> asyncAbort.incrementAndGet(), executor);
    failsafe.onAbortAsync((r, e) -> asyncAbortResult.incrementAndGet(), executor);
    failsafe.onAbortAsync((r, e, c) -> asyncCtxAbort.incrementAndGet(), executor);

    failsafe.onFailure(e -> failure.incrementAndGet());
    failsafe.onFailure((r, f) -> failureResult.incrementAndGet());
    failsafe.onFailure((r, f, s) -> ctxFailure.incrementAndGet());
    failsafe.onFailureAsync(e -> asyncFailure.incrementAndGet(), executor);
    failsafe.onFailureAsync((r, f) -> asyncFailureResult.incrementAndGet(), executor);
    failsafe.onFailureAsync((r, f, s) -> asyncCtxFailure.incrementAndGet(), executor);

    failsafe.onSuccess((r) -> success.incrementAndGet());
    failsafe.onSuccess((r, s) -> ctxSuccess.incrementAndGet());
    failsafe.onSuccessAsync((r) -> asyncSuccess.incrementAndGet(), executor);
    failsafe.onSuccessAsync((r, s) -> asyncCtxSuccess.incrementAndGet(), executor);

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
    bindListeners(Failsafe.with(new RetryPolicy().retryWhen(false))).get(callable);
    waiter.await(1000, 12 + 12 + 2);

    // Then
    assertEquals(failedAttempt.get(), 4);
    assertEquals(failedAttemptResult.get(), 4);
    assertEquals(ctxFailedAttempt.get(), 4);
    assertEquals(asyncFailedAttempt.get(), 4);
    assertEquals(asyncFailedAttemptResult.get(), 4);
    assertEquals(asyncCtxFailedAttempt.get(), 4);

    assertEquals(retry.get(), 4);
    assertEquals(retryResult.get(), 4);
    assertEquals(ctxRetry.get(), 4);
    assertEquals(asyncRetry.get(), 4);
    assertEquals(asyncRetryResult.get(), 4);
    assertEquals(asyncCtxRetry.get(), 4);

    assertEquals(complete.get(), 1);
    assertEquals(ctxComplete.get(), 1);
    assertEquals(asyncComplete.get(), 1);
    assertEquals(asyncCtxComplete.get(), 1);

    assertEquals(abort.get(), 0);
    assertEquals(abortResult.get(), 0);
    assertEquals(ctxAbort.get(), 0);
    assertEquals(asyncAbort.get(), 0);
    assertEquals(asyncAbortResult.get(), 0);
    assertEquals(asyncCtxAbort.get(), 0);

    assertEquals(failure.get(), 0);
    assertEquals(failureResult.get(), 0);
    assertEquals(ctxFailure.get(), 0);
    assertEquals(asyncFailure.get(), 0);
    assertEquals(asyncFailureResult.get(), 0);
    assertEquals(asyncCtxFailure.get(), 0);

    assertEquals(success.get(), 1);
    assertEquals(ctxSuccess.get(), 1);
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
    bindListeners(Failsafe.with(new RetryPolicy().retryWhen(false).withMaxRetries(3))).get(callable);
    waiter.await(1000, 12 + 9 + 2);

    // Then
    assertEquals(failedAttempt.get(), 4);
    assertEquals(failedAttemptResult.get(), 4);
    assertEquals(ctxFailedAttempt.get(), 4);
    assertEquals(asyncFailedAttempt.get(), 4);
    assertEquals(asyncFailedAttemptResult.get(), 4);
    assertEquals(asyncCtxFailedAttempt.get(), 4);

    assertEquals(retry.get(), 3);
    assertEquals(retryResult.get(), 3);
    assertEquals(ctxRetry.get(), 3);
    assertEquals(asyncRetry.get(), 3);
    assertEquals(asyncRetryResult.get(), 3);
    assertEquals(asyncCtxRetry.get(), 3);

    assertEquals(complete.get(), 1);
    assertEquals(ctxComplete.get(), 1);
    assertEquals(asyncComplete.get(), 1);
    assertEquals(asyncCtxComplete.get(), 1);

    assertEquals(abort.get(), 0);
    assertEquals(abortResult.get(), 0);
    assertEquals(ctxAbort.get(), 0);
    assertEquals(asyncAbort.get(), 0);
    assertEquals(asyncAbortResult.get(), 0);
    assertEquals(asyncCtxAbort.get(), 0);

    assertEquals(failure.get(), 1);
    assertEquals(failureResult.get(), 1);
    assertEquals(ctxFailure.get(), 1);
    assertEquals(asyncFailure.get(), 1);
    assertEquals(asyncFailureResult.get(), 1);
    assertEquals(asyncCtxFailure.get(), 1);

    assertEquals(success.get(), 0);
    assertEquals(ctxSuccess.get(), 0);
    assertEquals(asyncSuccess.get(), 0);
    assertEquals(asyncCtxSuccess.get(), 0);
  }

  /**
   * Asserts that listeners are called the expected number of times for an aborted execution.
   */
  @SuppressWarnings("unchecked")
  public void testListenersForAbort() throws Throwable {
    Callable<Boolean> callable = () -> service.connect();

    // Given - Fail twice then succeed
    when(service.connect()).thenThrow(failures(2, new IllegalStateException()))
        .thenThrow(IllegalArgumentException.class);

    // When
    Asserts.assertThrows(
        () -> bindListeners(Failsafe.with(new RetryPolicy().abortOn(IllegalArgumentException.class).withMaxRetries(3)))
            .get(callable),
        FailsafeException.class, IllegalArgumentException.class);
    waiter.await(1000, 9 + 6 + 2);

    // Then
    assertEquals(failedAttempt.get(), 3);
    assertEquals(failedAttemptResult.get(), 3);
    assertEquals(ctxFailedAttempt.get(), 3);
    assertEquals(asyncFailedAttempt.get(), 3);
    assertEquals(asyncFailedAttemptResult.get(), 3);
    assertEquals(asyncCtxFailedAttempt.get(), 3);

    assertEquals(retry.get(), 2);
    assertEquals(retryResult.get(), 2);
    assertEquals(ctxRetry.get(), 2);
    assertEquals(asyncRetry.get(), 2);
    assertEquals(asyncRetryResult.get(), 2);
    assertEquals(asyncCtxRetry.get(), 2);

    assertEquals(complete.get(), 1);
    assertEquals(ctxComplete.get(), 1);
    assertEquals(asyncComplete.get(), 1);
    assertEquals(asyncCtxComplete.get(), 1);

    assertEquals(abort.get(), 1);
    assertEquals(abortResult.get(), 1);
    assertEquals(ctxAbort.get(), 1);
    assertEquals(asyncAbort.get(), 1);
    assertEquals(asyncAbortResult.get(), 1);
    assertEquals(asyncCtxAbort.get(), 1);

    assertEquals(failure.get(), 1);
    assertEquals(failureResult.get(), 1);
    assertEquals(ctxFailure.get(), 1);
    assertEquals(asyncFailure.get(), 1);
    assertEquals(asyncFailureResult.get(), 1);
    assertEquals(asyncCtxFailure.get(), 1);

    assertEquals(success.get(), 0);
    assertEquals(ctxSuccess.get(), 0);
    assertEquals(asyncSuccess.get(), 0);
    assertEquals(asyncCtxSuccess.get(), 0);
  }

  /**
   * Asserts that a failure listener is called on an abort.
   */
  @SuppressWarnings("unchecked")
  public void testFailureListenerCalledOnAbort() {
    // Given
    RetryPolicy retryPolicy = new RetryPolicy().abortOn(IllegalArgumentException.class);
    AtomicBoolean called = new AtomicBoolean();

    // When
    try {
      Failsafe.with(retryPolicy).onFailure(e -> {
        called.set(true);
      }).run(() -> {
        throw new IllegalArgumentException();
      });

      fail("Expected exception");
    } catch (Exception expected) {
    }

    assertTrue(called.get());
  }
}
