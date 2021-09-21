package net.jodah.failsafe.functional;

import net.jodah.concurrentunit.Waiter;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.Testing;
import net.jodah.failsafe.event.ExecutionCompletedEvent;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.*;

/**
 * Tests behavior when a Failsafe Future is manually cancelled.
 */
@Test
public class ManualCancellationTest extends Testing {
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
}
