package net.jodah.failsafe.functional;

import net.jodah.failsafe.*;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.testng.Assert.assertFalse;

/**
 * Tests scenarios against a small threadpool where executions could be temporarily blocked.
 */
@Test
public class BlockedExecutionTest {
  /**
   * Asserts that a scheduled execution that is blocked on a threadpool is properly cancelled when a timeout occurs.
   */
  @Test
  public void shouldCancelScheduledExecutionOnTimeout() throws Throwable {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Timeout<Boolean> timeout = Timeout.of(Duration.ofMillis(100));
    AtomicBoolean supplierCalled = new AtomicBoolean();
    executor.submit(Testing.uncheck(() -> Thread.sleep(300)));

    Future<Boolean> future = Failsafe.with(timeout).with(executor).getAsync(() -> {
      supplierCalled.set(true);
      return false;
    });

    Asserts.assertThrows(() -> future.get(1000, TimeUnit.MILLISECONDS), ExecutionException.class,
      TimeoutExceededException.class);
    Thread.sleep(300);
    assertFalse(supplierCalled.get());
  }

  /**
   * Asserts that a scheduled retry that is blocked on a threadpool is properly cancelled when a timeout occurs.
   */
  public void shouldCancelScheduledRetryOnTimeout() {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Timeout<Boolean> timeout = Timeout.of(Duration.ofMillis(100));
    RetryPolicy<Boolean> rp = new RetryPolicy<Boolean>().withDelay(Duration.ofMillis(1000)).handleResult(false);

    Future<Boolean> future = Failsafe.with(timeout).compose(rp).with(executor).getAsync(() -> {
      // Tie up single thread immediately after execution, before the retry is scheduled
      executor.submit(Testing.uncheck(() -> Thread.sleep(1000)));
      return false;
    });

    Asserts.assertThrows(() -> future.get(500, TimeUnit.MILLISECONDS), ExecutionException.class,
      TimeoutExceededException.class);
  }

  /**
   * Asserts that a scheduled fallback that is blocked on a threadpool is properly cancelled when a timeout occurs.
   */
  public void shouldCancelScheduledFallbackOnTimeout() {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Timeout<Boolean> timeout = Timeout.of(Duration.ofMillis(100));
    AtomicBoolean fallbackCalled = new AtomicBoolean();
    Fallback<Boolean> fallback = Fallback.ofAsync(() -> {
      fallbackCalled.set(true);
      return true;
    }).handleResult(false);

    Future<Boolean> future = Failsafe.with(timeout).compose(fallback).with(executor).getAsync(() -> {
      // Tie up single thread immediately after execution, before the fallback is scheduled
      executor.submit(Testing.uncheck(() -> Thread.sleep(1000)));
      return false;
    });

    Asserts.assertThrows(() -> future.get(500, TimeUnit.MILLISECONDS), ExecutionException.class,
      TimeoutExceededException.class);
    assertFalse(fallbackCalled.get());
  }

  /**
   * Asserts that a scheduled fallback that is blocked on a threadpool is properly cancelled when the outer future is
   * cancelled.
   */
  public void shouldCancelScheduledFallbackOnCancel() throws Throwable {
    AtomicBoolean fallbackCalled = new AtomicBoolean();
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Fallback<Boolean> fallback = Fallback.ofAsync(() -> {
      fallbackCalled.set(true);
      return true;
    }).handleResult(false);

    Future<Boolean> future = Failsafe.with(fallback).with(executor).getAsync(() -> {
      executor.submit(Testing.uncheck(() -> Thread.sleep(300)));
      return false;
    });

    Thread.sleep(100);
    future.cancel(false);
    Asserts.assertThrows(future::get, CancellationException.class);
    Thread.sleep(300);
    assertFalse(fallbackCalled.get());
  }
}
