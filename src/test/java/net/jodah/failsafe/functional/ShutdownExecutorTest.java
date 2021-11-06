package net.jodah.failsafe.functional;

import net.jodah.concurrentunit.Waiter;
import net.jodah.failsafe.*;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static net.jodah.failsafe.testing.Asserts.assertThrows;
import static net.jodah.failsafe.testing.Testing.runAsync;
import static org.testng.Assert.assertEquals;

/**
 * Tests the handling of an executor service that is shutdown.
 */
@Test
public class ShutdownExecutorTest {
  Waiter waiter;

  @BeforeMethod
  protected void beforeMethod() {
    waiter = new Waiter();
  }

  /**
   * Asserts that Failsafe handles an initial scheduling failure due to an executor being shutdown.
   */
  public void shouldHandleInitialSchedulingFailure() {
    // Given
    ExecutorService executor = Executors.newFixedThreadPool(1);
    executor.shutdownNow();

    // When
    Future<?> future = Failsafe.with(Fallback.of(false), RetryPolicy.ofDefaults(), CircuitBreaker.ofDefaults())
      .with(executor)
      .runAsync(() -> waiter.fail("Should not execute supplier since executor has been shutdown"));

    assertThrows(() -> future.get(1000, TimeUnit.SECONDS), ExecutionException.class, RejectedExecutionException.class);
  }

  /**
   * Asserts that an ExecutorService shutdown() will leave current tasks running while preventing new tasks.
   */
  public void shouldHandleShutdown() throws Throwable {
    // Given
    ExecutorService executor = Executors.newSingleThreadExecutor();
    AtomicInteger counter = new AtomicInteger();

    // When
    Future<?> future = Failsafe.with(RetryPolicy.ofDefaults()).with(executor).getAsync(() -> {
      Thread.sleep(200);
      counter.incrementAndGet();
      return "success";
    });

    Thread.sleep(100);
    executor.shutdown();
    assertEquals("success", future.get());
    assertEquals(counter.get(), 1, "Supplier should have completed execution before executor was shutdown");

    future = Failsafe.with(RetryPolicy.ofDefaults()).with(executor).getAsync(() -> "test");
    assertThrows(future::get, ExecutionException.class, RejectedExecutionException.class);
  }

  /**
   * Asserts that an ExecutorService shutdown() will interrupt current tasks running and prevent new tasks.
   */
  public void shouldHandleShutdownNow() throws Throwable {
    // Given
    ExecutorService executor = Executors.newSingleThreadExecutor();
    AtomicInteger counter = new AtomicInteger();

    // When
    Future<?> future = Failsafe.with(RetryPolicy.ofDefaults()).with(executor).runAsync(() -> {
      Thread.sleep(200);
      counter.incrementAndGet();
    });

    Thread.sleep(100);
    executor.shutdownNow();
    assertThrows(future::get, ExecutionException.class, RejectedExecutionException.class);
    assertEquals(counter.get(), 0, "Supplier should have been interrupted after executor shutdownNow");
  }

  /**
   * Asserts that an ExecutorService shutdown() will not prevent internally scheduled Timeout tasks from cancelling a
   * sync execution.
   */
  public void testShutdownDoesNotPreventTimeoutSync() {
    // Given
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Timeout<Object> timeout = Timeout.builder(Duration.ofMillis(200)).withInterrupt().build();
    AtomicInteger counter = new AtomicInteger();

    // When / then
    assertThrows(() -> Failsafe.with(timeout).with(executor).run(() -> {
      Thread.sleep(500);
      counter.incrementAndGet();
    }), TimeoutExceededException.class);
    runAsync(() -> {
      Thread.sleep(100);
      executor.shutdown();
    });
    assertEquals(counter.get(), 0, "Supplier should have been interrupted after Timeout");
  }

  /**
   * Asserts that an ExecutorService shutdown() will not prevent internally scheduled Timeout tasks from cancelling an
   * async execution.
   */
  public void testShutdownDoesNotPreventTimeoutAsync() throws Throwable {
    // Given
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Timeout<Object> timeout = Timeout.builder(Duration.ofMillis(200)).withInterrupt().build();
    AtomicInteger counter = new AtomicInteger();

    // When
    Future<?> future = Failsafe.with(timeout).with(executor).runAsync(() -> {
      Thread.sleep(500);
      counter.incrementAndGet();
    });
    Thread.sleep(100);
    executor.shutdown();

    // Then
    assertThrows(future::get, ExecutionException.class, TimeoutExceededException.class);
    assertEquals(counter.get(), 0, "Supplier should have been interrupted after Timeout");
  }
}
