package net.jodah.recurrent;

import static net.jodah.recurrent.Asserts.assertThrows;
import static net.jodah.recurrent.Testing.failAlways;
import static net.jodah.recurrent.Testing.failNTimes;
import static org.testng.Assert.assertEquals;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import net.jodah.concurrentunit.Waiter;
import net.jodah.recurrent.Testing.RecordingCallable;

@Test
public class RecurrentTest {
  private ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
  private RetryPolicy retryTwice = new RetryPolicy().withMaxRetries(2);
  private Waiter waiter;

  @BeforeMethod
  protected void beforeMethod() {
    waiter = new Waiter();
  }

  public void testWithRetries() throws Throwable {
    // Fail twice then succeed
    RuntimeException expectedFailure = new IllegalArgumentException();
    RecordingCallable<String> runnable = failNTimes(2, expectedFailure, "foo");
    assertEquals(Recurrent.withRetries(runnable, new RetryPolicy()), "foo");
    assertEquals(runnable.failures, 2);

    // Fail three times
    final RecordingCallable<?> runnable2 = failAlways(expectedFailure);
    assertThrows(expectedFailure, () -> Recurrent.withRetries(runnable2, retryTwice));
    assertEquals(runnable2.failures, 3);
  }

  public void testWithRetriesWithScheduler() throws Throwable {
    RuntimeException expectedFailure = new IllegalArgumentException();

    // Fail twice then succeed
    waiter.expectResume();
    RecordingCallable<String> runnable = failNTimes(2, expectedFailure, "foo");
    Recurrent.withRetries(runnable, new RetryPolicy(), executor).whenComplete((result, failure) -> {
      waiter.assertEquals("foo", result);
      waiter.assertNull(failure);
      waiter.resume();
    });

    waiter.await();
    assertEquals(runnable.failures, 2);

    // Fail three times
    waiter.expectResume();
    RecordingCallable<String> runnable2 = failAlways(expectedFailure);
    assertThrows(ExecutionException.class,
        () -> Recurrent.withRetries(runnable2, retryTwice, executor).whenComplete((result, failure) -> {
          waiter.assertNull(result);
          waiter.assertEquals(expectedFailure, failure);
          waiter.resume();
        }).get());

    waiter.await();
    assertEquals(runnable2.failures, 3);
  }

  public void testWithRetriesWithInitialSynchronousCall() throws Throwable {
    RuntimeException expectedFailure = new IllegalArgumentException();
    RecordingCallable<String> runnable = failNTimes(2, expectedFailure, "foo");
    waiter.expectResume();

    // Fail twice then succeed
    Recurrent.withRetries(runnable, new RetryPolicy(), executor, true).whenComplete((result, failure) -> {
      waiter.assertEquals("foo", result);
      waiter.assertNull(failure);
      waiter.resume();
    });

    waiter.await();
  }
}
