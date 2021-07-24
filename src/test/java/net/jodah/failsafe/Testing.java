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
import net.jodah.failsafe.function.AsyncRunnable;
import net.jodah.failsafe.function.CheckedConsumer;
import net.jodah.failsafe.function.CheckedRunnable;
import net.jodah.failsafe.function.CheckedSupplier;
import net.jodah.failsafe.internal.CircuitBreakerInternals;
import net.jodah.failsafe.internal.CircuitState;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Utilities to to assist with testing.
 */
public class Testing {
  public static class ConnectException extends RuntimeException {
  }

  public static class Stats {
    // Common
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

  public interface Service {
    boolean connect();

    boolean disconnect();
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
      throw e;
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  public static void log(Object category, String msg, Object... args) {
    String clazz = category instanceof Class ?
      ((Class<?>) category).getSimpleName() :
      category.getClass().getSimpleName();
    String entry = String.format("[%s] %s - %s", Thread.currentThread().getName(), clazz, String.format(msg, args));
    System.out.println(entry);
  }

  public static <T> RetryPolicy<T> withLogs(RetryPolicy<T> retryPolicy) {
    return withStats(retryPolicy, new Stats(), true);
  }

  public static <T> CircuitBreaker<T> withLogs(CircuitBreaker<T> circuitBreaker) {
    return withStats(circuitBreaker, new Stats(), true);
  }

  public static <T extends FailurePolicy<T, R>, R> T withLogs(T policy) {
    return withStats(policy, new Stats(), true);
  }

  public static <T> RetryPolicy<T> withStats(RetryPolicy<T> retryPolicy, Stats stats, boolean withLogging) {
    retryPolicy.onFailedAttempt(e -> {
      stats.failedAttemptCount++;
      if (withLogging)
        System.out.printf("RetryPolicy failed an attempt with attempts: %s, executions: %s%n", e.getAttemptCount(),
          e.getExecutionCount());
    }).onRetry(e -> {
      stats.retryCount++;
      if (withLogging)
        System.out.println("RetryPolicy retrying");
    }).onRetryScheduled(e -> {
      stats.retryScheduledCount++;
      if (withLogging)
        System.out.printf("RetryPolicy scheduled with delay: %s ms%n", e.getDelay().toMillis());
    }).onRetriesExceeded(e -> {
      stats.retriesExceededCount++;
      if (withLogging)
        System.out.println("RetryPolicy retries exceeded");
    }).onAbort(e -> {
      stats.abortCount++;
      if (withLogging)
        System.out.println("RetryPolicy abort");
    });
    withStats((FailurePolicy) retryPolicy, stats, withLogging);
    return retryPolicy;
  }

  public static <T> CircuitBreaker<T> withStats(CircuitBreaker<T> circuitBreaker, Stats stats, boolean withLogging) {
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
    withStats((FailurePolicy) circuitBreaker, stats, withLogging);
    return circuitBreaker;
  }

  public static <T extends FailurePolicy<T, R>, R> T withStats(T policy, Stats stats, boolean withLogging) {
    return policy.onSuccess(e -> {
      stats.successCount++;
      if (withLogging)
        System.out.printf("%s success with attempts: %s, executions: %s%n", policy.getClass().getSimpleName(),
          e.getAttemptCount(), e.getExecutionCount());
    }).onFailure(e -> {
      stats.failureCount++;
      if (withLogging)
        System.out.printf("%s failure with attempts: %s, executions: %s%n", policy.getClass().getSimpleName(),
          e.getAttemptCount(), e.getExecutionCount());
    });
  }

  @SuppressWarnings("unchecked")
  public static <E extends Throwable> void sneakyThrow(Throwable e) throws E {
    throw (E) e;
  }

  public static CircuitBreakerInternals getInternals(CircuitBreaker circuitBreaker) {
    try {
      Field internalsField = CircuitBreaker.class.getDeclaredField("internals");
      internalsField.setAccessible(true);
      return (CircuitBreakerInternals) internalsField.get(circuitBreaker);
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Does a .run and .runAsync against the failsafe, performing pre-test setup and post-test assertion checks. {@code
   * expectedExceptions} are verified against thrown exceptions _and_ the ExecutionCompletedEvent's failure.
   * <p>
   * This method helps ensure behavior is identical between sync and async executions.
   */
  @SafeVarargs
  public static void testSyncAndAsync(FailsafeExecutor<?> failsafe, Runnable given, CheckedRunnable when,
    Consumer<ExecutionCompletedEvent<?>> then, Class<? extends Throwable>... expectedExceptions) {
    AtomicReference<ExecutionCompletedEvent<?>> completedEventRef = new AtomicReference<>();
    CheckedConsumer<ExecutionCompletedEvent<?>> setCompletedEventFn = completedEventRef::set;
    List<Class<? extends Throwable>> expected = new LinkedList<>();
    Collections.addAll(expected, expectedExceptions);

    Runnable postTestFn = () -> {
      if (expectedExceptions.length > 0)
        Asserts.assertMatches(completedEventRef.get().getFailure(), Arrays.asList(expectedExceptions));
      then.accept(completedEventRef.get());
    };

    // Sync test
    System.out.println("\nRunning sync test");
    given.run();
    if (expectedExceptions.length == 0)
      Testing.unwrapRunnableExceptions(() -> failsafe.onComplete(setCompletedEventFn::accept).run(when));
    else
      Asserts.assertThrows(() -> failsafe.onComplete(setCompletedEventFn::accept).run(when), expectedExceptions);
    postTestFn.run();

    // Async test
    System.out.println("\nRunning async test");
    given.run();
    if (expectedExceptions.length == 0) {
      Testing.unwrapExceptions(() -> failsafe.onComplete(setCompletedEventFn::accept).runAsync(when).get());
    } else {
      expected.add(0, ExecutionException.class);
      Asserts.assertThrows(() -> failsafe.onComplete(setCompletedEventFn::accept).runAsync(when).get(), expected);
    }
    postTestFn.run();
  }

  @SafeVarargs
  public static void testAsyncExecution(FailsafeExecutor<?> failsafe, AsyncRunnable when,
    Consumer<ExecutionCompletedEvent<?>> then, Class<? extends Throwable>... expectedExceptions) {
    AtomicReference<ExecutionCompletedEvent<?>> completedEventRef = new AtomicReference<>();
    CheckedConsumer<ExecutionCompletedEvent<?>> setCompletedEventFn = completedEventRef::set;
    Runnable postTestFn = () -> {
      if (expectedExceptions.length > 0)
        Asserts.assertMatches(completedEventRef.get().getFailure(), Arrays.asList(expectedExceptions));
      then.accept(completedEventRef.get());
    };

    // Async test
    System.out.println("\nRunning async execution test");
    if (expectedExceptions.length == 0) {
      Testing.unwrapExceptions(() -> failsafe.onComplete(setCompletedEventFn::accept).runAsyncExecution(when).get());
    } else {
      List<Class<? extends Throwable>> expected = new LinkedList<>();
      Collections.addAll(expected, expectedExceptions);
      expected.add(0, ExecutionException.class);
      Asserts.assertThrows(() -> failsafe.onComplete(setCompletedEventFn::accept).runAsyncExecution(when).get(),
        expected);
    }
    postTestFn.run();
  }
}
