package net.jodah.failsafe.issues;

import net.jodah.concurrentunit.Waiter;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.testng.annotations.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

@Test
public class Issue76Test {
  public void shouldAbortOnSyncError() {
    AssertionError error = new AssertionError();
    try {
      Failsafe.with(RetryPolicy.builder().abortOn(AssertionError.class).build()).run(() -> {
        throw error;
      });
      fail();
    } catch (AssertionError e) {
      assertEquals(e, error);
    }
  }

  public void shouldAbortOnAsyncError() throws Exception {
    final AssertionError error = new AssertionError();
    Waiter waiter = new Waiter();
    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    Future<?> future = Failsafe.with(RetryPolicy.builder().abortOn(AssertionError.class).build().onAbort(e -> {
      waiter.assertEquals(e.getFailure(), error);
      waiter.resume();
    })).with(executor).runAsync(() -> {
      throw error;
    });
    waiter.await(1000);

    try {
      future.get();
      fail();
    } catch (ExecutionException e) {
      assertEquals(e.getCause(), error);
    } finally {
      executor.shutdownNow();
    }
  }
}
