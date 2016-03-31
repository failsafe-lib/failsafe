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
public class RecurrentFutureTest {
  Service service = mock(Service.class);
  Waiter waiter;
  ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
  AtomicInteger complete;
  AtomicInteger completeStats;
  AtomicInteger completeAsync;
  AtomicInteger completeStatsAsync;
  AtomicInteger failure;
  AtomicInteger failureStats;
  AtomicInteger failureAsync;
  AtomicInteger failureStatsAsync;
  AtomicInteger success;
  AtomicInteger successStats;
  AtomicInteger successAsync;
  AtomicInteger successStatsAsync;

  public interface Service {
    boolean connect();
  }

  @BeforeMethod
  void beforeMethod() {
    reset(service);
    waiter = new Waiter();
    complete = new AtomicInteger();
    completeStats = new AtomicInteger();
    completeAsync = new AtomicInteger();
    completeStatsAsync = new AtomicInteger();
    failure = new AtomicInteger();
    failureStats = new AtomicInteger();
    failureAsync = new AtomicInteger();
    failureStatsAsync = new AtomicInteger();
    success = new AtomicInteger();
    successStats = new AtomicInteger();
    successAsync = new AtomicInteger();
    successStatsAsync = new AtomicInteger();
  }

  private <T> void registerListeners(RecurrentFuture<T> future) {
    future.whenComplete((r, f) -> {
      complete.incrementAndGet();
      waiter.resume();
    });
    future.whenComplete((r, f, s) -> {
      completeStats.incrementAndGet();
      waiter.resume();
    });
    future.whenCompleteAsync((r, f) -> {
      completeAsync.incrementAndGet();
      waiter.resume();
    });
    future.whenCompleteAsync((r, f, s) -> {
      completeStatsAsync.incrementAndGet();
      waiter.resume();
    });
    future.whenFailure((r, f) -> failure.incrementAndGet());
    future.whenFailure((r, f, s) -> failureStats.incrementAndGet());
    future.whenFailureAsync((r, f) -> {
      failureAsync.incrementAndGet();
      waiter.resume();
    });
    future.whenFailureAsync((r, f, s) -> {
      failureStatsAsync.incrementAndGet();
      waiter.resume();
    });
    future.whenSuccess((r) -> success.incrementAndGet());
    future.whenSuccess((r, s) -> successStats.incrementAndGet());
    future.whenSuccessAsync((r) -> {
      successAsync.incrementAndGet();
      waiter.resume();
    });
    future.whenSuccessAsync((r, s) -> {
      successStatsAsync.incrementAndGet();
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
    registerListeners(Recurrent.get(callable, new RetryPolicy().retryWhen(false), executor));
    waiter.await(1000, 6);

    // Then
    assertEquals(complete.get(), 1);
    assertEquals(completeStats.get(), 1);
    assertEquals(completeAsync.get(), 1);
    assertEquals(completeStatsAsync.get(), 1);
    assertEquals(failure.get(), 0);
    assertEquals(failureStats.get(), 0);
    assertEquals(failureAsync.get(), 0);
    assertEquals(failureStatsAsync.get(), 0);
    assertEquals(success.get(), 1);
    assertEquals(successStats.get(), 1);
    assertEquals(successAsync.get(), 1);
    assertEquals(successStatsAsync.get(), 1);
  }

  /**
   * Asserts that listeners are called the expected number of times for a failure completion.
   */
  public void testListenersForFailureCompletion() throws Throwable {
    Callable<Boolean> callable = () -> service.connect();

    // Given - Fail twice then succeed
    when(service.connect()).thenThrow(failures(2, SocketException.class)).thenReturn(false, false, true);

    // When
    registerListeners(Recurrent.get(callable, new RetryPolicy().retryWhen(false).withMaxRetries(3), executor));
    waiter.await(1000, 6);

    // Then
    assertEquals(complete.get(), 1);
    assertEquals(completeStats.get(), 1);
    assertEquals(completeAsync.get(), 1);
    assertEquals(completeStatsAsync.get(), 1);
    assertEquals(failure.get(), 1);
    assertEquals(failureStats.get(), 1);
    assertEquals(failureAsync.get(), 1);
    assertEquals(failureStatsAsync.get(), 1);
    assertEquals(success.get(), 0);
    assertEquals(successStats.get(), 0);
    assertEquals(successAsync.get(), 0);
    assertEquals(successStatsAsync.get(), 0);
  }
}
