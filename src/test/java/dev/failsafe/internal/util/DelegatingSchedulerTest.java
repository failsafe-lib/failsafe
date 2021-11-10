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
package dev.failsafe.internal.util;

import net.jodah.concurrentunit.Waiter;
import dev.failsafe.testing.Asserts;
import dev.failsafe.spi.Scheduler;
import org.testng.annotations.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

@Test
public class DelegatingSchedulerTest {
  Scheduler scheduler = DelegatingScheduler.INSTANCE;

  public void shouldSchedule() throws Throwable {
    // Given
    Duration delay = Duration.ofMillis(200);
    Waiter waiter = new Waiter();
    long startTime = System.nanoTime();

    // When
    scheduler.schedule(() -> {
      waiter.resume();
      return null;
    }, delay.toMillis(), TimeUnit.MILLISECONDS);

    // Then
    waiter.await(1000);
    assertTrue(System.nanoTime() - startTime > delay.toNanos());
  }

  public void shouldWrapCheckedExceptions() {
    Asserts.assertThrows(() -> scheduler.schedule(() -> {
      throw new IOException();
    }, 1, TimeUnit.MILLISECONDS).get(), ExecutionException.class, IOException.class);
  }

  public void shouldNotInterruptAlreadyDoneTask() throws Throwable {
    Future<?> future1 = scheduler.schedule(() -> null, 0, TimeUnit.MILLISECONDS);
    Thread.sleep(100);
    assertFalse(future1.cancel(true));
  }

  /**
   * Asserts that ForkJoinPool clears interrupt flags.
   */
  public void shouldClearInterruptFlagInForkJoinPoolThreads() throws Throwable {
    Scheduler scheduler = new DelegatingScheduler(new ForkJoinPool(1));
    AtomicReference<Thread> threadRef = new AtomicReference<>();
    Waiter waiter = new Waiter();

    // Create interruptable execution
    scheduler.schedule(() -> {
      threadRef.set(Thread.currentThread());
      waiter.resume();
      Thread.sleep(10000);
      return null;
    }, 0, TimeUnit.MILLISECONDS);
    waiter.await(1000);
    threadRef.get().interrupt();

    // Check for interrupt flag
    scheduler.schedule(() -> {
      waiter.assertFalse(Thread.currentThread().isInterrupted());
      waiter.resume();
      return null;
    }, 0, TimeUnit.MILLISECONDS);
    waiter.await(1000);
  }
}
