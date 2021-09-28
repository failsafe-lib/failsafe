package net.jodah.failsafe.functional;

import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.FailsafeExecutor;
import net.jodah.failsafe.testing.Testing;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;

/**
 * Tests the use of Failsafe.none().
 */
@Test
public class NoPolicyTest extends Testing {
  public void testWithNoPolicy() {
    AtomicInteger successCounter = new AtomicInteger();
    AtomicInteger failureCounter = new AtomicInteger();
    FailsafeExecutor<Object> failsafe = Failsafe.none().onFailure(e -> {
      System.out.println("Failure");
      failureCounter.incrementAndGet();
    }).onSuccess(e -> {
      System.out.println("Success");
      successCounter.incrementAndGet();
    });

    // Test success
    testGetSuccess(() -> {
      successCounter.set(0);
      failureCounter.set(0);
    }, failsafe, ctx -> {
      return "success";
    }, (f, e) -> {
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
    }, (f, e) -> {
      assertEquals(e.getAttemptCount(), 1);
      assertEquals(e.getExecutionCount(), 1);
      assertEquals(successCounter.get(), 0);
      assertEquals(failureCounter.get(), 1);
    }, IllegalStateException.class);
  }
}
