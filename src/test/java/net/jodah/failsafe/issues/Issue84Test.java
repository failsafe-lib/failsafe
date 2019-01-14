package net.jodah.failsafe.issues;

import net.jodah.failsafe.*;
import org.testng.annotations.Test;

import java.util.concurrent.*;

import static org.testng.Assert.assertFalse;

@Test
public class Issue84Test {
  public void shouldHandleCircuitBreakerOpenException() throws Throwable {
    ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
    CircuitBreaker<Boolean> circuitBreaker = new CircuitBreaker<Boolean>().withDelay(10, TimeUnit.MINUTES).handleResult(false);
    circuitBreaker.open();

    // Synchronous
    Asserts.assertThrows(() -> Failsafe.with(circuitBreaker).get(() -> true), CircuitBreakerOpenException.class);

    // Synchronous with fallback
    assertFalse(Failsafe.with(circuitBreaker).with(Fallback.of(false)).get(() -> true));

    // Asynchronous
    Future<Boolean> future1 = Failsafe.with(circuitBreaker).with(executor).getAsync(() -> true);
    Asserts.assertThrows(future1::get, ExecutionException.class, CircuitBreakerOpenException.class);

    // Asynchronous with fallback
    Future<Boolean> future2 = Failsafe.with(circuitBreaker).with(executor).with(Fallback.of(false)).getAsync(() -> true);
    assertFalse(future2.get());

    // Future
    Future<Boolean> future3 = Failsafe.with(circuitBreaker).with(executor).futureAsync(() -> CompletableFuture.completedFuture(false));
    Asserts.assertThrows(future3::get, ExecutionException.class, CircuitBreakerOpenException.class);

    // Future with fallback
    Future<Boolean> future4 = Failsafe.with(circuitBreaker)
        .with(executor)
        .with(Fallback.of(false))
        .futureAsync(() -> CompletableFuture.completedFuture(false));
    assertFalse(future4.get());
  }
}
