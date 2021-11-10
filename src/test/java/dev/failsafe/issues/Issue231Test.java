package dev.failsafe.issues;

import dev.failsafe.Timeout;
import dev.failsafe.testing.Asserts;
import dev.failsafe.Failsafe;
import dev.failsafe.TimeoutExceededException;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.testng.Assert.assertTrue;

@Test
public class Issue231Test {
  /**
   * Timeout, even with interruption, should wait for the execution to complete.
   */
  public void shouldWaitForExecutionCompletion() {
    Timeout<Object> timeout = Timeout.builder(Duration.ofMillis(100)).withInterrupt().build();
    AtomicBoolean executionCompleted = new AtomicBoolean();
    Asserts.assertThrows(() -> Failsafe.with(timeout).runAsync(() -> {
      try {
        Thread.sleep(1000);
      } catch (InterruptedException ignore) {
        Thread.sleep(200);
        executionCompleted.set(true);
      }
    }).get(), ExecutionException.class, TimeoutExceededException.class);
    assertTrue(executionCompleted.get());
  }
}
