package net.jodah.failsafe.functional;

import net.jodah.failsafe.*;
import net.jodah.failsafe.testing.Asserts;
import net.jodah.failsafe.testing.Testing;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.testng.Assert.*;

/**
 * Tests various execution interrupt scenarios.
 */
@Test
public class InterruptionTest extends Testing {
  /**
   * Asserts that Failsafe throws when interrupted while blocked in an execution.
   */
  public void shouldThrowWhenInterruptedDuringSynchronousExecution() {
    Thread main = Thread.currentThread();
    CompletableFuture.runAsync(() -> {
      try {
        Thread.sleep(100);
        main.interrupt();
      } catch (InterruptedException e) {
      }
    });

    Asserts.assertThrows(() -> Failsafe.with(new RetryPolicy<>().withMaxRetries(0)).run(() -> {
      try {
        Thread.sleep(10000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw e;
      }
      fail("Expected interruption");
    }), FailsafeException.class, InterruptedException.class);
    // Clear interrupt flag
    assertTrue(Thread.interrupted());
  }

  /**
   * Asserts that the thread's interrupt flag is set after interrupting a sync RetryPolicy delay.
   */
  public void shouldThrowWhenInterruptedDuringRetryPolicyDelay() {
    RetryPolicy<Object> rp = new RetryPolicy<>().withDelay(Duration.ofMillis(500));
    Thread main = Thread.currentThread();
    CompletableFuture.runAsync(() -> {
      try {
        Thread.sleep(100);
        main.interrupt();
      } catch (InterruptedException e) {
      }
    });

    Asserts.assertThrows(() -> Failsafe.with(rp).run(() -> {
      throw new Exception();
    }), FailsafeException.class, InterruptedException.class);
    // Clear interrupt flag
    Assert.assertTrue(Thread.interrupted());
  }

  /**
   * Asserts that Failsafe throws when interrupting while blocked between executions.
   */
  public void shouldThrowWhenInterruptedDuringSynchronousDelay() {
    Thread mainThread = Thread.currentThread();
    new Thread(() -> {
      try {
        Thread.sleep(100);
        mainThread.interrupt();
      } catch (Exception e) {
      }
    }).start();

    try {
      Failsafe.with(new RetryPolicy<>().withDelay(Duration.ofSeconds(5))).run(() -> {
        throw new Exception();
      });
    } catch (Exception e) {
      assertTrue(e instanceof FailsafeException);
      assertTrue(e.getCause() instanceof InterruptedException);
      // Clear interrupt flag
      assertTrue(Thread.interrupted());
      return;
    }
    fail("Exception expected");
  }

  /**
   * Asserts that the interrrupt flag is reset when a sync execution is interrupted.
   */
  public void shouldResetInterruptFlag() {
    // Given
    Thread t = Thread.currentThread();
    new Thread(() -> {
      try {
        Thread.sleep(100);
        t.interrupt();
      } catch (InterruptedException e) {
      }
    }).start();

    // Then
    assertThrows(() -> Failsafe.with(retryNever).run(() -> {
      Thread.sleep(1000);
    }), FailsafeException.class, InterruptedException.class);
    t.interrupt();

    // Then
    assertTrue(Thread.interrupted());
  }

  /**
   * Ensures that an internally interrupted execution should always have the interrupt flag cleared afterwards.
   */
  public void shouldResetInterruptFlagAfterInterruption() throws Throwable {
    // Given
    Timeout<Object> timeout = Timeout.of(Duration.ofMillis(1)).withInterrupt(true);

    // When / Then
    testRunFailure(false, Failsafe.with(timeout), ctx -> {
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }, (f, e) -> {
      assertFalse(Thread.currentThread().isInterrupted(), "Interrupt flag should be cleared after Failsafe handling");
    }, TimeoutExceededException.class);
  }
}
