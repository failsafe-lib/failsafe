package net.jodah.failsafe;

import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;

import net.jodah.failsafe.function.AsyncCallable;
import net.jodah.failsafe.function.AsyncRunnable;
import net.jodah.failsafe.function.CheckedBiConsumer;
import net.jodah.failsafe.function.CheckedBiFunction;
import net.jodah.failsafe.function.CheckedConsumer;
import net.jodah.failsafe.function.CheckedFunction;
import net.jodah.failsafe.function.CheckedRunnable;
import net.jodah.failsafe.function.ContextualCallable;
import net.jodah.failsafe.function.ContextualRunnable;
import net.jodah.failsafe.internal.util.Assert;

/**
 * Utilities and adapters for creating functions.
 * 
 * @author Jonathan Halterman
 */
final class Functions {
  static abstract class AsyncCallableWrapper<T> implements Callable<T> {
    protected AsyncExecution execution;

    void inject(AsyncExecution execution) {
      this.execution = execution;
    }
  }

  static abstract class ContextualCallableWrapper<T> implements Callable<T> {
    protected ExecutionContext context;

    void inject(ExecutionContext context) {
      this.context = context;
    }
  }

  static <T> AsyncCallableWrapper<T> asyncOf(final AsyncCallable<T> callable) {
    Assert.notNull(callable, "callable");
    return new AsyncCallableWrapper<T>() {
      @Override
      public synchronized T call() throws Exception {
        try {
          execution.before();
          T result = callable.call(execution);
          return result;
        } catch (Exception e) {
          execution.completeOrRetry(null, e);
          return null;
        }
      }
    };
  }

  static <T> AsyncCallableWrapper<T> asyncOf(final AsyncRunnable runnable) {
    Assert.notNull(runnable, "runnable");
    return new AsyncCallableWrapper<T>() {
      @Override
      public synchronized T call() throws Exception {
        try {
          execution.before();
          runnable.run(execution);
        } catch (Exception e) {
          execution.completeOrRetry(null, e);
        }

        return null;
      }
    };
  }

  static <T> AsyncCallableWrapper<T> asyncOf(final Callable<T> callable) {
    Assert.notNull(callable, "callable");
    return new AsyncCallableWrapper<T>() {
      @Override
      public T call() throws Exception {
        try {
          execution.before();
          T result = callable.call();
          execution.completeOrRetry(result, null);
          return result;
        } catch (Exception e) {
          execution.completeOrRetry(null, e);
          return null;
        }
      }
    };
  }

  static <T> AsyncCallableWrapper<T> asyncOf(final CheckedRunnable runnable) {
    Assert.notNull(runnable, "runnable");
    return new AsyncCallableWrapper<T>() {
      @Override
      public T call() throws Exception {
        try {
          execution.before();
          runnable.run();
          execution.completeOrRetry(null, null);
        } catch (Exception e) {
          execution.completeOrRetry(null, e);
        }

        return null;
      }
    };
  }

  static <T> AsyncCallableWrapper<T> asyncOf(final ContextualCallable<T> callable) {
    Assert.notNull(callable, "callable");
    return new AsyncCallableWrapper<T>() {
      @Override
      public T call() throws Exception {
        try {
          execution.before();
          T result = callable.call(execution);
          execution.completeOrRetry(result, null);
          return result;
        } catch (Exception e) {
          execution.completeOrRetry(null, e);
          return null;
        }
      }
    };
  }

  static <T> AsyncCallableWrapper<T> asyncOf(final ContextualRunnable runnable) {
    Assert.notNull(runnable, "runnable");
    return new AsyncCallableWrapper<T>() {
      @Override
      public T call() throws Exception {
        try {
          execution.before();
          runnable.run(execution);
          execution.completeOrRetry(null, null);
        } catch (Exception e) {
          execution.completeOrRetry(null, e);
        }

        return null;
      }
    };
  }

  static <T> AsyncCallableWrapper<T> asyncOfFuture(
      final AsyncCallable<java.util.concurrent.CompletableFuture<T>> callable) {
    Assert.notNull(callable, "callable");
    return new AsyncCallableWrapper<T>() {
      Semaphore asyncFutureLock = new Semaphore(1);

      @Override
      public T call() throws Exception {
        try {
          execution.before();
          asyncFutureLock.acquire();
          callable.call(execution).whenComplete(new java.util.function.BiConsumer<T, Throwable>() {
            @Override
            public void accept(T innerResult, Throwable failure) {
              try {
                if (failure != null)
                  execution.completeOrRetry(innerResult,
                      failure instanceof java.util.concurrent.CompletionException ? failure.getCause() : failure);
              } finally {
                asyncFutureLock.release();
              }
            }
          });
        } catch (Exception e) {
          try {
            execution.completeOrRetry(null, e);
          } finally {
            asyncFutureLock.release();
          }
        }

        return null;
      }
    };
  }

  static <T> AsyncCallableWrapper<T> asyncOfFuture(final Callable<java.util.concurrent.CompletableFuture<T>> callable) {
    Assert.notNull(callable, "callable");
    return new AsyncCallableWrapper<T>() {
      @Override
      public T call() throws Exception {
        try {
          execution.before();
          callable.call().whenComplete(new java.util.function.BiConsumer<T, Throwable>() {
            @Override
            public void accept(T innerResult, Throwable failure) {
              // Unwrap CompletionException cause
              if (failure != null && failure instanceof java.util.concurrent.CompletionException)
                failure = failure.getCause();
              execution.completeOrRetry(innerResult, failure);
            }
          });
        } catch (Exception e) {
          execution.completeOrRetry(null, e);
        }

        return null;
      }
    };
  }

  static <T> AsyncCallableWrapper<T> asyncOfFuture(
      final ContextualCallable<java.util.concurrent.CompletableFuture<T>> callable) {
    Assert.notNull(callable, "callable");
    return new AsyncCallableWrapper<T>() {
      @Override
      public T call() throws Exception {
        try {
          execution.before();
          callable.call(execution).whenComplete(new java.util.function.BiConsumer<T, Throwable>() {
            @Override
            public void accept(T innerResult, Throwable failure) {
              // Unwrap CompletionException cause
              if (failure != null && failure instanceof java.util.concurrent.CompletionException)
                failure = failure.getCause();
              execution.completeOrRetry(innerResult, failure);
            }
          });
        } catch (Exception e) {
          execution.completeOrRetry(null, e);
        }

        return null;
      }
    };
  }

  static <T> Callable<T> callableOf(final CheckedRunnable runnable) {
    Assert.notNull(runnable, "runnable");
    return new Callable<T>() {
      @Override
      public T call() throws Exception {
        runnable.run();
        return null;
      }
    };
  }

  static <T> Callable<T> callableOf(final ContextualCallable<T> callable) {
    Assert.notNull(callable, "callable");
    return new ContextualCallableWrapper<T>() {
      @Override
      public T call() throws Exception {
        T result = callable.call(context);
        return result;
      }
    };
  }

  static <T> Callable<T> callableOf(final ContextualRunnable runnable) {
    Assert.notNull(runnable, "runnable");
    return new ContextualCallableWrapper<T>() {
      @Override
      public T call() throws Exception {
        runnable.run(context);
        return null;
      }
    };
  }

  static <T, U, R> CheckedBiFunction<T, U, R> fnOf(final Callable<R> callable) {
    return new CheckedBiFunction<T, U, R>() {
      @Override
      public R apply(T t, U u) throws Exception {
        return callable.call();
      }
    };
  }

  static <T, U, R> CheckedBiFunction<T, U, R> fnOf(final CheckedBiConsumer<T, U> consumer) {
    return new CheckedBiFunction<T, U, R>() {
      @Override
      public R apply(T t, U u) throws Exception {
        consumer.accept(t, u);
        return null;
      }
    };
  }

  static <T, U, R> CheckedBiFunction<T, U, R> fnOf(final CheckedConsumer<U> consumer) {
    return new CheckedBiFunction<T, U, R>() {
      @Override
      public R apply(T t, U u) throws Exception {
        consumer.accept(u);
        return null;
      }
    };
  }

  static <T, U, R> CheckedBiFunction<T, U, R> fnOf(final CheckedFunction<U, R> function) {
    return new CheckedBiFunction<T, U, R>() {
      @Override
      public R apply(T t, U u) throws Exception {
        return function.apply(u);
      }
    };
  }

  static <T, U, R> CheckedBiFunction<T, U, R> fnOf(final CheckedRunnable runnable) {
    return new CheckedBiFunction<T, U, R>() {
      @Override
      public R apply(T t, U u) throws Exception {
        runnable.run();
        return null;
      }
    };
  }

  static <T, U, R> CheckedBiFunction<T, U, R> fnOf(final R result) {
    return new CheckedBiFunction<T, U, R>() {
      @Override
      public R apply(T t, U u) throws Exception {
        return result;
      }
    };
  }
}
