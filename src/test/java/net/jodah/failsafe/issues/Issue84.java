package net.jodah.failsafe.issues;

import static org.testng.Assert.assertFalse;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.testng.annotations.Test;

import net.jodah.failsafe.Asserts;
import net.jodah.failsafe.CircuitBreaker;
import net.jodah.failsafe.CircuitBreakerOpenException;
import net.jodah.failsafe.Failsafe;

@Test
public class Issue84 {
  public void shouldHandleCircuitBreakerOpenException() throws Throwable {
    ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
    CircuitBreaker circuitBreaker = new CircuitBreaker().withDelay(10, TimeUnit.MINUTES).failWhen(false);
    circuitBreaker.open();

    // Synchronous
    Asserts.assertThrows(() -> Failsafe.with(circuitBreaker).get(() -> true), CircuitBreakerOpenException.class);

    // Synchronous with fallback
    assertFalse(Failsafe.with(circuitBreaker).withFallback(false).get(() -> true));

    // Asynchronous
    Future<Boolean> future1 = Failsafe.with(circuitBreaker).with(executor).get(() -> true);
    Asserts.assertThrows(() -> future1.get(), ExecutionException.class, CircuitBreakerOpenException.class);

    // Asynchronous with fallback
    Future<Boolean> future2 = Failsafe.with(circuitBreaker).with(executor).withFallback(false).get(() -> true);
    assertFalse(future2.get());

    // Future
    Future<Boolean> future3 = Failsafe.with(circuitBreaker).with(executor).future(() -> CompletableFuture.completedFuture(false));
    Asserts.assertThrows(() -> future3.get(), ExecutionException.class, CircuitBreakerOpenException.class);

    // Future with fallback
    Future<Boolean> future4 = Failsafe.with(circuitBreaker)
        .with(executor)
        .withFallback(false)
        .future(() -> CompletableFuture.completedFuture(false));
    assertFalse(future4.get());
  }
}
