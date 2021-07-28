package net.jodah.failsafe.issues;

import net.jodah.failsafe.CircuitBreaker;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.Fallback;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class Issue75Test {
  @Test
  public void testThatFailSafeIsBrokenWithFallback() throws Exception {
    CircuitBreaker<Integer> breaker = new CircuitBreaker<Integer>().withFailureThreshold(10, 100).withSuccessThreshold(2).withDelay(
        Duration.ofMillis(100));
    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    int result = Failsafe.with(Fallback.of(e -> 999), breaker)
        .with(executor)
        .getStageAsync(() -> CompletableFuture.completedFuture(223))
        .get();

    Assert.assertEquals(result, 223);
    executor.shutdownNow();
  }
}
