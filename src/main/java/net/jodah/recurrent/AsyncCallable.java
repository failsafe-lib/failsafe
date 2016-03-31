package net.jodah.recurrent;

import java.util.concurrent.Callable;
import java.util.function.BiConsumer;

import net.jodah.recurrent.internal.util.Assert;

/**
 * An asynchronous callable with references to recurrent invocation information.
 * 
 * @author Jonathan Halterman
 * @param <T> result type
 */
abstract class AsyncCallable<T> implements Callable<T> {
  protected AsyncInvocation invocation;

  static <T> AsyncCallable<T> of(final Callable<T> callable) {
    Assert.notNull(callable, "callable");
    return new AsyncCallable<T>() {
      @Override
      public T call() throws Exception {
        try {
          invocation.reset();
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

  static <T> AsyncCallable<T> of(final ContextualCallable<T> callable) {
    Assert.notNull(callable, "callable");
    return new AsyncCallable<T>() {
      @Override
      public synchronized T call() throws Exception {
        try {
          invocation.reset();
          return callable.call(invocation);
        } catch (Exception e) {
          invocation.completeOrRetry(null, e);
          return null;
        }
      }
    };
  }

  static <T> AsyncCallable<T> of(final ContextualRunnable runnable) {
    Assert.notNull(runnable, "runnable");
    return new AsyncCallable<T>() {
      @Override
      public synchronized T call() throws Exception {
        try {
          invocation.reset();
          runnable.run(invocation);
        } catch (Exception e) {
          invocation.completeOrRetry(null, e);
        }

        return null;
      }
    };
  }

  static <T> AsyncCallable<T> of(final Runnable runnable) {
    Assert.notNull(runnable, "runnable");
    return new AsyncCallable<T>() {
      @Override
      public T call() throws Exception {
        try {
          invocation.reset();
          runnable.run();
          invocation.completeOrRetry(null, null);
        } catch (Exception e) {
          invocation.completeOrRetry(null, e);
        }

        return null;
      }
    };
  }
  
  static <T> AsyncCallable<T> of(final CheckedRunnable runnable) {
    Assert.notNull(runnable, "runnable");
    return new AsyncCallable<T>() {
      @Override
      public T call() throws Exception {
        try {
          invocation.reset();
          runnable.run();
          invocation.completeOrRetry(null, null);
        } catch (Exception e) {
          invocation.completeOrRetry(null, e);
        }

        return null;
      }
    };
  }

  static <T> AsyncCallable<T> ofFuture(final Callable<java.util.concurrent.CompletableFuture<T>> callable) {
    Assert.notNull(callable, "callable");
    return new AsyncCallable<T>() {
      @Override
      public T call() throws Exception {
        try {
          invocation.reset();
          callable.call().whenComplete(new BiConsumer<T, Throwable>() {
            @Override
            public void accept(T innerResult, Throwable failure) {
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

  static <T> AsyncCallable<T> ofFuture(final ContextualCallable<java.util.concurrent.CompletableFuture<T>> callable) {
    Assert.notNull(callable, "callable");
    return new AsyncCallable<T>() {
      @Override
      public synchronized T call() throws Exception {
        try {
          invocation.reset();
          callable.call(invocation).whenComplete(new BiConsumer<T, Throwable>() {
            @Override
            public void accept(T innerResult, Throwable failure) {
              if (failure != null)
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