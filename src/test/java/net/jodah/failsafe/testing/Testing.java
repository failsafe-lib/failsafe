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
package net.jodah.failsafe.testing;

import net.jodah.concurrentunit.Waiter;
import net.jodah.failsafe.CircuitBreaker;
import net.jodah.failsafe.FailsafeException;
import net.jodah.failsafe.FailsafeExecutor;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.event.EventListener;
import net.jodah.failsafe.event.ExecutionCompletedEvent;
import net.jodah.failsafe.function.*;
import net.jodah.failsafe.internal.CircuitBreakerImpl;
import net.jodah.failsafe.internal.CircuitState;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

/**
 * Utilities to assist with writing tests.
 */
public class Testing extends Logging {
  // Signals the test framework to call AsyncExecution.complete() for AsyncExecutions
  // Otherwise this is treated as a null expected result
  public static Object COMPLETE_SIGNAL = new Object();
  public RetryPolicy<Boolean> retryAlways = RetryPolicy.<Boolean>builder().withMaxRetries(-1).build();
  public RetryPolicy<Boolean> retryNever = RetryPolicy.<Boolean>builder().withMaxRetries(0).build();
  public RetryPolicy<Boolean> retryTwice = RetryPolicy.ofDefaults();

  public interface Then<R> {
    void accept(CompletableFuture<R> future, ExecutionCompletedEvent<R> event);
  }

  public interface Server {
    boolean connect();
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
      stateField = CircuitBreakerImpl.class.getDeclaredField("state");
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

  public static <T> void testRunSuccess(FailsafeExecutor<T> failsafe, ContextualRunnable<T> when, T expectedResult) {
    testRunSuccess(null, failsafe, when, null, expectedResult);
  }

  public static <T> void testRunSuccess(FailsafeExecutor<T> failsafe, ContextualRunnable<T> when, Then<T> then,
    T expectedResult) {
    testRunSuccess(null, failsafe, when, then, expectedResult);
  }

  public static <T> void testRunSuccess(Runnable given, FailsafeExecutor<T> failsafe, ContextualRunnable<T> when,
    Then<T> then, T expectedResult) {
    testRunSuccess(true, given, failsafe, when, then, expectedResult);
  }

  public static <T> void testRunSuccess(boolean runAsyncExecutions, Runnable given, FailsafeExecutor<T> failsafe,
    ContextualRunnable<T> when, Then<T> then, T expectedResult) {
    ContextualSupplier<T, T> whenSupplier = ctx -> {
      when.run(ctx);
      return null;
    };
    testGetInternal(runAsyncExecutions, given, failsafe, whenSupplier, then, expectedResult, null);
  }

  public static <T> void testGetSuccess(boolean runAsyncExecutions, FailsafeExecutor<T> failsafe,
    ContextualSupplier<T, T> when, T expectedResult) {
    testGetInternal(runAsyncExecutions, null, failsafe, when, null, expectedResult, null);
  }

  public static <T> void testGetSuccess(FailsafeExecutor<T> failsafe, ContextualSupplier<T, T> when, T expectedResult) {
    testGetInternal(true, null, failsafe, when, null, expectedResult, null);
  }

  public static <T> void testGetSuccess(FailsafeExecutor<T> failsafe, ContextualSupplier<T, T> when, Then<T> then,
    T expectedResult) {
    testGetInternal(true, null, failsafe, when, then, expectedResult, null);
  }

  public static <T> void testGetSuccess(Runnable given, FailsafeExecutor<T> failsafe, ContextualSupplier<T, T> when,
    T expectedResult) {
    testGetInternal(true, given, failsafe, when, null, expectedResult, null);
  }

  public static <T> void testGetSuccess(Runnable given, FailsafeExecutor<T> failsafe, ContextualSupplier<T, T> when,
    Then<T> then, T expectedResult) {
    testGetInternal(true, given, failsafe, when, then, expectedResult, null);
  }

  public static <T> void testGetSuccess(boolean runAsyncExecutions, Runnable given, FailsafeExecutor<T> failsafe,
    ContextualSupplier<T, T> when, Then<T> then, T expectedResult) {
    testGetInternal(runAsyncExecutions, given, failsafe, when, then, expectedResult, null);
  }

  @SafeVarargs
  public static <T> void testRunFailure(FailsafeExecutor<T> failsafe, ContextualRunnable<T> when,
    Class<? extends Throwable>... expectedExceptions) {
    testRunFailure(true, null, failsafe, when, null, expectedExceptions);
  }

  @SafeVarargs
  public static <T> void testRunFailure(FailsafeExecutor<T> failsafe, ContextualRunnable<T> when, Then<T> then,
    Class<? extends Throwable>... expectedExceptions) {
    testRunFailure(true, null, failsafe, when, then, expectedExceptions);
  }

  @SafeVarargs
  public static <T> void testRunFailure(boolean runAsyncExecutions, FailsafeExecutor<T> failsafe,
    ContextualRunnable<T> when, Then<T> then, Class<? extends Throwable>... expectedExceptions) {
    testRunFailure(runAsyncExecutions, null, failsafe, when, then, expectedExceptions);
  }

  @SafeVarargs
  public static <T> void testRunFailure(Runnable given, FailsafeExecutor<T> failsafe, ContextualRunnable<T> when,
    Class<? extends Throwable>... expectedExceptions) {
    testRunFailure(true, given, failsafe, when, null, expectedExceptions);
  }

  @SafeVarargs
  public static <T> void testRunFailure(Runnable given, FailsafeExecutor<T> failsafe, ContextualRunnable<T> when,
    Then<T> then, Class<? extends Throwable>... expectedExceptions) {
    testRunFailure(true, given, failsafe, when, then, expectedExceptions);
  }

  @SafeVarargs
  public static <T> void testRunFailure(boolean runAsyncExecutions, Runnable given, FailsafeExecutor<T> failsafe,
    ContextualRunnable<T> when, Then<T> then, Class<? extends Throwable>... expectedExceptions) {
    ContextualSupplier<T, T> whenSupplier = ctx -> {
      when.run(ctx);
      return null;
    };
    testGetInternal(runAsyncExecutions, given, failsafe, whenSupplier, then, null, expectedExceptions);
  }

  @SafeVarargs
  public static <T> void testGetFailure(FailsafeExecutor<T> failsafe, ContextualSupplier<T, T> when,
    Class<? extends Throwable>... expectedExceptions) {
    testGetInternal(true, null, failsafe, when, null, null, expectedExceptions);
  }

  @SafeVarargs
  public static <T> void testGetFailure(boolean runAsyncExecutions, FailsafeExecutor<T> failsafe,
    ContextualSupplier<T, T> when, Then<T> then, Class<? extends Throwable>... expectedExceptions) {
    testGetInternal(runAsyncExecutions, null, failsafe, when, then, null, expectedExceptions);
  }

  @SafeVarargs
  public static <T> void testGetFailure(FailsafeExecutor<T> failsafe, ContextualSupplier<T, T> when, Then<T> then,
    Class<? extends Throwable>... expectedExceptions) {
    testGetInternal(true, null, failsafe, when, then, null, expectedExceptions);
  }

  @SafeVarargs
  public static <T> void testGetFailure(Runnable given, FailsafeExecutor<T> failsafe, ContextualSupplier<T, T> when,
    Then<T> then, Class<? extends Throwable>... expectedExceptions) {
    testGetInternal(true, given, failsafe, when, then, null, expectedExceptions);
  }

  /**
   * This method helps ensure behavior is identical between sync and async executions.
   * <p>
   * Does a .get, .getAsync, .getAsyncExecution, and .getStageAsync against the failsafe, performing pre-test setup and
   * post-test assertion checks. {@code expectedResult} and {@code expectedExceptions} are verified against the returned
   * result or thrown exceptions _and_ the ExecutionCompletedEvent's result and failure.
   *
   * @param given The pre-execution setup to perform. Useful for resetting stats and mocks.
   * @param failsafe The FailsafeExecutor to execute with
   * @param when the Supplier to provide to the FailsafeExecutor
   * @param then post-test Assertions that are provided with Future (if any) and ExecutionCompletedEvent
   * @param expectedResult The expected result to assert against the actual result
   * @param expectedExceptions The expected exceptions to assert against the actual exceptions
   * @param runAsyncExecutions Indicates whether to run the AsyncExecution tests, including .getAsyncExecution. These
   * may be skipped for tests that involve timeouts, which don't work reliably against AsyncExecutions, since those may
   * return immediately.
   */
  private static <T> void testGetInternal(boolean runAsyncExecutions, Runnable given, FailsafeExecutor<T> failsafe,
    ContextualSupplier<T, T> when, Then<T> then, T expectedResult, Class<? extends Throwable>[] expectedExceptions) {

    AtomicReference<CompletableFuture<T>> futureRef = new AtomicReference<>();
    AtomicReference<ExecutionCompletedEvent<T>> completedEventRef = new AtomicReference<>();
    Waiter completionListenerWaiter = new Waiter();
    EventListener<ExecutionCompletedEvent<T>> setCompletedEventFn = e -> {
      completedEventRef.set(e);
      completionListenerWaiter.resume();
    };
    List<Class<? extends Throwable>> expected = new LinkedList<>();
    Class<? extends Throwable>[] expectedExInner = expectedExceptions == null ? new Class[] {} : expectedExceptions;
    Collections.addAll(expected, expectedExInner);

    // Assert results by treating COMPLETE_SIGNAL as a null expected result
    Consumer<T> resultAssertion = result -> {
      if (result == COMPLETE_SIGNAL)
        assertNull(expectedResult);
      else
        assertEquals(result, expectedResult);
    };

    Runnable postTestFn = () -> {
      ignoreExceptions(() -> completionListenerWaiter.await(5000));
      ExecutionCompletedEvent<T> completedEvent = completedEventRef.get();
      if (expectedExInner.length > 0) {
        assertNull(completedEvent.getResult());
        assertMatches(completedEvent.getFailure(), Arrays.asList(expectedExInner));
      } else {
        resultAssertion.accept(completedEvent.getResult());
        assertNull(completedEvent.getFailure());
      }
      if (then != null)
        then.accept(futureRef.get(), completedEvent);
    };

    // Run sync test
    System.out.println("\nRunning sync test");
    if (given != null)
      given.run();
    if (expectedExInner.length == 0) {
      resultAssertion.accept(unwrapExceptions(() -> failsafe.onComplete(setCompletedEventFn).get(when)));
    } else {
      assertThrows(() -> failsafe.onComplete(setCompletedEventFn).get(when), expectedExInner);
    }
    postTestFn.run();

    if (expectedExInner.length > 0)
      expected.add(0, ExecutionException.class);

    // Create async tester
    Consumer<Function<FailsafeExecutor<T>, CompletableFuture<T>>> asyncTester = test -> {
      if (given != null)
        given.run();
      if (expectedExInner.length == 0) {
        CompletableFuture<T> future = test.apply(failsafe.onComplete(setCompletedEventFn));
        futureRef.set(future);
        resultAssertion.accept(unwrapExceptions(future::get));
      } else {
        CompletableFuture<T> future = test.apply(failsafe.onComplete(setCompletedEventFn));
        futureRef.set(future);
        assertThrowsSup(future::get, expected);
      }
      postTestFn.run();
    };

    // Run async test
    System.out.println("\nRunning async test");
    asyncTester.accept(executor -> executor.getAsync(when));

    // Run async execution test
    if (runAsyncExecutions) {
      System.out.println("\nRunning async execution test");
      AsyncRunnable<T> asyncExecutionWhen = exec -> {
        // Run supplier in a different thread
        runInThread(() -> {
          try {
            T result = when.get(exec);
            if (result == COMPLETE_SIGNAL)
              exec.complete();
            else
              exec.recordResult(result);
          } catch (Throwable t) {
            exec.recordFailure(t);
          }
        });
      };
      asyncTester.accept(executor -> executor.getAsyncExecution(asyncExecutionWhen));
    }

    // Run stage async test
    System.out.println("\nRunning get stage async test");
    ContextualSupplier<T, ? extends CompletionStage<T>> stageAsyncWhen = ctx -> {
      CompletableFuture<T> promise = new CompletableFuture<>();
      // Run supplier in a different thread
      runInThread(() -> {
        try {
          promise.complete(when.get(ctx));
        } catch (Throwable t) {
          promise.completeExceptionally(t);
        }
      });
      return promise;
    };
    asyncTester.accept(executor -> executor.getStageAsync(stageAsyncWhen));
  }
}
