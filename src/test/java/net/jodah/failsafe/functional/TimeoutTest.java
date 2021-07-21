package net.jodah.failsafe.functional;

import net.jodah.failsafe.*;
import net.jodah.failsafe.event.ExecutionCompletedEvent;
import net.jodah.failsafe.function.CheckedConsumer;
import net.jodah.failsafe.function.CheckedRunnable;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.testng.Assert.assertEquals;

/**
 * Tests various Timeout policy scenarios.
 */
@Test
public class TimeoutTest {
  /**
   * Tests that an inner timeout does not prevent outer retries from being performed when the inner Supplier is
   * blocked.
   */
  public void testTimeoutThenRetryWithBlockedSupplier() {
    AtomicInteger timeoutCounter = new AtomicInteger();
    AtomicInteger retryPolicyCounter = new AtomicInteger();
    Timeout<Object> timeout = Timeout.of(Duration.ofMillis(1)).onFailure(e -> {
      timeoutCounter.incrementAndGet();
      System.out.println("Timed out");
    });
    RetryPolicy<Object> retryPolicy = new RetryPolicy<>().withMaxRetries(2).onRetry(e -> {
      retryPolicyCounter.incrementAndGet();
      System.out.println("Retrying");
    });

    Runnable test = () -> testSyncAndAsync(Failsafe.with(retryPolicy, timeout), () -> {
      timeoutCounter.set(0);
      retryPolicyCounter.set(0);
    }, () -> {
      Thread.sleep(100);
      throw new Exception();
    }, e -> {
      assertEquals(e.getAttemptCount(), 3);
      assertEquals(timeoutCounter.get(), 3);
      assertEquals(retryPolicyCounter.get(), 2);
    }, TimeoutExceededException.class);

    // Test without interrupt
    timeout.withInterrupt(false);
    test.run();

    // Test with interrupt
    timeout.withInterrupt(true);
    test.run();
  }

  /**
   * Tests that an inner timeout does not prevent outer retries from being performed when a retry is pending. In this
   * test, the timeout has no effect on the outer retry policy.
   */
  public void testTimeoutThenRetryWithPendingRetry() {
    AtomicInteger executionCounter = new AtomicInteger();
    AtomicInteger timeoutSuccessCounter = new AtomicInteger();
    AtomicInteger timeoutFailureCounter = new AtomicInteger();
    Timeout<Object> timeout = Timeout.of(Duration.ofMillis(10)).onSuccess(e -> {
      timeoutSuccessCounter.incrementAndGet();
      System.out.println("Timed out");
    }).onFailure(e -> {
      timeoutFailureCounter.incrementAndGet();
    });
    RetryPolicy<Object> retryPolicy = new RetryPolicy<>().withDelay(Duration.ofMillis(100)).onRetry(e -> {
      System.out.println("Retrying");
    });

    Runnable test = () -> testSyncAndAsync(Failsafe.with(retryPolicy, timeout), () -> {
      executionCounter.set(0);
      timeoutSuccessCounter.set(0);
    }, () -> {
      executionCounter.incrementAndGet();
      throw new IllegalStateException();
    }, e -> {
      assertEquals(e.getAttemptCount(), 3);
      assertEquals(executionCounter.get(), 3);
      assertEquals(timeoutSuccessCounter.get(), 3);
      assertEquals(timeoutFailureCounter.get(), 0);
    }, IllegalStateException.class);

    // Test without interrupt
    timeout.withInterrupt(false);
    test.run();

    // Test with interrupt
    timeout.withInterrupt(true);
    test.run();
  }

  /**
   * Tests that an outer timeout will cancel inner retries when the inner Supplier is blocked. The flow should be:
   * <p>
   * <br>Execution that blocks
   * <br>Timeout
   */
  public void testRetryThenTimeoutWithBlockedSupplier() {
    AtomicInteger timeoutCounter = new AtomicInteger();
    AtomicInteger executionCounter = new AtomicInteger();
    Timeout<Object> timeout = Timeout.of(Duration.ofMillis(1)).onFailure(e -> {
      timeoutCounter.incrementAndGet();
      System.out.println("Timed out");
    });
    RetryPolicy<Object> retryPolicy = new RetryPolicy<>().onRetry(e -> {
      System.out.println("Retrying");
    });

    Runnable test = () -> testSyncAndAsync(Failsafe.with(timeout, retryPolicy), () -> {
      executionCounter.set(0);
      timeoutCounter.set(0);
    }, () -> {
      System.out.println("Executing");
      executionCounter.incrementAndGet();
      Thread.sleep(100);
      throw new Exception();
    }, e -> {
      assertEquals(e.getAttemptCount(), 1);
      assertEquals(executionCounter.get(), 1);
      assertEquals(timeoutCounter.get(), 1);
    }, TimeoutExceededException.class);

    // Test without interrupt
    timeout.withInterrupt(false);
    test.run();

    // Test with interrupt
    timeout.withInterrupt(true);
    test.run();
  }

  /**
   * Tests that an outer timeout will cancel inner retries when an inner retry is pending. The flow should be:
   * <p>
   * <br>Execution
   * <br>Retry sleep/scheduled that blocks
   * <br>Timeout
   */
  public void testRetryThenTimeoutWithPendingRetry() {
    AtomicInteger executionCounter = new AtomicInteger();
    AtomicInteger timeoutCounter = new AtomicInteger();
    AtomicInteger failedAttemptCounter = new AtomicInteger();
    Timeout<Object> timeout = Timeout.of(Duration.ofMillis(100)).onFailure(e -> {
      System.out.println("Timed out");
      timeoutCounter.incrementAndGet();
    });
    RetryPolicy<Object> retryPolicy = new RetryPolicy<>().withDelay(Duration.ofMillis(1000)).onFailedAttempt(e -> {
      failedAttemptCounter.incrementAndGet();
    }).onRetry(e -> {
      System.out.println("Retrying");
    });

    Runnable test = () -> testSyncAndAsync(Failsafe.with(timeout, retryPolicy), () -> {
      executionCounter.set(0);
      timeoutCounter.set(0);
      failedAttemptCounter.set(0);
    }, () -> {
      System.out.println("Executing");
      executionCounter.incrementAndGet();
      throw new Exception();
    }, e -> {
      assertEquals(e.getAttemptCount(), 1);
      assertEquals(executionCounter.get(), 1);
      assertEquals(timeoutCounter.get(), 1);
      assertEquals(failedAttemptCounter.get(), 1);
    }, TimeoutExceededException.class);

    // Test without interrupt
    timeout.withInterrupt(false);
    test.run();

    // Test with interrupt
    timeout.withInterrupt(true);
    test.run();
  }

  /**
   * Tests an inner timeout that fires while the supplier is blocked.
   */
  public void testTimeoutThenFallbackWithBlockedSupplier() {
    AtomicInteger timeoutCounter = new AtomicInteger();
    AtomicInteger fallbackCounter = new AtomicInteger();
    Timeout<Object> timeout = Timeout.of(Duration.ofMillis(1)).onFailure(e -> {
      System.out.println("Timed out");
      timeoutCounter.incrementAndGet();
    });
    Fallback<Object> fallback = Fallback.of(() -> {
      System.out.println("Falling back");
      fallbackCounter.incrementAndGet();
      throw new IllegalStateException();
    });

    Runnable test = () -> testSyncAndAsync(Failsafe.with(fallback, timeout), () -> {
      timeoutCounter.set(0);
      fallbackCounter.set(0);
    }, () -> {
      System.out.println("Executing");
      Thread.sleep(100);
      throw new Exception();
    }, e -> {
      assertEquals(e.getAttemptCount(), 1);
      assertEquals(timeoutCounter.get(), 1);
      assertEquals(fallbackCounter.get(), 1);
    }, IllegalStateException.class);

    // Test without interrupt
    timeout.withInterrupt(false);
    test.run();

    // Test with interrupt
    timeout.withInterrupt(true);
    test.run();
  }

  /**
   * Tests that an inner timeout will not interrupt an outer fallback. The inner timeout is never triggered since the
   * supplier completes immediately.
   */
  public void testTimeoutThenFallback() {
    AtomicInteger timeoutCounter = new AtomicInteger();
    AtomicInteger fallbackCounter = new AtomicInteger();
    Timeout<Object> timeout = Timeout.of(Duration.ofMillis(100)).onFailure(e -> {
      System.out.println("Timed out");
      timeoutCounter.incrementAndGet();
    });
    Fallback<Object> fallback = Fallback.of(() -> {
      System.out.println("Falling back");
      fallbackCounter.incrementAndGet();
      throw new IllegalStateException();
    });

    Runnable test = () -> testSyncAndAsync(Failsafe.with(fallback, timeout), () -> {
      fallbackCounter.set(0);
    }, () -> {
      System.out.println("Executing");
      throw new Exception();
    }, e -> {
      assertEquals(e.getAttemptCount(), 1);
      assertEquals(timeoutCounter.get(), 0);
      assertEquals(fallbackCounter.get(), 1);
    }, IllegalStateException.class);

    // Test without interrupt
    timeout.withInterrupt(false);
    test.run();

    // Test with interrupt
    timeout.withInterrupt(true);
    test.run();
  }

  /**
   * Tests that an outer timeout will interrupt an inner supplier that is blocked, skipping the inner fallback.
   */
  public void testFallbackThenTimeoutWithBlockedSupplier() {
    AtomicInteger timeoutCounter = new AtomicInteger();
    AtomicInteger fallbackCounter = new AtomicInteger();
    Timeout<Object> timeout = Timeout.of(Duration.ofMillis(1)).onFailure(e -> {
      System.out.println("Timed out");
      timeoutCounter.incrementAndGet();
    });
    Fallback<Object> fallback = Fallback.of(() -> {
      System.out.println("Falling back");
      fallbackCounter.incrementAndGet();
      throw new IllegalStateException();
    });

    Runnable test = () -> testSyncAndAsync(Failsafe.with(timeout, fallback), () -> {
      timeoutCounter.set(0);
      fallbackCounter.set(0);
    }, () -> {
      System.out.println("Executing");
      Thread.sleep(100);
      throw new Exception();
    }, e -> {
      assertEquals(e.getAttemptCount(), 1);
      assertEquals(timeoutCounter.get(), 1);
      assertEquals(fallbackCounter.get(), 0);
    }, TimeoutExceededException.class);

    // Test without interrupt
    timeout.withInterrupt(false);
    test.run();

    // Test with interrupt
    timeout.withInterrupt(true);
    test.run();
  }

  /**
   * Tests that an outer timeout will interrupt an inner fallback that is blocked.
   */
  public void testFallbackThenTimeoutWithBlockedFallback() {
    AtomicInteger timeoutCounter = new AtomicInteger();
    AtomicInteger fallbackCounter = new AtomicInteger();
    Timeout<Object> timeout = Timeout.of(Duration.ofMillis(1)).onFailure(e -> {
      System.out.println("Timed out");
      timeoutCounter.incrementAndGet();
    });
    Fallback<Object> fallback = Fallback.of(() -> {
      System.out.println("Falling back");
      fallbackCounter.incrementAndGet();
      Thread.sleep(100);
      throw new IllegalStateException();
    });

    Runnable test = () -> testSyncAndAsync(Failsafe.with(timeout, fallback), () -> {
      timeoutCounter.set(0);
      fallbackCounter.set(0);
    }, () -> {
      System.out.println("Executing");
      throw new Exception();
    }, e -> {
      assertEquals(e.getAttemptCount(), 1);
      assertEquals(timeoutCounter.get(), 1);
      assertEquals(fallbackCounter.get(), 1);
    }, TimeoutExceededException.class);

    // Test without interrupt
    timeout.withInterrupt(false);
    test.run();

    // Test with interrupt
    timeout.withInterrupt(true);
    test.run();
  }

  /**
   * Does a .run and .runAsync against the failsafe, performing pre-test setup and post-test assertion checks. {@code
   * expectedExceptions} are verified against thrown exceptions _and_ the ExecutionCompletedEvent's failure.
   */
  @SafeVarargs
  private final void testSyncAndAsync(FailsafeExecutor<?> failsafe, Runnable preTestSetup, CheckedRunnable test,
    Consumer<ExecutionCompletedEvent<?>> postTestAssertions, Class<? extends Throwable>... expectedExceptions) {
    AtomicReference<ExecutionCompletedEvent<?>> completedEventRef = new AtomicReference<>();
    CheckedConsumer<ExecutionCompletedEvent<?>> setCompletedEventFn = completedEventRef::set;
    List<Class<? extends Throwable>> expected = new LinkedList<>();
    Collections.addAll(expected, expectedExceptions);

    Runnable postTestFn = () -> {
      if (expectedExceptions.length > 0)
        Asserts.assertMatches(completedEventRef.get().getFailure(), Arrays.asList(expectedExceptions));
      postTestAssertions.accept(completedEventRef.get());
    };

    // Sync test
    System.out.println("\nRunning sync test");
    preTestSetup.run();
    if (expectedExceptions.length == 0)
      Testing.unwrapRunnableExceptions(() -> failsafe.onComplete(setCompletedEventFn::accept).run(test));
    else
      Asserts.assertThrows(() -> failsafe.onComplete(setCompletedEventFn::accept).run(test), expectedExceptions);
    postTestFn.run();

    // Async test
    System.out.println("\nRunning async test");
    preTestSetup.run();
    if (expectedExceptions.length == 0) {
      Testing.unwrapExceptions(() -> failsafe.onComplete(setCompletedEventFn::accept).runAsync(test).get());
    } else {
      expected.add(0, ExecutionException.class);
      Asserts.assertThrows(() -> failsafe.onComplete(setCompletedEventFn::accept).runAsync(test).get(), expected);
    }
    postTestFn.run();
  }
}
