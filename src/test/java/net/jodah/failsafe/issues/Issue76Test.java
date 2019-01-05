package net.jodah.failsafe.issues;

import net.jodah.concurrentunit.Waiter;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.FailsafeException;
import net.jodah.failsafe.RetryPolicy;
import org.testng.annotations.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

@Test
public class Issue76Test {
  public void shouldAbortOnSyncError() {
    AssertionError error = new AssertionError();
    try {
      Failsafe.with(new RetryPolicy<>().abortOn(AssertionError.class)).run(() -> {
        throw error;
      });
      fail();
    } catch (FailsafeException e) {
      assertEquals(e.getCause(), error);
    }
  }

  public void shouldAbortOnAsyncError() throws Exception {
    final AssertionError error = new AssertionError();
    Waiter waiter = new Waiter();
    Future<?> future = Failsafe.with(new RetryPolicy<>().abortOn(AssertionError.class)
        .onAbort(e -> {
          waiter.assertEquals(e.failure, error);
          waiter.resume();
        }))
        .with(Executors.newSingleThreadScheduledExecutor())
        .runAsync(() -> {
          throw error;
        });
    waiter.await(1000);

    try {
      future.get();
      fail();
    } catch (ExecutionException e) {
      assertEquals(e.getCause(), error);
    }
  }
}
