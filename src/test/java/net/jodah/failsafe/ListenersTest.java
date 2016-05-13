package net.jodah.failsafe;

import static net.jodah.failsafe.Testing.failures;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test
public class ListenersTest {
  private Service service = mock(Service.class);
  private Listeners<Boolean> listeners;
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

  public interface Service {
    boolean connect();
  }

  @BeforeMethod
  void beforeMethod() {
    reset(service);
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

    listeners = new Listeners<Boolean>();
    listeners.onComplete((r, f, s) -> completeStats.incrementAndGet());
    listeners.onComplete((r, f) -> complete.incrementAndGet());
    listeners.onFailedAttempt((r, f, s) -> assertEquals(failedAttemptStats.incrementAndGet(), s.getExecutions()));
    listeners.onFailedAttempt((r, f) -> failedAttempt.incrementAndGet());
    listeners.onFailure((r, f, s) -> failureStats.incrementAndGet());
    listeners.onFailure((r, f) -> failure.incrementAndGet());
    listeners.onRetry((r, f, s) -> assertEquals(retryStats.incrementAndGet(), s.getExecutions()));
    listeners.onRetry((r, f) -> retry.incrementAndGet());
    listeners.onSuccess((r, s) -> successStats.incrementAndGet());
    listeners.onSuccess((r) -> success.incrementAndGet());
  }

  /**
   * Asserts that listeners are called the expected number of times for a successful completion.
   */
  public void testListenersForSuccessfulCompletion() {
    Callable<Boolean> callable = () -> service.connect();

    // Given - Fail twice then succeed
    when(service.connect()).thenThrow(failures(2, new IllegalStateException())).thenReturn(false, false, true);

    // When
    Failsafe.with(new RetryPolicy().retryWhen(false)).with(listeners).get(callable);

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
  }

  /**
   * Asserts that listeners are called the expected number of times for a failure completion.
   */
  public void testListenersForFailureCompletion() {
    Callable<Boolean> callable = () -> service.connect();

    // Given - Fail twice then succeed
    when(service.connect()).thenThrow(failures(2, new IllegalStateException())).thenReturn(false, false, true);

    // When
    Failsafe.with(new RetryPolicy().retryWhen(false).withMaxRetries(3)).with(listeners).get(callable);

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
  }
}
