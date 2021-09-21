package net.jodah.failsafe.functional;

import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.FailsafeExecutor;
import net.jodah.failsafe.Testing;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;

@Test
public class NoPolicyTest extends Testing {
  public void testWithNoPolicy() {
    AtomicInteger successCounter = new AtomicInteger();
    AtomicInteger failureCounter = new AtomicInteger();
    FailsafeExecutor<Object> failsafe = Failsafe.none().onFailure(e -> {
      failureCounter.incrementAndGet();
    }).onSuccess(e -> {
      successCounter.incrementAndGet();
    });

    // Test success
    testGetSuccess(() -> {
      successCounter.set(0);
      failureCounter.set(0);
    }, failsafe, ctx -> "success", e -> {
      assertEquals(e.getAttemptCount(), 1);
      assertEquals(e.getExecutionCount(), 1);
      assertEquals(successCounter.get(), 1);
      assertEquals(failureCounter.get(), 0);
    }, "success");

    // Test failure
    testRunFailure(() -> {
      successCounter.set(0);
      failureCounter.set(0);
    }, failsafe, ctx -> {
      throw new IllegalStateException();
    }, e -> {
      assertEquals(e.getAttemptCount(), 1);
      assertEquals(e.getExecutionCount(), 1);
      assertEquals(successCounter.get(), 0);
      assertEquals(failureCounter.get(), 1);
    }, IllegalStateException.class);
  }
}
