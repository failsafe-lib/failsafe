package net.jodah.failsafe.functional;

import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.Test;

import net.jodah.concurrentunit.Waiter;
import net.jodah.failsafe.CircuitBreaker;
import net.jodah.failsafe.CircuitBreakerOpenException;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.Testing;

/**
 * Tests max concurrency in a circuit breaker.
 * 
 * @author Jonathan Halterman
 */
@Test(groups = "slow")
public class MaxBreakerConcurrencyTest {
  public void shouldLimitExecutionsToMaxConcurrency() throws Throwable {
    CircuitBreaker breaker = new CircuitBreaker().withMaxConcurrency(2);
    breaker.halfOpen();
    Waiter waiter = new Waiter();
    AtomicInteger counter = new AtomicInteger();

    for (int i = 0; i < 5; i++) {
      Throwable failure = null;

      // Retry until breaker allows execution
      do {
        try {
          Failsafe.with(breaker).run(() -> {
            waiter.assertTrue(counter.incrementAndGet() <= 2);
            Testing.sleep(100);
            counter.decrementAndGet();
            waiter.resume();
          });
        } catch (CircuitBreakerOpenException e) {
          failure = e;
          Thread.sleep(10);
        }
      } while (failure != null);
    }

    waiter.await(5000, 5);
  }
}
