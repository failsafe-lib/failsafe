package dev.failsafe.issues;

import dev.failsafe.Fallback;
import dev.failsafe.CircuitBreaker;
import dev.failsafe.Failsafe;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class Issue75Test {
  @Test
  public void testThatFailSafeIsBrokenWithFallback() throws Exception {
    CircuitBreaker<Integer> breaker = CircuitBreaker.<Integer>builder()
      .withFailureThreshold(10, 100)
      .withSuccessThreshold(2)
      .withDelay(Duration.ofMillis(100))
      .build();
    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    int result = Failsafe.with(Fallback.of(e -> 999), breaker)
      .with(executor)
      .getStageAsync(() -> CompletableFuture.completedFuture(223))
      .get();

    Assert.assertEquals(result, 223);
    executor.shutdownNow();
  }
}
