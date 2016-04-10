package net.jodah.recurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import java.util.function.BiConsumer;

import net.jodah.recurrent.internal.util.Assert;

/**
 * An asynchronous callable with a reference to invocation information.
 * 
 * @author Jonathan Halterman
 * @param <T> result type
 */
abstract class AsyncContextualCallable<T> implements Callable<T> {
  protected AsyncInvocation invocation;

  static <T> AsyncContextualCallable<T> of(final AsyncCallable<T> callable) {
    Assert.notNull(callable, "callable");
    return new AsyncContextualCallable<T>() {
      @Override
      public synchronized T call() throws Exception {
        try {
          invocation.prepare();
          T result = callable.call(invocation);
          return result;
        } catch (Exception e) {
          invocation.completeOrRetry(null, e);
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
          invocation.prepare();
          runnable.run(invocation);
        } catch (Exception e) {
          invocation.completeOrRetry(null, e);
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
          invocation.prepare();
          T result = callable.call();
          invocation.completeOrRetry(result, null);
          return result;
        } catch (Exception e) {
          invocation.completeOrRetry(null, e);
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
          invocation.prepare();
          runnable.run();
          invocation.completeOrRetry(null, null);
        } catch (Exception e) {
          invocation.completeOrRetry(null, e);
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
          invocation.prepare();
          T result = callable.call(invocation);
          invocation.completeOrRetry(result, null);
          return result;
        } catch (Exception e) {
          invocation.completeOrRetry(null, e);
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
          invocation.prepare();
          runnable.run(invocation);
          invocation.completeOrRetry(null, null);
        } catch (Exception e) {
          invocation.completeOrRetry(null, e);
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
          invocation.prepare();
          asyncFutureLock.acquire();
          callable.call(invocation).whenComplete(new BiConsumer<T, Throwable>() {
            @Override
            public void accept(T innerResult, Throwable failure) {
              try {
                if (failure != null)
                  invocation.completeOrRetry(innerResult,
                      failure instanceof java.util.concurrent.CompletionException ? failure.getCause() : failure);
              } finally {
                asyncFutureLock.release();
              }
            }
          });
        } catch (Exception e) {
          try {
            invocation.completeOrRetry(null, e);
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
          invocation.prepare();
          callable.call().whenComplete(new BiConsumer<T, Throwable>() {
            @Override
            public void accept(T innerResult, Throwable failure) {
              // Unwrap CompletionException cause
              if (failure != null && failure instanceof java.util.concurrent.CompletionException)
                failure = failure.getCause();
              invocation.completeOrRetry(innerResult, failure);
            }
          });
        } catch (Exception e) {
          invocation.completeOrRetry(null, e);
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
          invocation.prepare();
          callable.call(invocation).whenComplete(new BiConsumer<T, Throwable>() {
            @Override
            public void accept(T innerResult, Throwable failure) {
              // Unwrap CompletionException cause
              if (failure != null && failure instanceof java.util.concurrent.CompletionException)
                failure = failure.getCause();
              invocation.completeOrRetry(innerResult, failure);
            }
          });
        } catch (Exception e) {
          invocation.completeOrRetry(null, e);
        }

        return null;
      }
    };
  }

  void initialize(AsyncInvocation invocation) {
    this.invocation = invocation;
  }
}