package dev.failsafe.issues;

import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import org.testng.annotations.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;

public class Issue311Test {
  @Test
  void failsafeFail() throws Throwable {
    AtomicInteger counter = new AtomicInteger(0);
    Executor executor = Executors.newSingleThreadExecutor();
    Failsafe.with(RetryPolicy.builder().handle(RuntimeException.class).withMaxAttempts(2).build())
      .with(executor)
      .runAsync(() -> {
        if (counter.incrementAndGet() == 1)
          throw new RuntimeException();
      }).get();
    assertEquals(counter.get(), 2);
  }
}
