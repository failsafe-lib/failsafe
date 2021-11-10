package dev.failsafe.issues;

import dev.failsafe.CircuitBreaker;
import dev.failsafe.CircuitBreakerOpenException;
import dev.failsafe.Failsafe;
import dev.failsafe.Fallback;
import dev.failsafe.testing.Asserts;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.concurrent.*;

import static org.testng.Assert.assertFalse;

@Test
public class Issue84Test {
  public void shouldHandleCircuitBreakerOpenException() throws Throwable {
    ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
    CircuitBreaker<Boolean> circuitBreaker = CircuitBreaker.<Boolean>builder()
      .withDelay(Duration.ofMinutes(10))
      .handleResult(false)
      .build();
    circuitBreaker.open();

    // Synchronous
    Asserts.assertThrows(() -> Failsafe.with(circuitBreaker).get(() -> true), CircuitBreakerOpenException.class);

    // Synchronous with fallback
    assertFalse(Failsafe.with(Fallback.of(false), circuitBreaker).get(() -> true));

    // Asynchronous
    Future<Boolean> future1 = Failsafe.with(circuitBreaker).with(executor).getAsync(() -> true);
    Asserts.assertThrows(future1::get, ExecutionException.class, CircuitBreakerOpenException.class);

    // Asynchronous with fallback
    Future<Boolean> future2 = Failsafe.with(Fallback.of(false), circuitBreaker).with(executor).getAsync(() -> true);
    assertFalse(future2.get());

    // Future
    Future<Boolean> future3 = Failsafe.with(circuitBreaker)
      .with(executor)
      .getStageAsync(() -> CompletableFuture.completedFuture(false));
    Asserts.assertThrows(future3::get, ExecutionException.class, CircuitBreakerOpenException.class);

    // Future with fallback
    Future<Boolean> future4 = Failsafe.with(Fallback.of(false), circuitBreaker)
      .getStageAsync(() -> CompletableFuture.completedFuture(false));
    assertFalse(future4.get());

    executor.shutdownNow();
  }
}
