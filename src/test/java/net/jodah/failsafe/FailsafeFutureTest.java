/*
 * Copyright 2016 the original author or authors.
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
package net.jodah.failsafe;

import net.jodah.concurrentunit.Waiter;
import org.testng.annotations.Test;

import java.util.concurrent.*;

import static org.testng.Assert.*;

@Test
public class FailsafeFutureTest {
  ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);

  /**
   * Asserts that retries are stopped and completion handlers are called on cancel.
   */
  public void shouldCallOnCompleteWhenCancelled() throws Throwable {
    Waiter waiter = new Waiter();
    CompletableFuture<String> future = Failsafe.with(new RetryPolicy<String>()).with(executor).onComplete(e -> {
      waiter.assertNull(e.result);
      waiter.assertTrue(e.failure instanceof CancellationException);
      waiter.resume();
    }).getAsync(() -> {
      Thread.sleep(1000);
      throw new IllegalStateException();
    });

    // Note: We have to add whenComplete to the returned future separately, otherwise cancel will not be noticed by
    // Failsafe
    future.whenComplete((result, failure) -> {
      waiter.assertNull(result);
      waiter.assertTrue(failure instanceof CancellationException);
      waiter.resume();
    });

    future.cancel(true);
    waiter.await(1000, 2);
    future.complete("unxpected2");
    Asserts.assertThrows(future::get, CancellationException.class);
  }

  /**
   * Asserts that a completed future ignroes subsequent completion attempts.
   */
  public void shouldNotCancelCompletedFuture() throws Throwable {
    // Given
    CompletableFuture<String> future = Failsafe.with(new RetryPolicy<String>()).with(executor).getAsync(() -> "test");

    // When
    Thread.sleep(200);
    assertFalse(future.isCancelled());
    assertTrue(future.isDone());
    assertFalse(future.cancel(true));

    // Then
    assertFalse(future.isCancelled());
    assertTrue(future.isDone());
    assertEquals(future.get(), "test");
  }

  /**
   * Asserts that a cancelled future ignores subsequent completion attempts.
   */
  public void shouldNotCompleteCancelledFuture() {
    CompletableFuture<String> future = Failsafe.with(new RetryPolicy<String>()).with(executor).getAsync(() -> {
      Thread.sleep(1000);
      throw new IllegalStateException();
    });

    future.cancel(true);
    future.complete("unxpected2");
    Asserts.assertThrows(future::get, CancellationException.class);
  }
}
