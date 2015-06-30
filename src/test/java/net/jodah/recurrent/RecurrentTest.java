package net.jodah.recurrent;

import static net.jodah.recurrent.Asserts.shouldFail;
import static net.jodah.recurrent.Testing.failAlways;
import static net.jodah.recurrent.Testing.failNTimes;
import static org.testng.Assert.assertEquals;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import net.jodah.concurrentunit.Waiter;
import net.jodah.recurrent.Testing.RecordingRunnable;

@Test
public class RecurrentTest {
  private ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
  private Scheduler scheduler = Schedulers.of(executor);
  private RetryPolicy retryTwice = new RetryPolicy().withMaxRetries(2);
  private Waiter waiter;

  @BeforeMethod
  protected void beforeMethod() {
    waiter = new Waiter();
  }

  public void testDoWithRetries() throws Throwable {
    // Fail twice then succeed
    RuntimeException expectedFailure = new IllegalArgumentException();
    RecordingRunnable runnable = failNTimes(2, expectedFailure);
    Recurrent.doWithRetries(runnable, new RetryPolicy());
    assertEquals(runnable.failures, 2);

    // Fail three times
    final RecordingRunnable runnable2 = failAlways(expectedFailure);
    shouldFail(() -> Recurrent.doWithRetries(runnable2, retryTwice), expectedFailure);
    assertEquals(runnable2.failures, 3);
  }

  public void testDoWithRetriesWithScheduler() throws Throwable {
    RuntimeException expectedFailure = new IllegalArgumentException();

    // Fail twice then succeed
    waiter.expectResume();
    RecordingRunnable runnable = failNTimes(2, expectedFailure);
    Recurrent.doWithRetries(runnable, new RetryPolicy(), scheduler).whenComplete((result, failure) -> {
      waiter.resume();
    });

    waiter.await();
    assertEquals(runnable.failures, 2);

    // Fail three times
    waiter.expectResume();
    runnable = failAlways(expectedFailure);
    Recurrent.doWithRetries(runnable, retryTwice, scheduler).whenComplete((result, failure) -> {
      waiter.assertEquals(expectedFailure, failure);
      waiter.resume();
    });

    waiter.await();
    assertEquals(runnable.failures, 3);
  }
}
