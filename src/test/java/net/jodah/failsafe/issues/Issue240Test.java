package net.jodah.failsafe.issues;

import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.testing.Testing;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;

@Test
public class Issue240Test {
  public void testHandleResult() {
    AtomicInteger counter = new AtomicInteger();
    RetryPolicy<Object> rp = new RetryPolicy<>().handle(IllegalArgumentException.class)
      .withMaxRetries(2)
      .handleResult(null);

    Testing.ignoreExceptions(() -> {
      Failsafe.with(rp).get(() -> {
        counter.incrementAndGet();
        throw new IllegalStateException();
      });
    });

    assertEquals(counter.get(), 1);
  }

  public void testAbortWhen() {
    AtomicInteger counter = new AtomicInteger();
    RetryPolicy<Object> rp = new RetryPolicy<>().handle(IllegalArgumentException.class)
      .withMaxRetries(2)
      .abortWhen(null);

    Testing.ignoreExceptions(() -> {
      Failsafe.with(rp).get(() -> {
        counter.incrementAndGet();
        throw new IllegalArgumentException();
      });
    });

    assertEquals(counter.get(), 3);
  }
}
