package dev.failsafe.issues;

import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import net.jodah.concurrentunit.Waiter;
import org.testng.annotations.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.*;

@Test
public class Issue311Test {
  public void failsafeFail() throws Throwable {
    AtomicInteger counter = new AtomicInteger(0);
    Executor executor = Executors.newSingleThreadExecutor();
    Failsafe.with(RetryPolicy.builder().handle(RuntimeException.class).withMaxAttempts(2).build())
      .with(executor)
      .runAsync(() -> {
        if (counter.incrementAndGet() == 1)
          throw new RuntimeException();
      })
      .get();
    assertEquals(counter.get(), 2);
  }

  public void testNullCompletionStage() throws Throwable {
    assertNull(Failsafe.none().getStageAsync(() -> {
      return null;
    }).get());
  }

  public void testRunAsyncWithThreadLocalInExecutor() throws Throwable {
    ThreadLocal<Boolean> threadLocal = new ThreadLocal<>();
    Executor executor = runnable -> {
      threadLocal.set(true);
      runnable.run();
    };
    Failsafe.none().with(executor).runAsync(() -> {
      assertTrue(threadLocal.get());
    }).get();
  }

  public void testGetStageAsyncWithThreadLocalInExecutor() throws Throwable {
    ThreadLocal<Boolean> threadLocal = new ThreadLocal<>();
    Executor executor = runnable -> {
      threadLocal.set(true);
      runnable.run();
    };
    assertNull(Failsafe.none().with(executor).getStageAsync(() -> {
      assertTrue(threadLocal.get());
      return CompletableFuture.completedFuture("ignored");
    }).get());
  }
}
