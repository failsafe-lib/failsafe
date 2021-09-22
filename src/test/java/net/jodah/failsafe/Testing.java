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

import net.jodah.failsafe.event.ExecutionCompletedEvent;
import net.jodah.failsafe.function.*;
import net.jodah.failsafe.internal.CircuitBreakerInternals;
import net.jodah.failsafe.internal.CircuitState;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

/**
 * Utilities to assist with testing.
 */
public class Testing extends Asserts {
  public static class ConnectException extends RuntimeException {
  }

  public interface Service {
    boolean connect();

    boolean disconnect();
  }

  public static class SyncExecutor implements Executor {
    @Override
    public void execute(Runnable command) {
      command.run();
    }
  }

  public static class Stats {
    // Common
    public volatile int executionCount;
    public volatile int failureCount;
    public volatile int successCount;

    // RetryPolicy
    public volatile int failedAttemptCount;
    public volatile int retryCount;
    public volatile int retryScheduledCount;
    public volatile int retriesExceededCount;
    public volatile int abortCount;

    // CircuitBreaker
    public volatile int openCount;
    public volatile int halfOpenCount;
    public volatile int closedCount;

    public void reset() {
      executionCount = 0;
      failureCount = 0;
      successCount = 0;
      failedAttemptCount = 0;
      retryCount = 0;
      retryScheduledCount = 0;
      retriesExceededCount = 0;
      abortCount = 0;
      openCount = 0;
      halfOpenCount = 0;
      closedCount = 0;
    }
  }

  public static Throwable getThrowable(CheckedRunnable runnable) {
    try {
      runnable.run();
    } catch (Throwable t) {
      return t;
    }

    return null;
  }

  public static <T> T ignoreExceptions(CheckedSupplier<T> supplier) {
    try {
      return supplier.get();
    } catch (Throwable t) {
      return null;
    }
  }

  public static void ignoreExceptions(CheckedRunnable runnable) {
    try {
      runnable.run();
    } catch (Throwable e) {
    }
  }

  public static Exception[] failures(int numFailures, Exception failure) {
    Exception[] failures = new Exception[numFailures];
    for (int i = 0; i < numFailures; i++)
      failures[i] = failure;
    return failures;
  }

  public static void runInThread(CheckedRunnable runnable) {
    new Thread(() -> ignoreExceptions(runnable)).start();
  }

  public static void runAsync(CheckedRunnable runnable) {
    CompletableFuture.runAsync(() -> {
      try {
        runnable.run();
      } catch (Throwable throwable) {
        throwable.printStackTrace();
      }
    });
  }

  @SuppressWarnings("unchecked")
  public static <T extends CircuitState> T stateFor(CircuitBreaker breaker) {
    Field stateField;
    try {
      stateField = CircuitBreaker.class.getDeclaredField("state");
      stateField.setAccessible(true);
      return ((AtomicReference<T>) stateField.get(breaker)).get();
    } catch (Exception e) {
      throw new IllegalStateException("Could not get circuit breaker state");
    }
  }

  public static void resetBreaker(CircuitBreaker<?> breaker) {
    breaker.close();
    CircuitState state = stateFor(breaker);
    state.getStats().reset();
  }

  /**
   * Returns a future that is completed with the {@code result} on the {@code executor}.
   */
  public static CompletableFuture<Object> futureResult(ScheduledExecutorService executor, Object result) {
    CompletableFuture<Object> future = new CompletableFuture<>();
    executor.schedule(() -> future.complete(result), 0, TimeUnit.MILLISECONDS);
    return future;
  }

  /**
   * Returns a future that is completed with the {@code exception} on the {@code executor}.
   */
  public static CompletableFuture<Object> futureException(ScheduledExecutorService executor, Exception exception) {
    CompletableFuture<Object> future = new CompletableFuture<>();
    executor.schedule(() -> future.completeExceptionally(exception), 0, TimeUnit.MILLISECONDS);
    return future;
  }

  public static void sleep(long duration) {
    try {
      Thread.sleep(duration);
    } catch (InterruptedException ignore) {
    }
  }

  /**
   * Unwraps and throws ExecutionException and FailsafeException causes.
   */
  public static void unwrapRunnableExceptions(CheckedRunnable runnable) {
    unwrapExceptions(() -> {
      runnable.run();
      return null;
    });
  }

  /**
   * Unwraps and throws ExecutionException and FailsafeException causes.
   */
  public static <T> T unwrapExceptions(CheckedSupplier<T> supplier) {
    try {
      return supplier.get();
    } catch (ExecutionException e) {
      sneakyThrow(e.getCause());
      return null;
    } catch (FailsafeException e) {
      sneakyThrow(e.getCause() == null ? e : e.getCause());
      return null;
    } catch (RuntimeException | Error e) {
      e.printStackTrace();
      throw e;
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  static volatile long lastTimestamp;

  public static void log(Object object, String msg, Object... args) {
    Class<?> clazz = object instanceof Class ? (Class<?>) object : object.getClass();
    log(clazz.getSimpleName() + " " + String.format(msg, args));
  }

  public static void log(Class<?> clazz, String msg) {
    log(clazz.getSimpleName() + " " + msg);
  }

  public static void log(String msg) {
    long currentTimestamp = System.currentTimeMillis();
    if (lastTimestamp + 80 < currentTimestamp)
      System.out.printf("%n%n");
    lastTimestamp = currentTimestamp;
    String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("H:mm:ss.SSS"));
    StringBuilder threadName = new StringBuilder(Thread.currentThread().getName());
    for (int i = threadName.length(); i < 35; i++)
      threadName.append(" ");
    System.out.println("[" + time + "] " + "[" + threadName + "] " + msg);
  }

  public static <T> RetryPolicy<T> withLogs(RetryPolicy<T> retryPolicy) {
    return withStatsAndLogs(retryPolicy, new Stats(), true);
  }

  public static <T> Timeout<T> withLogs(Timeout<T> timeout) {
    return withStatsAndLogs(timeout, new Stats(), true);
  }

  public static <T> CircuitBreaker<T> withLogs(CircuitBreaker<T> circuitBreaker) {
    return withStatsAndLogs(circuitBreaker, new Stats(), true);
  }

  public static <T extends FailurePolicy<T, R>, R> T withLogs(T policy) {
    return withStatsAndLogs(policy, new Stats(), true);
  }

  public static <T> RetryPolicy<T> withStats(RetryPolicy<T> retryPolicy, Stats stats) {
    return withStatsAndLogs(retryPolicy, stats, false);
  }

  public static <T> RetryPolicy<T> withStatsAndLogs(RetryPolicy<T> retryPolicy, Stats stats) {
    return withStatsAndLogs(retryPolicy, stats, true);
  }

  private static <T> RetryPolicy<T> withStatsAndLogs(RetryPolicy<T> retryPolicy, Stats stats, boolean withLogging) {
    retryPolicy.onFailedAttempt(e -> {
      stats.executionCount++;
      stats.failedAttemptCount++;
      if (withLogging)
        System.out.printf("RetryPolicy %s failed attempt [result: %s, failure: %s, attempts: %s, executions: %s]%n",
          retryPolicy.hashCode(), e.getLastResult(), e.getLastFailure(), e.getAttemptCount(), e.getExecutionCount());
    }).onRetry(e -> {
      stats.retryCount++;
      if (withLogging)
        System.out.printf("RetryPolicy %s retrying [result: %s, failure: %s]%n", retryPolicy.hashCode(),
          e.getLastResult(), e.getLastFailure());
    }).onRetryScheduled(e -> {
      stats.retryScheduledCount++;
      if (withLogging)
        System.out.printf("RetryPolicy %s scheduled [delay: %s ms]%n", retryPolicy.hashCode(), e.getDelay().toMillis());
    }).onRetriesExceeded(e -> {
      stats.retriesExceededCount++;
      if (withLogging)
        System.out.printf("RetryPolicy %s retries exceeded%n", retryPolicy.hashCode());
    }).onAbort(e -> {
      stats.abortCount++;
      if (withLogging)
        System.out.printf("RetryPolicy %s abort%n", retryPolicy.hashCode());
    });
    withStatsAndLogs((FailurePolicy) retryPolicy, stats, withLogging);
    return retryPolicy;
  }

  public static <T> Timeout<T> withStats(Timeout<T> timeout, Stats stats) {
    return withStatsAndLogs(timeout, stats, false);
  }

  public static <T> Timeout<T> withStatsAndLogs(Timeout<T> timeout, Stats stats) {
    return withStatsAndLogs(timeout, stats, true);
  }

  private static <T> Timeout<T> withStatsAndLogs(Timeout<T> timeout, Stats stats, boolean withLogging) {
    return timeout.onSuccess(e -> {
      stats.executionCount++;
      stats.successCount++;
      if (withLogging)
        System.out.printf("Timeout %s success policy executions=%s, successes=%s%n", timeout.hashCode(),
          stats.executionCount, stats.successCount);
    }).onFailure(e -> {
      stats.executionCount++;
      stats.failureCount++;
      if (withLogging)
        System.out.printf("Timeout %s exceeded policy executions=%s, failure=%s%n", timeout.hashCode(),
          stats.executionCount, stats.failureCount);
    });
  }

  public static <T> CircuitBreaker<T> withStats(CircuitBreaker<T> circuitBreaker, Stats stats) {
    return withStatsAndLogs(circuitBreaker, stats, false);
  }

  public static <T> CircuitBreaker<T> withStatsAndLogs(CircuitBreaker<T> circuitBreaker, Stats stats) {
    return withStatsAndLogs(circuitBreaker, stats, true);
  }

  private static <T> CircuitBreaker<T> withStatsAndLogs(CircuitBreaker<T> circuitBreaker, Stats stats,
    boolean withLogging) {
    circuitBreaker.onOpen(() -> {
      stats.openCount++;
      if (withLogging)
        System.out.println("CircuitBreaker opening");
    }).onHalfOpen(() -> {
      stats.halfOpenCount++;
      if (withLogging)
        System.out.println("CircuitBreaker half-opening");
    }).onClose(() -> {
      stats.closedCount++;
      if (withLogging)
        System.out.println("CircuitBreaker closing");
    });
    withStatsAndLogs((FailurePolicy) circuitBreaker, stats, withLogging);
    return circuitBreaker;
  }

  public static <T extends FailurePolicy<T, R>, R> T withStats(T policy, Stats stats) {
    return withStatsAndLogs(policy, stats, false);
  }

  public static <T extends FailurePolicy<T, R>, R> T withStatsAndLogs(T policy, Stats stats) {
    return withStatsAndLogs(policy, stats, true);
  }

  private static <T extends FailurePolicy<T, R>, R> T withStatsAndLogs(T policy, Stats stats, boolean withLogging) {
    return policy.onSuccess(e -> {
      stats.executionCount++;
      stats.successCount++;
      if (withLogging)
        System.out.printf("%s success [result: %s, attempts: %s, executions: %s]%n", policy.getClass().getSimpleName(),
          e.getResult(), e.getAttemptCount(), e.getExecutionCount());
    }).onFailure(e -> {
      stats.executionCount++;
      stats.failureCount++;
      if (withLogging)
        System.out.printf("%s failure [result: %s, failure: %s, attempts: %s, executions: %s]%n",
          policy.getClass().getSimpleName(), e.getResult(), e.getFailure(), e.getAttemptCount(), e.getExecutionCount());
    });
  }

  @SuppressWarnings("unchecked")
  public static <E extends Throwable> void sneakyThrow(Throwable e) throws E {
    throw (E) e;
  }

  public static Runnable uncheck(CheckedRunnable runnable) {
    return () -> {
      try {
        runnable.run();
      } catch (Throwable e) {
        throw new RuntimeException(e);
      }
    };
  }

  public static CircuitBreakerInternals<?> getInternals(CircuitBreaker<?> circuitBreaker) {
    try {
      Field internalsField = CircuitBreaker.class.getDeclaredField("internals");
      internalsField.setAccessible(true);
      return (CircuitBreakerInternals<?>) internalsField.get(circuitBreaker);
    } catch (Exception e) {
      return null;
    }
  }

  public static <T> void testRunAsyncSuccess(FailsafeExecutor<T> failsafe, ContextualRunnable<T> when,
    Consumer<ExecutionCompletedEvent<T>> then, T expectedResult) {
    ContextualSupplier<T, T> supplier = ctx -> {
      when.run(ctx);
      return null;
    };
    testGetInternal(null, failsafe, supplier, then, expectedResult, null, false);
  }

  public static <T> void testGetAsyncSuccess(FailsafeExecutor<T> failsafe, ContextualSupplier<T, T> when,
    Consumer<ExecutionCompletedEvent<T>> then, T expectedResult) {
    testGetInternal(null, failsafe, when, then, expectedResult, null, false);
  }

  @SafeVarargs
  public static <T> void testRunAsyncFailure(FailsafeExecutor<T> failsafe, ContextualRunnable<T> when,
    Consumer<ExecutionCompletedEvent<T>> then, Class<? extends Throwable>... expectedExceptions) {
    ContextualSupplier<T, T> supplier = ctx -> {
      when.run(ctx);
      return null;
    };
    testGetInternal(null, failsafe, supplier, then, null, expectedExceptions, false);
  }

  @SafeVarargs
  public static <T> void testGetAsyncFailure(FailsafeExecutor<T> failsafe, ContextualSupplier<T, T> when,
    Consumer<ExecutionCompletedEvent<T>> then, Class<? extends Throwable>... expectedExceptions) {
    testGetInternal(null, failsafe, when, then, null, expectedExceptions, false);
  }

  public static <T> void testRunSuccess(FailsafeExecutor<T> failsafe, ContextualRunnable<T> when,
    Consumer<ExecutionCompletedEvent<T>> then, T expectedResult) {
    ContextualSupplier<T, T> supplier = ctx -> {
      when.run(ctx);
      return null;
    };
    testGetInternal(null, failsafe, supplier, then, expectedResult, null, true);
  }

  public static <T> void testRunSuccess(Runnable given, FailsafeExecutor<T> failsafe, ContextualRunnable<T> when,
    Consumer<ExecutionCompletedEvent<T>> then, T expectedResult) {
    ContextualSupplier<T, T> supplier = ctx -> {
      when.run(ctx);
      return null;
    };
    testGetInternal(given, failsafe, supplier, then, expectedResult, null, true);
  }

  public static <T> void testGetSuccess(FailsafeExecutor<T> failsafe, ContextualSupplier<T, T> when,
    Consumer<ExecutionCompletedEvent<T>> then, T expectedResult) {
    testGetInternal(null, failsafe, when, then, expectedResult, null, true);
  }

  public static <T> void testGetSuccess(Runnable given, FailsafeExecutor<T> failsafe, ContextualSupplier<T, T> when,
    Consumer<ExecutionCompletedEvent<T>> then, T expectedResult) {
    testGetInternal(given, failsafe, when, then, expectedResult, null, true);
  }

  @SafeVarargs
  public static <T> void testRunFailure(FailsafeExecutor<T> failsafe, ContextualRunnable<T> when,
    Consumer<ExecutionCompletedEvent<T>> then, Class<? extends Throwable>... expectedExceptions) {
    testRunFailure(null, failsafe, when, then, expectedExceptions);
  }

  @SafeVarargs
  public static <T> void testRunFailure(Runnable given, FailsafeExecutor<T> failsafe, ContextualRunnable<T> when,
    Consumer<ExecutionCompletedEvent<T>> then, Class<? extends Throwable>... expectedExceptions) {
    ContextualSupplier<T, T> supplier = ctx -> {
      when.run(ctx);
      return null;
    };
    testGetInternal(given, failsafe, supplier, then, null, expectedExceptions, true);
  }

  @SafeVarargs
  public static <T> void testGetFailure(Runnable given, FailsafeExecutor<T> failsafe, ContextualSupplier<T, T> when,
    Consumer<ExecutionCompletedEvent<T>> then, Class<? extends Throwable>... expectedExceptions) {
    testGetInternal(given, failsafe, when, then, null, expectedExceptions, true);
  }

  /**
   * This method helps ensure behavior is identical between sync and async executions.
   * <p>
   * Does a .get and .getAsync against the failsafe, performing pre-test setup and post-test assertion checks. {@code
   * expectedResult} and {@code expectedExceptions} are verified against the returned result or thrown exceptions _and_
   * the ExecutionCompletedEvent's result and failure.
   */
  private static <T> void testGetInternal(Runnable given, FailsafeExecutor<T> failsafe, ContextualSupplier<T, T> when,
    Consumer<ExecutionCompletedEvent<T>> then, T expectedResult, Class<? extends Throwable>[] expectedExceptions,
    boolean testSync) {
    AtomicReference<ExecutionCompletedEvent<T>> completedEventRef = new AtomicReference<>();
    CheckedConsumer<ExecutionCompletedEvent<T>> setCompletedEventFn = completedEventRef::set;
    List<Class<? extends Throwable>> expected = new LinkedList<>();
    Class<? extends Throwable>[] expectedExInner = expectedExceptions == null ? new Class[] {} : expectedExceptions;
    Collections.addAll(expected, expectedExInner);

    Runnable postTestFn = () -> {
      ExecutionCompletedEvent<T> completedEvent = completedEventRef.get();
      if (expectedExInner.length > 0) {
        assertNull(completedEvent.getResult());
        Asserts.assertMatches(completedEvent.getFailure(), Arrays.asList(expectedExInner));
      } else {
        assertEquals(completedEvent.getResult(), expectedResult);
        assertNull(completedEvent.getFailure());
      }
      then.accept(completedEventRef.get());
    };

    // Sync test
    if (testSync) {
      System.out.println("\nRunning sync test");
      if (given != null)
        given.run();
      if (expectedExInner.length == 0) {
        T result = Testing.unwrapExceptions(() -> failsafe.onComplete(setCompletedEventFn).get(when));
        assertEquals(result, expectedResult);
      } else
        Asserts.assertThrows(() -> failsafe.onComplete(setCompletedEventFn).get(when), expectedExInner);
      postTestFn.run();
    }

    // Async test
    System.out.println("\nRunning async test");
    if (given != null)
      given.run();
    if (expectedExInner.length == 0) {
      T result = Testing.unwrapExceptions(() -> failsafe.onComplete(setCompletedEventFn).getAsync(when).get());
      assertEquals(result, expectedResult);
    } else {
      expected.add(0, ExecutionException.class);
      Asserts.assertThrows(() -> failsafe.onComplete(setCompletedEventFn).getAsync(when).get(), expected);
    }
    postTestFn.run();
  }

  public static <T> void testAsyncExecutionSuccess(FailsafeExecutor<T> failsafe, AsyncRunnable<T> when,
    Consumer<ExecutionCompletedEvent<T>> then, T expectedResult) {
    testAsyncExecutionInternal(failsafe, when, then, expectedResult);
  }

  @SafeVarargs
  public static <T> void testAsyncExecutionFailure(FailsafeExecutor<T> failsafe, AsyncRunnable<T> when,
    Consumer<ExecutionCompletedEvent<T>> then, Class<? extends Throwable>... expectedExceptions) {
    testAsyncExecutionInternal(failsafe, when, then, null, expectedExceptions);
  }

  @SafeVarargs
  private static <T> void testAsyncExecutionInternal(FailsafeExecutor<T> failsafe, AsyncRunnable<T> when,
    Consumer<ExecutionCompletedEvent<T>> then, T expectedResult, Class<? extends Throwable>... expectedExceptions) {

    AtomicReference<ExecutionCompletedEvent<T>> completedEventRef = new AtomicReference<>();
    CountDownLatch completedEventLatch = new CountDownLatch(1);
    CheckedConsumer<ExecutionCompletedEvent<T>> setCompletedEventFn = e -> {
      completedEventRef.set(e);
      completedEventLatch.countDown();
    };
    Runnable postTestFn = () -> {
      ignoreExceptions(() -> completedEventLatch.await(1, TimeUnit.SECONDS));
      ExecutionCompletedEvent<T> completedEvent = completedEventRef.get();
      if (expectedExceptions.length > 0)
        Asserts.assertMatches(completedEvent.getFailure(), Arrays.asList(expectedExceptions));
      else
        assertEquals(completedEvent.getResult(), expectedResult);
      then.accept(completedEventRef.get());
    };

    // Async test
    System.out.println("\nRunning async execution test");
    if (expectedExceptions.length == 0) {
      T result = Testing.unwrapExceptions(() -> failsafe.onComplete(setCompletedEventFn).getAsyncExecution(when).get());
      assertEquals(result, expectedResult);
    } else {
      List<Class<? extends Throwable>> expected = new LinkedList<>();
      Collections.addAll(expected, expectedExceptions);
      expected.add(0, ExecutionException.class);
      Asserts.assertThrows(() -> failsafe.onComplete(setCompletedEventFn).getAsyncExecution(when).get(), expected);
    }
    postTestFn.run();
  }
}
