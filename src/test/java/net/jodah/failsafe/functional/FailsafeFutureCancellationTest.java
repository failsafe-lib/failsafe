package net.jodah.failsafe.functional;

import net.jodah.concurrentunit.Waiter;
import net.jodah.failsafe.*;
import net.jodah.failsafe.event.ExecutionCompletedEvent;
import net.jodah.failsafe.function.ContextualSupplier;
import net.jodah.failsafe.Policy;
import net.jodah.failsafe.testing.Testing;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.*;

/**
 * Tests behavior when a FailsafeFuture is explicitly cancelled.
 */
@Test
public class FailsafeFutureCancellationTest extends Testing {
  Waiter waiter;

  @BeforeMethod
  void beforeMethod() {
    waiter = new Waiter();
  }

  private <R> void assertCancel(FailsafeExecutor<R> executor, ContextualSupplier<R, R> supplier) throws Throwable {
    // Given
    CompletableFuture<R> future = executor.onComplete(e -> {
      waiter.assertNull(e.getResult());
      waiter.assertTrue(e.getFailure() instanceof CancellationException);
      waiter.resume();
    }).getAsync(supplier);

    Testing.sleep(300);

    // When
    assertTrue(future.cancel(true));
    waiter.await(1000);

    // Then
    assertTrue(future.isCancelled());
    // assertTrue(future.cancelFunctions.isEmpty());
    assertTrue(future.isDone());
    assertThrows(future::get, CancellationException.class);
  }

  public void shouldCancelOnGetAsyncWithRetries() throws Throwable {
    assertCancel(Failsafe.with(retryAlways), ctx -> {
      try {
        waiter.assertFalse(ctx.isCancelled());
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        waiter.assertTrue(ctx.isCancelled());
        throw e;
      }
      return false;
    });
  }

  public void shouldCancelOnGetAsyncWithTimeout() throws Throwable {
    assertCancel(Failsafe.with(Timeout.of(Duration.ofMinutes(1))), ctx -> {
      Thread.sleep(1000);
      return "test";
    });
  }

  /**
   * Asserts that cancelling a FailsafeFuture causes both retry policies to stop.
   */
  public void testCancelWithNestedRetries() throws Throwable {
    // Given
    Stats outerRetryStats = new Stats();
    Stats innerRetryStats = new Stats();
    RetryPolicy<Object> outerRetryPolicy = withStatsAndLogs(RetryPolicy.ofDefaults(), outerRetryStats);
    RetryPolicy<Object> innerRetryPolicy = withStatsAndLogs(
      RetryPolicy.builder().withMaxRetries(3).withDelay(Duration.ofMillis(100)).build(), innerRetryStats);
    AtomicReference<Future<Void>> futureRef = new AtomicReference<>();
    AtomicReference<ExecutionCompletedEvent<Object>> completedRef = new AtomicReference<>();
    Waiter waiter = new Waiter();

    // When
    futureRef.set(Failsafe.with(outerRetryPolicy, innerRetryPolicy).onComplete(e -> {
      completedRef.set(e);
      waiter.resume();
    }).runAsync(ctx -> {
      if (ctx.isFirstAttempt())
        throw new IllegalStateException();
      else
        futureRef.get().cancel(false);
    }));

    // Then
    assertThrows(() -> futureRef.get().get(1, TimeUnit.SECONDS), CancellationException.class);
    waiter.await(1000);
    assertNull(completedRef.get().getResult());
    assertTrue(completedRef.get().getFailure() instanceof CancellationException);
    assertEquals(outerRetryStats.failedAttemptCount, 0);
    assertEquals(innerRetryStats.failedAttemptCount, 1);
  }

  /**
   * Asserts that FailsafeFuture cancellations are propagated to a CompletionStage.
   */
  public void shouldPropagateCancellationToStage() {
    // Given
    Policy<String> retryPolicy = RetryPolicy.ofDefaults();

    // When
    CompletableFuture<String> promise = new CompletableFuture<>();
    CompletableFuture<String> future = Failsafe.with(retryPolicy).getStageAsync(() -> promise);
    sleep(200);
    future.cancel(false);

    // Then
    assertThrows(() -> future.get(1, TimeUnit.SECONDS), CancellationException.class);
    assertThrows(() -> promise.get(1, TimeUnit.SECONDS), CancellationException.class);
  }

  /**
   * Asserts that FailsafeFuture cancellations are propagated to the most recent ExecutionContext.
   */
  public void shouldPropagateCancellationToExecutionContext() throws Throwable {
    // Given
    Policy<Void> retryPolicy = withLogs(RetryPolicy.ofDefaults());
    AtomicReference<ExecutionContext<Void>> ctxRef = new AtomicReference<>();
    Waiter waiter = new Waiter();

    // When
    Future<?> future = Failsafe.with(retryPolicy).runAsync(ctx -> {
      ctxRef.set(ctx);
      if (ctx.getAttemptCount() < 2)
        throw new Exception();
      else {
        waiter.resume();
        Thread.sleep(1000);
      }
    });
    waiter.await(1000);
    future.cancel(true);

    // Then
    assertTrue(ctxRef.get().isCancelled());
  }

  private void assertInterruptedExceptionOnCancel(FailsafeExecutor<Boolean> failsafe) throws Throwable {
    Waiter waiter = new Waiter();
    CompletableFuture<Void> future = failsafe.runAsync(() -> {
      try {
        Thread.sleep(1000);
        waiter.fail("Expected to be interrupted");
      } catch (InterruptedException e) {
        waiter.resume();
      }
    });

    Thread.sleep(100);
    assertTrue(future.cancel(true));
    waiter.await(1000);
  }

  public void shouldInterruptExecutionOnCancelWithForkJoinPool() throws Throwable {
    assertInterruptedExceptionOnCancel(Failsafe.with(retryAlways));
  }

  public void shouldInterruptExecutionOnCancelWithScheduledExecutorService() throws Throwable {
    ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
    assertInterruptedExceptionOnCancel(Failsafe.with(retryAlways).with(executorService));
    executorService.shutdownNow();
  }

  public void shouldInterruptExecutionOnCancelWithExecutorService() throws Throwable {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    assertInterruptedExceptionOnCancel(Failsafe.with(retryAlways).with(executor));
    executor.shutdownNow();
  }
}
