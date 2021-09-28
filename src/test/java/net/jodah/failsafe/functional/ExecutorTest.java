package net.jodah.failsafe.functional;

import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.testing.Testing;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;

/**
 * Tests failsafe with an executor.
 */
@Test
public class ExecutorTest extends Testing {
  AtomicInteger executions = new AtomicInteger();
  Executor executor = runnable -> {
    executions.incrementAndGet();
    runnable.run();
  };

  @BeforeMethod
  protected void beforeMethod() {
    executions.set(0);
  }

  public void testExecutorWithSyncExecution() {
    assertThrows(() -> Failsafe.with(new RetryPolicy<>()).with(executor).run(() -> {
      throw new IllegalStateException();
    }), IllegalStateException.class);
    assertEquals(executions.get(), 3);
  }

  public void testExecutorWithAsyncExecution() {
    assertThrows(() -> Failsafe.with(new RetryPolicy<>()).with(executor).runAsync(() -> {
      throw new IllegalStateException();
    }).get(), ExecutionException.class, IllegalStateException.class);
    assertEquals(executions.get(), 3);
  }
}
