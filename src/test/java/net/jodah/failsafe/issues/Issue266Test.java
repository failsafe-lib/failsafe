package net.jodah.failsafe.issues;

import net.jodah.failsafe.Asserts;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.Policy;
import net.jodah.failsafe.RetryPolicy;
import org.testng.annotations.Test;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;

@Test
public class Issue266Test {
  AtomicInteger cancelledCounter = new AtomicInteger();

  public void test() {
    Policy<String> retryPolicy = new RetryPolicy<>();
    CompletableFuture<String> future = Failsafe.with(retryPolicy).getStageAsync(this::computeSomething);
    future.whenComplete((r, t) -> {
      if (t instanceof CancellationException) {
        cancelledCounter.incrementAndGet();
      }
    });
    future.cancel(true);
    Asserts.assertThrows(future::get, CancellationException.class);
    assertEquals(cancelledCounter.get(), 2);
  }

  CompletionStage<String> computeSomething() {
    CompletableFuture<String> future = new CompletableFuture<>();
    future.whenComplete((r, t) -> {
      if (t instanceof CancellationException) {
        cancelledCounter.incrementAndGet();
      }
    });
    return future;
  }
}
