package net.jodah.failsafe.functional;

import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.FailsafeExecutor;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static net.jodah.failsafe.Testing.testSyncAndAsyncFailure;
import static net.jodah.failsafe.Testing.testSyncAndAsyncSuccess;
import static org.testng.Assert.assertEquals;

@Test
public class NoPolicyTest {
  public void testWithNoPolicy() {
    AtomicInteger successCounter = new AtomicInteger();
    AtomicInteger failureCounter = new AtomicInteger();
    FailsafeExecutor<Object> executor = Failsafe.none().onFailure(e -> {
      failureCounter.incrementAndGet();
    }).onSuccess(e -> {
      successCounter.incrementAndGet();
    });

    // Test success
    testSyncAndAsyncSuccess(executor, () -> {
      successCounter.set(0);
      failureCounter.set(0);
    }, () -> "success", e -> {
      assertEquals(e.getAttemptCount(), 1);
      assertEquals(e.getExecutionCount(), 1);
      assertEquals(successCounter.get(), 1);
      assertEquals(failureCounter.get(), 0);
    }, "success");

    // Test failure
    testSyncAndAsyncFailure(executor, () -> {
      successCounter.set(0);
      failureCounter.set(0);
    }, () -> {
      throw new IllegalStateException();
    }, e -> {
      assertEquals(e.getAttemptCount(), 1);
      assertEquals(e.getExecutionCount(), 1);
      assertEquals(successCounter.get(), 0);
      assertEquals(failureCounter.get(), 1);
    }, IllegalStateException.class);
  }
}
