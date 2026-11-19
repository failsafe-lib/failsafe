/*
 * Copyright 2026 the original author or authors.
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
 * limitations under the License.
 */
package dev.failsafe;

import dev.failsafe.function.*;
import dev.failsafe.spi.AsyncExecutionInternal;
import dev.failsafe.spi.ExecutionResult;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.testng.Assert.*;

/**
 * Tests the internal function adapters.
 */
@Test
public class FunctionsTest {
  private static final String DESCRIPTION = "custom function";

  private static class RecordingScheduler extends ScheduledThreadPoolExecutor {
    final List<String> submittedDescriptions = Collections.synchronizedList(new ArrayList<>());

    RecordingScheduler() {
      super(1);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
      submittedDescriptions.add(callable.toString());
      return super.schedule(callable, delay, unit);
    }
  }

  public void shouldPropagateToStringForFunctionAdapters() {
    ContextualSupplier<Object, Object> contextualSupplier = new ContextualSupplier<Object, Object>() {
      @Override
      public Object get(ExecutionContext<Object> context) {
        return DESCRIPTION;
      }

      @Override
      public String toString() {
        return DESCRIPTION;
      }
    };
    CheckedRunnable checkedRunnable = new CheckedRunnable() {
      @Override
      public void run() {
      }

      @Override
      public String toString() {
        return DESCRIPTION;
      }
    };
    ContextualRunnable<Void> contextualRunnable = new ContextualRunnable<Void>() {
      @Override
      public void run(ExecutionContext<Void> context) {
      }

      @Override
      public String toString() {
        return DESCRIPTION;
      }
    };
    CheckedSupplier<String> checkedSupplier = new CheckedSupplier<String>() {
      @Override
      public String get() {
        return DESCRIPTION;
      }

      @Override
      public String toString() {
        return DESCRIPTION;
      }
    };
    CheckedConsumer<Object> checkedConsumer = new CheckedConsumer<Object>() {
      @Override
      public void accept(Object value) {
      }

      @Override
      public String toString() {
        return DESCRIPTION;
      }
    };
    ContextualSupplier<Object, CompletableFuture<Object>> stageSupplier =
      new ContextualSupplier<Object, CompletableFuture<Object>>() {
        @Override
        public CompletableFuture<Object> get(ExecutionContext<Object> context) {
          return CompletableFuture.completedFuture(DESCRIPTION);
        }

        @Override
        public String toString() {
          return DESCRIPTION;
        }
      };
    AsyncRunnable<Object> asyncRunnable = new AsyncRunnable<Object>() {
      @Override
      public void run(AsyncExecution<Object> execution) {
      }

      @Override
      public String toString() {
        return DESCRIPTION;
      }
    };

    assertEquals(Functions.toCtxSupplier(checkedRunnable).toString(), DESCRIPTION);
    assertEquals(Functions.toCtxSupplier(contextualRunnable).toString(), DESCRIPTION);
    assertEquals(Functions.toCtxSupplier(checkedSupplier).toString(), DESCRIPTION);
    assertEquals(Functions.toFn(checkedConsumer).toString(), DESCRIPTION);
    assertEquals(Functions.toFn(checkedRunnable).toString(), DESCRIPTION);
    assertEquals(Functions.toFn(checkedSupplier).toString(), DESCRIPTION);

    Function<SyncExecutionImpl<Object>, ExecutionResult<Object>> syncFunction =
      Functions.get(contextualSupplier, null);
    Function<AsyncExecutionInternal<Object>, CompletableFuture<ExecutionResult<Object>>> promiseFunction =
      Functions.getPromise(contextualSupplier, null);
    Function<AsyncExecutionInternal<Object>, CompletableFuture<ExecutionResult<Object>>> promiseExecutionFunction =
      Functions.getPromiseExecution(asyncRunnable, null);
    Function<AsyncExecutionInternal<Object>, CompletableFuture<ExecutionResult<Object>>> promiseOfStageFunction =
      Functions.getPromiseOfStage(stageSupplier, null, null);
    assertEquals(syncFunction.toString(), DESCRIPTION);
    assertEquals(promiseFunction.toString(), DESCRIPTION);
    assertEquals(promiseExecutionFunction.toString(), DESCRIPTION);
    assertEquals(promiseOfStageFunction.toString(), DESCRIPTION);

    assertEquals(Functions.toExecutionAware(promiseFunction).toString(), DESCRIPTION);
    assertEquals(Functions.toAsync(promiseFunction, null, null).toString(), DESCRIPTION);
  }

  public void shouldPropagateToStringToExecutorSubmittedRunnable() throws Throwable {
    AtomicInteger supplierCalls = new AtomicInteger();
    ContextualSupplier<Object, String> contextualSupplier = new ContextualSupplier<Object, String>() {
      @Override
      public String get(ExecutionContext<Object> context) {
        supplierCalls.incrementAndGet();
        return DESCRIPTION;
      }

      @Override
      public String toString() {
        return DESCRIPTION;
      }
    };
    AtomicInteger runnableCalls = new AtomicInteger();
    AsyncRunnable<Object> asyncRunnable = new AsyncRunnable<Object>() {
      @Override
      public void run(AsyncExecution<Object> execution) {
        runnableCalls.incrementAndGet();
      }

      @Override
      public String toString() {
        return DESCRIPTION;
      }
    };
    List<Runnable> submitted = new ArrayList<>();
    Executor executor = submitted::add;

    assertSame(Functions.withExecutor(contextualSupplier, null), contextualSupplier);
    assertSame(Functions.withExecutor(asyncRunnable, null), asyncRunnable);
    ContextualSupplier<Object, String> wrappedSupplier = Functions.withExecutor(contextualSupplier, executor);
    AsyncRunnable<Object> wrappedRunnable = Functions.withExecutor(asyncRunnable, executor);
    assertEquals(wrappedSupplier.toString(), DESCRIPTION);
    assertEquals(wrappedRunnable.toString(), DESCRIPTION);

    assertNull(wrappedSupplier.get(null));
    assertEquals(submitted.size(), 1);
    assertEquals(submitted.get(0).toString(), DESCRIPTION);
    submitted.get(0).run();
    assertEquals(supplierCalls.get(), 1);

    wrappedRunnable.run(null);
    assertEquals(submitted.size(), 2);
    assertEquals(submitted.get(1).toString(), DESCRIPTION);
    submitted.get(1).run();
    assertEquals(runnableCalls.get(), 1);
  }

  public void shouldUseCurrentDelegateDescription() {
    AtomicReference<String> description = new AtomicReference<>("before");
    CheckedRunnable runnable = new CheckedRunnable() {
      @Override
      public void run() {
      }

      @Override
      public String toString() {
        return description.get();
      }
    };
    ContextualSupplier<Void, Void> wrapped = Functions.toCtxSupplier(runnable);
    assertEquals(wrapped.toString(), "before");
    description.set("after");
    assertEquals(wrapped.toString(), "after");
  }

  public void shouldPreserveExecutorExceptionHandling() throws Throwable {
    Exception checked = new Exception("checked");
    ContextualSupplier<Object, Object> supplier = context -> {
      throw checked;
    };
    try {
      Functions.withExecutor(supplier, Runnable::run).get(null);
      fail("Expected FailsafeException");
    } catch (FailsafeException e) {
      assertSame(e.getCause(), checked);
    }

    RuntimeException runtime = new IllegalStateException("runtime");
    AsyncRunnable<Object> runnable = execution -> {
      throw runtime;
    };
    try {
      Functions.withExecutor(runnable, Runnable::run).run(null);
      fail("Expected the original runtime exception");
    } catch (RuntimeException e) {
      assertSame(e, runtime);
    }
  }

  public void shouldPropagateToStringToScheduledCallable() throws Exception {
    CheckedSupplier<String> supplier = new CheckedSupplier<String>() {
      @Override
      public String get() {
        return DESCRIPTION;
      }

      @Override
      public String toString() {
        return DESCRIPTION;
      }
    };
    RecordingScheduler scheduler = new RecordingScheduler();
    try {
      String result = Failsafe.with(RetryPolicy.ofDefaults()).with(scheduler).getAsync(supplier).get(5, TimeUnit.SECONDS);
      assertEquals(result, DESCRIPTION);
      assertEquals(scheduler.submittedDescriptions, Collections.singletonList(DESCRIPTION));
    } finally {
      scheduler.shutdownNow();
    }
  }

  public void shouldPropagateToStringToScheduledRetry() throws Exception {
    AtomicInteger attempts = new AtomicInteger();
    CheckedSupplier<String> supplier = new CheckedSupplier<String>() {
      @Override
      public String get() {
        if (attempts.incrementAndGet() == 1)
          throw new IllegalStateException("retry once");
        return DESCRIPTION;
      }

      @Override
      public String toString() {
        return DESCRIPTION;
      }
    };
    RecordingScheduler scheduler = new RecordingScheduler();
    try {
      String result = Failsafe.with(RetryPolicy.builder().withMaxRetries(1).build())
        .with(scheduler).getAsync(supplier).get(5, TimeUnit.SECONDS);
      assertEquals(result, DESCRIPTION);
      assertEquals(attempts.get(), 2);
      assertEquals(scheduler.submittedDescriptions, Arrays.asList(DESCRIPTION, DESCRIPTION));
    } finally {
      scheduler.shutdownNow();
    }
  }

  public void shouldPropagateToStringThroughPolicyComposition() throws Exception {
    assertDescriptionForPolicyOrder(true);
    assertDescriptionForPolicyOrder(false);
  }

  private void assertDescriptionForPolicyOrder(boolean retryFirst) throws Exception {
    AtomicInteger attempts = new AtomicInteger();
    CheckedSupplier<String> supplier = new CheckedSupplier<String>() {
      @Override
      public String get() {
        if (attempts.incrementAndGet() == 1)
          throw new IllegalStateException("retry once");
        return DESCRIPTION;
      }

      @Override
      public String toString() {
        return DESCRIPTION;
      }
    };
    RetryPolicy<String> retry = RetryPolicy.<String>builder().withMaxRetries(1).build();
    CircuitBreaker<String> breaker = CircuitBreaker.<String>builder().withFailureThreshold(3).build();
    FailsafeExecutor<String> executor =
      retryFirst ? Failsafe.with(retry, breaker) : Failsafe.with(breaker, retry);
    RecordingScheduler scheduler = new RecordingScheduler();
    try {
      assertEquals(executor.with(scheduler).getAsync(supplier).get(5, TimeUnit.SECONDS), DESCRIPTION);
      assertEquals(attempts.get(), 2);
      assertEquals(scheduler.submittedDescriptions, Arrays.asList(DESCRIPTION, DESCRIPTION));
    } finally {
      scheduler.shutdownNow();
    }
  }
}
