package net.jodah.failsafe.issues;

import static org.testng.Assert.assertEquals;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.Test;

import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

@Test
public class Issue55 {
  public void shouldOnlyFallbackOnFailure() throws Throwable {
    ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

    AtomicInteger counter = new AtomicInteger();
    Failsafe.with(new RetryPolicy()).with(executor).withFallback(() -> counter.incrementAndGet()).get(() -> null);

    Thread.sleep(100);
    assertEquals(counter.get(), 0);

    Failsafe.with(new RetryPolicy().withMaxRetries(1))
        .with(executor)
        .withFallback(() -> counter.incrementAndGet())
        .run(() -> {
          throw new RuntimeException();
        });

    Thread.sleep(100);
    assertEquals(counter.get(), 1);
  }
}
