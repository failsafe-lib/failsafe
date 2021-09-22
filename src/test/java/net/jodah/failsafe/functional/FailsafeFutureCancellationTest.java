package net.jodah.failsafe.functional;

import net.jodah.concurrentunit.Waiter;
import net.jodah.failsafe.*;
import net.jodah.failsafe.event.ExecutionCompletedEvent;
import net.jodah.failsafe.function.AsyncSupplier;
import net.jodah.failsafe.function.CheckedSupplier;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.concurrent.*;
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
    Thread.sleep(1000);
  }

  /**
   * Asserts that FailsafeFuture cancellations are propagated to a CompletionStage.
   */
  public void shouldPropagateCancellationToStage() throws Throwable {
    // Given
    Policy<String> retryPolicy = new RetryPolicy<>();
    Waiter cancellationWaiter = new Waiter();
    BiConsumer<String, Throwable> waiterResumer = (r, t) -> {
      if (t instanceof CancellationException)
        cancellationWaiter.resume();
    };
    CheckedSupplier<CompletionStage<String>> doWork = () -> {
      CompletableFuture<String> promise = new CompletableFuture<>();
      promise.whenComplete(waiterResumer);

      // Simulate asynchronous work
      runInThread(() -> Thread.sleep(1000));
      return promise;
    };

    // When
    CompletableFuture<String> failsafeFuture = Failsafe.with(retryPolicy).getStageAsync(doWork);
    failsafeFuture.whenComplete(waiterResumer);
    failsafeFuture.cancel(true);

    // Then
    Asserts.assertThrows(failsafeFuture::get, CancellationException.class);
    // Wait for the promise and failsafeFuture to complete with cancellation
    cancellationWaiter.await(1000, 2);
  }

  /**
   * Asserts that FailsafeFuture cancellations are propagated to a CompletionStage in an async integration execution.
   */
  public void shouldPropagateCancellationToStageAsyncExecution() throws Throwable {
    // Given
    Policy<String> retryPolicy = new RetryPolicy<>();
    Waiter cancellationWaiter = new Waiter();
    BiConsumer<String, Throwable> waiterResumer = (r, t) -> {
      if (t instanceof CancellationException)
        cancellationWaiter.resume();
    };
    AsyncSupplier<String, CompletionStage<String>> doWork = exec -> {
      CompletableFuture<String> promise = new CompletableFuture<>();
      promise.whenComplete(waiterResumer);

      // Simulate asynchronous work
      runInThread(() -> Thread.sleep(1000));
      return promise;
    };

    // When
    CompletableFuture<String> failsafeFuture = Failsafe.with(retryPolicy).getStageAsyncExecution(doWork);
    failsafeFuture.whenComplete(waiterResumer);
    failsafeFuture.cancel(true);

    // Then
    Asserts.assertThrows(failsafeFuture::get, CancellationException.class);
    // Wait for the promise and failsafeFuture to complete with cancellation
    cancellationWaiter.await(1000, 2);
  }
}
