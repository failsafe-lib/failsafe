package net.jodah.failsafe.issues;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.testng.annotations.Test;

import net.jodah.concurrentunit.Waiter;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.FailsafeException;
import net.jodah.failsafe.RetryPolicy;

@Test
public class Issue76Test {
  public void shouldAbortOnSyncError() throws Exception {
    AssertionError error = new AssertionError();
    try {
      Failsafe.with(new RetryPolicy().abortOn(AssertionError.class)).run(() -> {
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
    Future<?> future = Failsafe.with(new RetryPolicy().abortOn(AssertionError.class))
        .with(Executors.newSingleThreadScheduledExecutor())
        .onAbort(e -> {
          waiter.assertEquals(e, error);
          waiter.resume();
        })
        .run(() -> {
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
