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
import dev.failsafe.testing.Asserts;
import dev.failsafe.testing.Testing;
import net.jodah.concurrentunit.Waiter;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.testng.Assert.assertFalse;

/**
 * Tests scenarios against a small threadpool where executions could be temporarily blocked.
 */
@Test
public class BlockedExecutionTest extends Testing {
  /**
   * Asserts that a scheduled execution that is blocked on a threadpool is properly cancelled when a timeout occurs.
   */
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
    executor.shutdownNow();
  }

  /**
   * Asserts that a scheduled retry that is blocked on a threadpool is properly cancelled when a timeout occurs.
   */
  public void shouldCancelScheduledRetryOnTimeout() {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Timeout<Boolean> timeout = Timeout.of(Duration.ofMillis(100));
    RetryPolicy<Boolean> rp = RetryPolicy.<Boolean>builder()
      .withDelay(Duration.ofMillis(1000))
      .handleResult(false)
      .build();

    Future<Boolean> future = Failsafe.with(timeout).compose(rp).with(executor).getAsync(() -> {
      // Tie up single thread immediately after execution, before the retry is scheduled
      executor.submit(Testing.uncheck(() -> Thread.sleep(1000)));
      return false;
    });

    Asserts.assertThrows(() -> future.get(500, TimeUnit.MILLISECONDS), ExecutionException.class,
      TimeoutExceededException.class);
    executor.shutdownNow();
  }

  /**
   * Asserts that a scheduled fallback that is blocked on a threadpool is properly cancelled when a timeout occurs.
   */
  public void shouldCancelScheduledFallbackOnTimeout() {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Timeout<Boolean> timeout = Timeout.of(Duration.ofMillis(100));
    AtomicBoolean fallbackCalled = new AtomicBoolean();
    Fallback<Boolean> fallback = Fallback.builder(() -> {
      fallbackCalled.set(true);
      return true;
    }).handleResult(false).withAsync().build();

    Future<Boolean> future = Failsafe.with(timeout).compose(fallback).with(executor).getAsync(() -> {
      // Tie up single thread immediately after execution, before the fallback is scheduled
      executor.submit(Testing.uncheck(() -> Thread.sleep(1000)));
      return false;
    });

    Asserts.assertThrows(() -> future.get(500, TimeUnit.MILLISECONDS), ExecutionException.class,
      TimeoutExceededException.class);
    assertFalse(fallbackCalled.get());
    executor.shutdownNow();
  }

  /**
   * Asserts that a scheduled fallback that is blocked on a threadpool is properly cancelled when the outer future is
   * cancelled.
   */
  public void shouldCancelScheduledFallbackOnCancel() throws Throwable {
    AtomicBoolean fallbackCalled = new AtomicBoolean();
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Fallback<Boolean> fallback = Fallback.builder(() -> {
      fallbackCalled.set(true);
      return true;
    }).handleResult(false).withAsync().build();

    Future<Boolean> future = Failsafe.with(fallback).with(executor).getAsync(() -> {
      executor.submit(Testing.uncheck(() -> Thread.sleep(300)));
      return false;
    });

    Thread.sleep(100);
    future.cancel(false);
    Asserts.assertThrows(future::get, CancellationException.class);
    Thread.sleep(300);
    assertFalse(fallbackCalled.get());
    executor.shutdownNow();
  }

  /**
   * Asserts that start times are not populated in execution events for an execution that times out while blocked on a
   * thread pool, and never starts.
   */
  public void shouldNotPopulateStartTime() throws Throwable {
    Waiter waiter = new Waiter();
    Timeout<Object> timeout = Timeout.builder(Duration.ofMillis(50)).withInterrupt().onFailure(e -> {
      waiter.assertTrue(!e.getStartTime().isPresent());
    }).build();
    ExecutorService executor = Executors.newFixedThreadPool(1);
    executor.execute(uncheck(() -> {
      Thread.sleep(500);
    }));

    Failsafe.with(timeout).with(executor).onComplete(e -> {
      waiter.assertTrue(!e.getStartTime().isPresent());
      waiter.resume();
    }).runAsync(() -> {
      waiter.fail("Execution should not start due to timeout");
    });

    waiter.await(1, TimeUnit.SECONDS);
    executor.shutdownNow();
  }
}
