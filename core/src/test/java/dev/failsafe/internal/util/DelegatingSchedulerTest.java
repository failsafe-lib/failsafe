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
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.Future;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;
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
    }, delay.toMillis(), MILLISECONDS);

    // Then
    waiter.await(1000);
    assertTrue(System.nanoTime() - startTime > delay.toNanos());
  }

  public void shouldWrapCheckedExceptions() {
    Asserts.assertThrows(() -> scheduler.schedule(() -> {
      throw new IOException();
    }, 1, MILLISECONDS).get(), ExecutionException.class, IOException.class);
  }

  public void shouldNotInterruptAlreadyDoneTask() throws Throwable {
    Future<?> future1 = scheduler.schedule(() -> null, 0, MILLISECONDS);
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
    }, 0, MILLISECONDS);
    waiter.await(1000);
    threadRef.get().interrupt();

    // Check for interrupt flag
    scheduler.schedule(() -> {
      waiter.assertFalse(Thread.currentThread().isInterrupted());
      waiter.resume();
      return null;
    }, 0, MILLISECONDS);
    waiter.await(1000);
  }


  @Test
  public void testInternalPool() throws TimeoutException, ExecutionException, InterruptedException{
    DelegatingScheduler ds = new DelegatingScheduler((byte) 8);// internal, not ForkJoin

    Waiter waiter = new Waiter();

    ScheduledFuture<?> sf = ds.schedule(()->{
      waiter.rethrow(new IOException("OK! testInternalPool"));
      return 42;
    }, 5, MILLISECONDS);

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
    assertTrue(sf1.getDelay(MILLISECONDS) > 2000);

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


  @Test
  public void testScheduleAndWork() throws TimeoutException, ExecutionException, InterruptedException{
    Waiter w = new Waiter();
    ScheduledFuture<?> sf1 = DelegatingScheduler.INSTANCE.schedule(()->{
      w.resume();// after ~ 1 sec
      Thread.sleep(5000);//hard work
      w.resume();
      return 42;
    }, 1, TimeUnit.SECONDS);

    ScheduledFuture<?> sf2 = DelegatingScheduler.INSTANCE.schedule(()->112, 3, TimeUnit.SECONDS);

    assertTrue(sf1.getDelay(MILLISECONDS) > 600);
    assertTrue(sf2.getDelay(MILLISECONDS) > 2600);
    assertEquals(-1, sf1.compareTo(sf2));// 1 sec < 3 sec
    assertFalse(sf1.isDone());
    assertFalse(sf2.isDone());

    w.await(1200, 1);// sf1 in normal executor

    assertEquals(-1, sf1.compareTo(sf2));// 1 sec < 3 sec
    assertEquals(sf1.getDelay(MILLISECONDS), 0);
    assertTrue(sf2.getDelay(MILLISECONDS) > 1600);
    assertFalse(sf1.isDone());
    assertFalse(sf2.isDone());

    w.await(5200, 1);// sf2 is done

    assertEquals(0, sf1.compareTo(sf2));// no more time info inside
    assertEquals(sf1.getDelay(MILLISECONDS), 0);
    assertEquals(sf2.getDelay(MILLISECONDS), 0);
    assertTrue(sf1.isDone());
    assertTrue(sf2.isDone());
    assertEquals(42, sf1.get());
    assertEquals(112, sf2.get());
  }

  @Test
  public void testScheduleAndCancel() throws TimeoutException, ExecutionException, InterruptedException{
    Waiter w = new Waiter();
    ScheduledFuture<?> sf1 = DelegatingScheduler.INSTANCE.schedule(()->{
      w.resume();// after ~ 1 sec
      Thread.sleep(5000);//hard work
      w.resume();
      return 42;
    }, 1, TimeUnit.SECONDS);

    ScheduledFuture<?> sf2 = DelegatingScheduler.INSTANCE.schedule(()->112, 3, TimeUnit.SECONDS);

    assertTrue(sf1.getDelay(MILLISECONDS) > 600);
    assertTrue(sf2.getDelay(MILLISECONDS) > 2600);
    assertEquals(-1, sf1.compareTo(sf2));// 1 sec < 3 sec
    assertFalse(sf1.isDone());
    assertFalse(sf2.isDone());

    w.await(1200, 1);// sf1 in normal executor

    assertEquals(-1, sf1.compareTo(sf2));// 1 sec < 3 sec
    assertEquals(sf1.getDelay(MILLISECONDS), 0);
    assertTrue(sf2.getDelay(MILLISECONDS) > 1600);
    assertFalse(sf1.isDone());
    assertFalse(sf2.isDone());

    sf1.cancel(true);
    sf2.cancel(true);

    assertEquals(-1, sf1.compareTo(sf2));// time info inside in sf2's delegate
    assertEquals(sf1.getDelay(MILLISECONDS), 0);
    assertTrue(sf2.getDelay(MILLISECONDS) > 1000);
    assertTrue(sf1.isDone());
    assertTrue(sf2.isDone());
    assertTrue(sf1.isCancelled());
    assertTrue(sf2.isCancelled());
    CancellationException c1 = expectThrows(CancellationException.class, sf1::get);
    CancellationException c2 = expectThrows(CancellationException.class, sf2::get);
    DelegatingScheduler.ScheduledCompletableFuture<?> scf1 = (DelegatingScheduler.ScheduledCompletableFuture<?>) sf1;
    DelegatingScheduler.ScheduledCompletableFuture<?> scf2 = (DelegatingScheduler.ScheduledCompletableFuture<?>) sf2;

    assertTrue(scf1.delegate instanceof ForkJoinTask);// was executing
    ForkJoinTask<?> task1 = (ForkJoinTask<?>) scf1.delegate;
    assertTrue(scf2.delegate instanceof RunnableScheduledFuture);// was in scheduler's delayQueue
    RunnableScheduledFuture<?> task2 = (RunnableScheduledFuture<?>) scf2.delegate;
    assertTrue(task1.isCompletedAbnormally());
    assertTrue(task1.isCancelled());
    assertTrue(task2.isCancelled());
  }

}