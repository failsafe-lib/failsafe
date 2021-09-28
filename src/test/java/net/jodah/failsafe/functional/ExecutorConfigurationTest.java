package net.jodah.failsafe.functional;

import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.testing.Testing;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests the configuration of an Executor.
 */
@Test
public class ExecutorConfigurationTest extends Testing {
  AtomicBoolean executorCalled;
  AtomicBoolean executionCalled;
  RetryPolicy<String> retryPolicy = withLogs(new RetryPolicy<>());

  Executor executor = execution -> {
    executorCalled.set(true);
    execution.run();
  };

  Executor throwingExecutor = execution -> {
    executorCalled.set(true);
    execution.run();
    throw new IllegalStateException();
  };

  @BeforeMethod
  protected void beforeMethod() {
    executorCalled = new AtomicBoolean();
    executionCalled = new AtomicBoolean();
  }

  public void testSyncExecutionSuccess() {
    String result = Failsafe.with(retryPolicy).with(executor).get(() -> {
      executionCalled.set(true);
      return "result";
    });

    assertTrue(executorCalled.get());
    assertTrue(executionCalled.get());
    assertNull(result);
  }

  public void testSyncExecutionFailure() {
    assertThrows(() -> Failsafe.with(retryPolicy).with(executor).run(() -> {
      executionCalled.set(true);
      throw new IllegalStateException();
    }), IllegalStateException.class);

    assertTrue(executorCalled.get());
    assertTrue(executionCalled.get());
  }

  public void testSyncExecutionThatThrowsFromTheExecutor() {
    assertThrows(() -> Failsafe.with(retryPolicy).with(throwingExecutor).run(() -> {
      executionCalled.set(true);
    }), IllegalStateException.class);

    assertTrue(executorCalled.get());
    assertTrue(executionCalled.get());
  }

  public void testAsyncExecutionSuccess() throws Throwable {
    AtomicBoolean fjpAssertion = new AtomicBoolean();

    String result = Failsafe.with(retryPolicy).with(executor).getAsync(() -> {
      fjpAssertion.set(Thread.currentThread() instanceof ForkJoinWorkerThread);
      executionCalled.set(true);
      return "result";
    }).get();

    assertTrue(executorCalled.get());
    assertTrue(executionCalled.get());
    assertTrue(fjpAssertion.get(), "the execution should run on a fork join pool thread");
    assertNull(result);
  }

  public void testAsyncExecutionFailure() {
    AtomicBoolean fjpAssertion = new AtomicBoolean();

    assertThrows(() -> Failsafe.with(retryPolicy).with(executor).getAsync(() -> {
      fjpAssertion.set(Thread.currentThread() instanceof ForkJoinWorkerThread);
      executionCalled.set(true);
      throw new IllegalStateException();
    }).get(), ExecutionException.class, IllegalStateException.class);

    assertTrue(executorCalled.get());
    assertTrue(executionCalled.get());
    assertTrue(fjpAssertion.get(), "the execution should run on a fork join pool thread");
  }

  public void testAsyncExecutionThatThrowsFromTheExecutor() {
    assertThrows(() -> Failsafe.with(retryPolicy).with(throwingExecutor).runAsync(() -> {
      executionCalled.set(true);
    }).get(), ExecutionException.class, IllegalStateException.class);

    assertTrue(executorCalled.get());
    assertTrue(executionCalled.get());
  }
}
