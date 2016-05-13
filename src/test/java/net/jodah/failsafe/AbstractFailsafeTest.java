package net.jodah.failsafe;

import static net.jodah.failsafe.Asserts.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.Test;

import net.jodah.concurrentunit.Waiter;
import net.jodah.failsafe.function.CheckedRunnable;

@Test
public abstract class AbstractFailsafeTest {
  RetryPolicy retryAlways = new RetryPolicy();
  RetryPolicy retryNever = new RetryPolicy().withMaxRetries(0);
  RetryPolicy retryTwice = new RetryPolicy().withMaxRetries(2);
  Service service = mock(Service.class);
  AtomicInteger counter;

  public static class ConnectException extends RuntimeException {
  }

  public interface Service {
    boolean connect();

    boolean disconnect();
  }

  public interface FastService extends Service {
  }

  abstract ScheduledExecutorService getExecutor();

  /**
   * Does a failsafe get with an optional executor.
   */
  <T> T failsafeGet(RetryPolicy retryPolicy, Callable<T> callable) throws ExecutionException, InterruptedException {
    ScheduledExecutorService executor = getExecutor();
    return executor == null ? (T) Failsafe.with(retryPolicy).get(callable)
        : (T) Failsafe.with(retryPolicy).with(executor).get(callable).get();
  }

  /**
   * Does a failsafe run with an optional executor.
   */
  void failsafeRun(CircuitBreaker breaker, CheckedRunnable runnable) throws ExecutionException, InterruptedException {
    ScheduledExecutorService executor = getExecutor();
    if (executor == null)
      Failsafe.with(breaker).run(runnable);
    else
      Failsafe.with(breaker).with(executor).run(runnable);
  }

  /**
   * Asserts that retries are not attempted after a successful execution.
   */
  public void shouldSucceedWithoutRetries() throws Throwable {
    // Given retries not allowed
    reset(service);
    when(service.connect()).thenReturn(false);

    // When / Then
    assertEquals(failsafeGet(retryNever, service::connect), Boolean.FALSE);
    verify(service).connect();
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
    assertThrows(() -> failsafeGet(retryPolicy, service::connect), ExecutionException.class,
        IllegalStateException.class);
    verify(service, times(3)).connect();
  }

  /**
   * Should throw CircuitBreakerOpenException when max half-open executions are occurring.
   */
  public void shouldRejectExcessiveExecutionsThroughHalfOpenCircuit() throws Throwable {
    // Given
    CircuitBreaker breaker = new CircuitBreaker().withSuccessThreshold(3);
    breaker.halfOpen();
    Waiter waiter = new Waiter();
    for (int i = 0; i < 3; i++)
      Testing.runInThread(() -> failsafeRun(breaker, () -> {
        waiter.resume();
        Thread.sleep(1000);
      }));

    // When / Then
    waiter.await(10000, 3);
    for (int i = 0; i < 5; i++)
      assertThrows(() -> failsafeRun(breaker, Testing::noop), CircuitBreakerOpenException.class);
  }
}
