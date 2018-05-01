package net.jodah.failsafe.issues;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.testng.Assert;
import org.testng.annotations.Test;

import net.jodah.failsafe.CircuitBreaker;
import net.jodah.failsafe.Failsafe;

public class Issue75Test {
  @Test
  public void testThatFailSafeIsBrokenWithFallback() throws Exception {
    CircuitBreaker breaker = new CircuitBreaker().withFailureThreshold(10, 100).withSuccessThreshold(2).withDelay(100,
        TimeUnit.MILLISECONDS);
    ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
    int result = Failsafe.with(breaker)
        .with(service)
        .withFallback((a, b) -> 999)
        .future(() -> CompletableFuture.completedFuture(223))
        .get();

    Assert.assertEquals(result, 223);
  }
}
