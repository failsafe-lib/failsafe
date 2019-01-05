package net.jodah.failsafe.issues;

import net.jodah.failsafe.Asserts;
import net.jodah.failsafe.CircuitBreaker;
import net.jodah.failsafe.CircuitBreakerOpenException;
import net.jodah.failsafe.Failsafe;
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
    assertFalse(Failsafe.with(circuitBreaker).withFallback(false).get(() -> true));

    // Asynchronous
    Future<Boolean> future1 = Failsafe.with(circuitBreaker).with(executor).getAsync(() -> true);
    Asserts.assertThrows(future1::get, ExecutionException.class, CircuitBreakerOpenException.class);

    // Asynchronous with fallback
    Future<Boolean> future2 = Failsafe.with(circuitBreaker).with(executor).withFallback(false).getAsync(() -> true);
    assertFalse(future2.get());

    // Future
    Future<Boolean> future3 = Failsafe.with(circuitBreaker).with(executor).future(() -> CompletableFuture.completedFuture(false));
    Asserts.assertThrows(future3::get, ExecutionException.class, CircuitBreakerOpenException.class);

    // Future with fallback
    Future<Boolean> future4 = Failsafe.with(circuitBreaker)
        .with(executor)
        .withFallback(false)
        .future(() -> CompletableFuture.completedFuture(false));
    assertFalse(future4.get());
  }
}
