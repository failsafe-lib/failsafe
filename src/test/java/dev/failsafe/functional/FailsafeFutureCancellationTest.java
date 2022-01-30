/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package dev.failsafe.functional;

import dev.failsafe.*;
import dev.failsafe.function.ContextualRunnable;
import dev.failsafe.testing.Testing;
import net.jodah.concurrentunit.Waiter;
import dev.failsafe.event.ExecutionCompletedEvent;
import dev.failsafe.function.ContextualSupplier;
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

  private void assertCancel(FailsafeExecutor<Void> executor, ContextualRunnable<Void> runnable) throws Throwable {
    // Given
    CompletableFuture<Void> future = executor.onComplete(e -> {
      waiter.assertNull(e.getResult());
      waiter.assertTrue(e.getFailure() instanceof CancellationException);
      waiter.resume();
    }).runAsync(runnable);

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

  public void shouldCancelAsyncRetriesWithPendingDelay() throws Throwable {
    RetryPolicy<Void> retryPolicy = RetryPolicy.<Void>builder().withDelay(Duration.ofMinutes(1)).build();
    assertCancel(Failsafe.with(retryPolicy), ctx -> {
      throw new IllegalStateException();
    });
  }

  public void shouldCancelAsyncRetriesWithBlockedExecution() throws Throwable {
    assertCancel(Failsafe.with(RetryPolicy.ofDefaults()), ctx -> {
      try {
        waiter.assertFalse(ctx.isCancelled());
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        waiter.assertTrue(ctx.isCancelled());
        throw e;
      }
    });
  }

  public void shouldCancelAsyncTimeoutWithBlockedExecution() throws Throwable {
    assertCancel(Failsafe.with(Timeout.of(Duration.ofMinutes(1))), ctx -> {
      try {
        waiter.assertFalse(ctx.isCancelled());
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        waiter.assertTrue(ctx.isCancelled());
        throw e;
      }
    });
  }

  public void shouldCancelAsyncRateLimiterWaitingOnPermit() throws Throwable {
    RateLimiter<Void> limiter = RateLimiter.<Void>smoothBuilder(1, Duration.ofSeconds(1))
      .withMaxWaitTime(Duration.ofMinutes(1))
      .build();
    limiter.tryAcquirePermit(); // All permits should be used now

    assertCancel(Failsafe.with(limiter), ctx -> {
      fail("Execution should be cancelled during preExecute");
    });
  }

  /**
   * Asserts that cancelling a FailsafeFuture causes both retry policies to stop.
   */
  public void testCancelWithNestedRetries() throws Throwable {
    // Given
    Stats outerRetryStats = new Stats();
    Stats innerRetryStats = new Stats();
    RetryPolicy<Object> outerRetryPolicy = withStatsAndLogs(RetryPolicy.builder(), outerRetryStats).build();
    RetryPolicy<Object> innerRetryPolicy = withStatsAndLogs(
      RetryPolicy.builder().withMaxRetries(3).withDelay(Duration.ofMillis(100)), innerRetryStats).build();
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
    Policy<Void> retryPolicy = withLogs(RetryPolicy.<Void>builder()).build();
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
