package net.jodah.recurrent;

import java.util.concurrent.Callable;
import java.util.function.BiConsumer;

/**
 * An asynchronous callable with references to recurrent invocation information.
 * 
 * @author Jonathan Halterman
 * @param <T> result type
 */
abstract class AsyncCallable<T> implements Callable<T> {
  protected AsyncInvocation invocation;

  static <T> AsyncCallable<T> of(final Callable<T> callable) {
    return new AsyncCallable<T>() {
      @Override
      public T call() throws Exception {
        try {
          T result = callable.call();
          invocation.retryOrComplete(result, null);
          return result;
        } catch (Exception e) {
          invocation.retryOrComplete(null, e);
          return null;
        }
      }
    };
  }

  static <T> AsyncCallable<T> of(final ContextualCallable<T> callable) {
    return new AsyncCallable<T>() {
      @Override
      public synchronized T call() throws Exception {
        try {
          invocation.reset();
          return callable.call(invocation);
        } catch (Exception e) {
          invocation.retryOrComplete(null, e);
          return null;
        }
      }
    };
  }

  static AsyncCallable<?> of(final ContextualRunnable runnable) {
    return new AsyncCallable<Object>() {
      @Override
      public synchronized Void call() throws Exception {
        try {
          invocation.reset();
          runnable.run(invocation);
        } catch (Exception e) {
          invocation.retryOrComplete(null, e);
        }

        return null;
      }
    };
  }

  static AsyncCallable<?> of(final Runnable runnable) {
    return new AsyncCallable<Object>() {
      @Override
      public Void call() throws Exception {
        try {
          runnable.run();
          invocation.retryOrComplete(null, null);
        } catch (Exception e) {
          invocation.retryOrComplete(null, e);
        }

        return null;
      }
    };
  }

  static <T> AsyncCallable<T> ofFuture(final Callable<java.util.concurrent.CompletableFuture<T>> callable) {
    return new AsyncCallable<T>() {
      @Override
      public T call() throws Exception {
        try {
          callable.call().whenComplete(new BiConsumer<T, Throwable>() {
            @Override
            public void accept(T innerResult, Throwable failure) {
              invocation.retryOrComplete(innerResult, failure);
            }
          });
        } catch (Exception e) {
          invocation.retryOrComplete(null, e);
        }

        return null;
      }
    };
  }

  static <T> AsyncCallable<T> ofFuture(final ContextualCallable<java.util.concurrent.CompletableFuture<T>> callable) {
    return new AsyncCallable<T>() {
      @Override
      public synchronized T call() throws Exception {
        try {
          invocation.reset();
          callable.call(invocation).whenComplete(new BiConsumer<T, Throwable>() {
            @Override
            public void accept(T innerResult, Throwable failure) {
              if (failure != null)
                invocation.retryOrComplete(innerResult, failure);
            }
          });
        } catch (Exception e) {
          invocation.retryOrComplete(null, e);
        }

        return null;
      }
    };
  }

  void initialize(AsyncInvocation invocation) {
    this.invocation = invocation;
  }
}