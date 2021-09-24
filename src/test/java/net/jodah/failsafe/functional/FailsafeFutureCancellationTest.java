package net.jodah.failsafe.functional;

import net.jodah.concurrentunit.Waiter;
import net.jodah.failsafe.*;
import net.jodah.failsafe.event.ExecutionCompletedEvent;
import net.jodah.failsafe.spi.Policy;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import static org.testng.Assert.*;

/**
 * Tests behavior when a FailsafeFuture is manually cancelled.
 */
@Test
public class FailsafeFutureCancellationTest extends Testing {
  /**
   * Asserts that cancelling a FailsafeFuture causes both retry policies to stop.
   */
  public void testCancelWithNestedRetries() throws Throwable {
    // Given
    Stats outerRetryStats = new Stats();
    Stats innerRetryStats = new Stats();
    RetryPolicy<Object> outerRetryPolicy = withStatsAndLogs(new RetryPolicy<>().withMaxRetries(2), outerRetryStats);
    RetryPolicy<Object> innerRetryPolicy = withStatsAndLogs(
      new RetryPolicy<>().withMaxRetries(3).withDelay(Duration.ofMillis(100)), innerRetryStats);
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
  public void shouldPropagateCancellationToStage() throws Throwable {
    // Given
    Policy<String> retryPolicy = new RetryPolicy<>();
    Waiter waiter = new Waiter();
    BiConsumer<String, Throwable> resumeWaiter = (r, t) -> {
      if (t instanceof CancellationException)
        waiter.resume();
    };

    // When
    CompletableFuture<String> future = Failsafe.with(retryPolicy).getStageAsync(() -> {
      waiter.resume();
      CompletableFuture<String> promise = new CompletableFuture<>();
      promise.whenComplete(resumeWaiter);
      return promise;
    });
    // Wait for execution to start
    waiter.await(1000);
    future.whenComplete(resumeWaiter);
    future.cancel(false);

    // Then
    Asserts.assertThrows(future::get, CancellationException.class);
    // Wait for the promise and future to complete with cancellation
    waiter.await(1000, 2);
  }

  /**
   * Asserts that FailsafeFuture cancellations are propagated to a CompletionStage in an async integration execution.
   */
  public void shouldPropagateCancellationToStageAsyncExecution() throws Throwable {
    // Given
    Policy<String> retryPolicy = new RetryPolicy<>();
    Waiter waiter = new Waiter();
    BiConsumer<String, Throwable> resumeWaiter = (r, t) -> {
      if (t instanceof CancellationException)
        waiter.resume();
    };

    // When
    CompletableFuture<String> future = Failsafe.with(retryPolicy).getStageAsyncExecution(exec -> {
      waiter.resume();
      CompletableFuture<String> promise = new CompletableFuture<>();
      promise.whenComplete(resumeWaiter);
      return promise;
    });
    // Wait for execution to start
    waiter.await(1000);
    future.whenComplete(resumeWaiter);
    future.cancel(true);

    // Then
    Asserts.assertThrows(future::get, CancellationException.class);
    // Wait for the promise and failsafeFuture to complete with cancellation
    waiter.await(1000, 2);
  }

  /**
   * Asserts that FailsafeFuture cancellations are propagated to the most recent ExecutionContext.
   */
  public void shouldPropagateCancellationToExecutionContext() throws Throwable {
    // Given
    Policy<Void> retryPolicy = new RetryPolicy<>();
    AtomicReference<ExecutionContext<Void>> ctxRef = new AtomicReference<>();
    Waiter waiter = new Waiter();

    // When
    Future<?> future = Failsafe.with(retryPolicy).runAsync(ctx -> {
      waiter.resume();
      ctxRef.set(ctx);
      if (ctx.getAttemptCount() < 3)
        throw new Exception();
      else
        Thread.sleep(1000);
    });
    waiter.await(1000);
    future.cancel(true);

    // Then
    assertTrue(ctxRef.get().isCancelled());
  }
}
