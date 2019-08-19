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

import net.jodah.failsafe.function.CheckedRunnable;
import net.jodah.failsafe.function.CheckedSupplier;
import net.jodah.failsafe.internal.CircuitBreakerInternals;
import net.jodah.failsafe.internal.CircuitState;

import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class Testing {
  public static class ConnectException extends RuntimeException {
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
    } catch (Exception e) {
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

  @SuppressWarnings("unchecked")
  public static <T extends CircuitState> T stateFor(CircuitBreaker breaker) {
    Field stateField;
    try {
      stateField = CircuitBreaker.class.getDeclaredField("state");
      stateField.setAccessible(true);
      return (T) ((AtomicReference<T>) stateField.get(breaker)).get();
    } catch (Exception e) {
      return null;
    }
  }

  public static CompletableFuture<Object> futureResult(ScheduledExecutorService executor, Object result) {
    CompletableFuture<Object> future = new CompletableFuture<>();
    executor.schedule(() -> future.complete(result), 0, TimeUnit.MILLISECONDS);
    return future;
  }

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
    } catch (Exception e) {
      throw (RuntimeException) e;
    }
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
}
