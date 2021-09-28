package net.jodah.failsafe.internal.util;

import net.jodah.concurrentunit.Waiter;
import net.jodah.failsafe.testing.Asserts;
import net.jodah.failsafe.spi.Scheduler;
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
