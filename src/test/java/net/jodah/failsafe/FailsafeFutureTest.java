package net.jodah.failsafe;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.testng.annotations.Test;

import net.jodah.concurrentunit.Waiter;

@Test
public class FailsafeFutureTest {
  ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);

  @Test(expectedExceptions = TimeoutException.class)
  public void shouldGetWithTimeout() throws Throwable {
    Failsafe.with(new RetryPolicy()).with(executor).run(() -> {
      Thread.sleep(1000);
    }).get(100, TimeUnit.MILLISECONDS);

    Thread.sleep(1000);
  }

  public void shouldCompleteFutureOnCancel() throws Throwable {
    Waiter waiter = new Waiter();
    FailsafeFuture<String> future = Failsafe.with(new RetryPolicy()).with(executor).onComplete((r, f) -> {
      waiter.assertNull(r);
      waiter.assertTrue(f instanceof CancellationException);
      waiter.resume();
    }).get(() -> {
      Thread.sleep(5000);
      return "test";
    });

    Testing.sleep(300);
    future.cancel(true);
    waiter.await(1000);

    assertTrue(future.isCancelled());
    assertTrue(future.isDone());
    Asserts.assertThrows(() -> future.get(), CancellationException.class);
  }

  /**
   * Asserts that completion handlers are not called again if a completed execution is cancelled.
   */
  public void shouldNotCancelCompletedExecution() throws Throwable {
    Waiter waiter = new Waiter();
    FailsafeFuture<String> future = Failsafe.with(new RetryPolicy()).with(executor).onComplete((r, f) -> {
      waiter.assertEquals("test", r);
      waiter.assertNull(f);
      waiter.resume();
    }).get(() -> "test");

    waiter.await(500);
    assertFalse(future.cancel(true));
    assertFalse(future.isCancelled());
    assertTrue(future.isDone());
    assertEquals(future.get(), "test");
  }
}
