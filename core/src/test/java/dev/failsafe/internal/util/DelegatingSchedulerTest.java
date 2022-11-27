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

import dev.failsafe.spi.Scheduler;
import dev.failsafe.testing.Asserts;
import net.jodah.concurrentunit.Waiter;
import org.testng.annotations.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

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


  @Test
  public void testInternalPool() throws TimeoutException, ExecutionException, InterruptedException{
    DelegatingScheduler ds = new DelegatingScheduler((byte) 8);// internal, not ForkJoin

    Waiter waiter = new Waiter();

    ScheduledFuture<?> sf = ds.schedule(()->{
      waiter.rethrow(new IOException("OK! testInternalPool"));
      return 42;
    }, 5, TimeUnit.MILLISECONDS);

    try {
      waiter.await(1000);
      fail();
    } catch (Throwable e) {
      assertEquals(e.toString(), "java.io.IOException: OK! testInternalPool");
    }
    assertTrue(sf.isDone());

    try {
      sf.get();
      fail();
    } catch (ExecutionException e) {
      assertEquals(e.toString(), "java.util.concurrent.ExecutionException: java.io.IOException: OK! testInternalPool");
    }
  }

  @Test
  public void testExternalScheduler() throws TimeoutException, ExecutionException, InterruptedException{
    ScheduledThreadPoolExecutor stpe = new ScheduledThreadPoolExecutor(1);
    DelegatingScheduler ds = new DelegatingScheduler(stpe, true);

    Waiter waiter = new Waiter();

    ScheduledFuture<?> sf1 = ds.schedule(()->{
      waiter.rethrow(new IOException("OK! fail 1"));
      return 42;
    }, 3, TimeUnit.SECONDS);
    ScheduledFuture<?> sf2 = ds.schedule(()->{
      waiter.rethrow(new IOException("OK! fail 2 fast"));
      return 42;
    }, 1, TimeUnit.SECONDS);
    assertEquals(1, sf1.compareTo(sf2));
    assertEquals(0, sf1.compareTo(sf1));
    assertTrue(sf1.getDelay(TimeUnit.MILLISECONDS) > 2000);

    try {
      waiter.await(3200);
      fail();
    } catch (Throwable e) {
      assertEquals(e.toString(), "java.io.IOException: OK! fail 2 fast");
    }
    assertTrue(sf2.isDone());
    Thread.sleep(2500);//3-1 = 2 for slow sf1
    assertTrue(sf1.isDone());

    try {
      sf1.get();
      fail();
    } catch (ExecutionException e) {
      assertEquals(e.toString(), "java.util.concurrent.ExecutionException: java.io.IOException: OK! fail 1");
    }
    try {
      sf2.get();
      fail();
    } catch (ExecutionException e) {
      assertEquals(e.toString(), "java.util.concurrent.ExecutionException: java.io.IOException: OK! fail 2 fast");
    }
    assertEquals(stpe.shutdownNow().size(), 0);

    assertEquals(-1, sf2.compareTo(sf1));
  }
}