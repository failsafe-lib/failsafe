package net.jodah.recurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import java.util.function.BiConsumer;

import net.jodah.recurrent.internal.util.Assert;

/**
 * An asynchronous callable with a reference to execution information.
 * 
 * @author Jonathan Halterman
 * @param <T> result type
 */
abstract class AsyncContextualCallable<T> implements Callable<T> {
  protected AsyncExecution execution;

  static <T> AsyncContextualCallable<T> of(final AsyncCallable<T> callable) {
    Assert.notNull(callable, "callable");
    return new AsyncContextualCallable<T>() {
      @Override
      public synchronized T call() throws Exception {
        try {
          execution.prepare();
          T result = callable.call(execution);
          return result;
        } catch (Exception e) {
          execution.completeOrRetry(null, e);
          return null;
        }
      }
    };
  }

  static <T> AsyncContextualCallable<T> of(final AsyncRunnable runnable) {
    Assert.notNull(runnable, "runnable");
    return new AsyncContextualCallable<T>() {
      @Override
      public synchronized T call() throws Exception {
        try {
          execution.prepare();
          runnable.run(execution);
        } catch (Exception e) {
          execution.completeOrRetry(null, e);
        }

        return null;
      }
    };
  }

  static <T> AsyncContextualCallable<T> of(final Callable<T> callable) {
    Assert.notNull(callable, "callable");
    return new AsyncContextualCallable<T>() {
      @Override
      public T call() throws Exception {
        try {
          execution.prepare();
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

  static <T> AsyncContextualCallable<T> of(final CheckedRunnable runnable) {
    Assert.notNull(runnable, "runnable");
    return new AsyncContextualCallable<T>() {
      @Override
      public T call() throws Exception {
        try {
          execution.prepare();
          runnable.run();
          execution.completeOrRetry(null, null);
        } catch (Exception e) {
          execution.completeOrRetry(null, e);
        }

        return null;
      }
    };
  }

  static <T> AsyncContextualCallable<T> of(final ContextualCallable<T> callable) {
    Assert.notNull(callable, "callable");
    return new AsyncContextualCallable<T>() {
      @Override
      public T call() throws Exception {
        try {
          execution.prepare();
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

  static <T> AsyncContextualCallable<T> of(final ContextualRunnable runnable) {
    Assert.notNull(runnable, "runnable");
    return new AsyncContextualCallable<T>() {
      @Override
      public T call() throws Exception {
        try {
          execution.prepare();
          runnable.run(execution);
          execution.completeOrRetry(null, null);
        } catch (Exception e) {
          execution.completeOrRetry(null, e);
        }

        return null;
      }
    };
  }

  static <T> AsyncContextualCallable<T> ofFuture(
      final AsyncCallable<java.util.concurrent.CompletableFuture<T>> callable) {
    Assert.notNull(callable, "callable");
    return new AsyncContextualCallable<T>() {
      Semaphore asyncFutureLock = new Semaphore(1);

      @Override
      public T call() throws Exception {
        try {
          execution.prepare();
          asyncFutureLock.acquire();
          callable.call(execution).whenComplete(new BiConsumer<T, Throwable>() {
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

  static <T> AsyncContextualCallable<T> ofFuture(final Callable<java.util.concurrent.CompletableFuture<T>> callable) {
    Assert.notNull(callable, "callable");
    return new AsyncContextualCallable<T>() {
      @Override
      public T call() throws Exception {
        try {
          execution.prepare();
          callable.call().whenComplete(new BiConsumer<T, Throwable>() {
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

  static <T> AsyncContextualCallable<T> ofFuture(
      final ContextualCallable<java.util.concurrent.CompletableFuture<T>> callable) {
    Assert.notNull(callable, "callable");
    return new AsyncContextualCallable<T>() {
      @Override
      public T call() throws Exception {
        try {
          execution.prepare();
          callable.call(execution).whenComplete(new BiConsumer<T, Throwable>() {
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

  void initialize(AsyncExecution execution) {
    this.execution = execution;
  }
}