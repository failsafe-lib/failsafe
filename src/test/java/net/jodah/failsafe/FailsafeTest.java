package net.jodah.failsafe;

import static org.testng.Assert.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.mockito.Mockito;
import org.testng.annotations.Test;

import net.jodah.failsafe.AsyncFailsafe;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.SyncFailsafe;
import net.jodah.failsafe.util.concurrent.Scheduler;

@Test
public class FailsafeTest {
  public void testWithExecutor() {
    ScheduledExecutorService executor = Mockito.mock(ScheduledExecutorService.class);
    Scheduler scheduler = Mockito.mock(Scheduler.class);

    assertTrue(Failsafe.with(new RetryPolicy()) instanceof SyncFailsafe);
    assertTrue(Failsafe.with(new RetryPolicy()).with(executor) instanceof AsyncFailsafe);
    assertTrue(Failsafe.with(new RetryPolicy()).with(scheduler) instanceof AsyncFailsafe);

  }

  public void test() throws Throwable {
    CompletableFuture.completedFuture("test").whenComplete((r, f) -> test(r));

    Failsafe.with(new RetryPolicy())
        .with(Executors.newScheduledThreadPool(1))
        .onFailedAttempt((String r, Throwable e) -> test(r))
        .onFailedAttemptAsync((String r, Throwable e) -> test(r))
        .onComplete((String r, Throwable t) -> test(r))
        .run(() -> {});

    Failsafe.<String>with(new RetryPolicy())
        .with(Executors.newScheduledThreadPool(1))
        .onFailedAttempt((r, e) -> test(r))
        .onFailedAttemptAsync((r, e) -> test(r))
        .onComplete((r, t) -> test(r))
        .get(() -> "asdf");
  }

  private void test(String adsf) {
  }
}
