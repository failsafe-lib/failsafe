package net.jodah.recurrent;

import java.util.concurrent.Callable;

import net.jodah.recurrent.event.CompletionListener;

/**
 * A callable that performs async callbacks.
 * 
 * @author Jonathan Halterman
 * @param <T> result type
 */
abstract class AsyncCallable<T> implements Callable<T> {
  protected Invocation invocation;
  protected CompletionListener<T> listener;

  static <T> AsyncCallable<T> of(final Callable<T> callable) {
    return new AsyncCallable<T>() {
      @Override
      public T call() throws Exception {
        try {
          T result = callable.call();
          listener.onCompletion(result, null);
          return result;
        } catch (Exception e) {
          listener.onCompletion(null, e);
          return null;
        }
      }
    };
  }

  static <T> AsyncCallable<T> of(final RetryableCallable<T> callable) {
    return new AsyncCallable<T>() {
      @Override
      public T call() throws Exception {
        try {
          T result = callable.call(invocation);
          listener.onCompletion(result, null);
          return result;
        } catch (Exception e) {
          listener.onCompletion(null, e);
          return null;
        }
      }
    };
  }

  static AsyncCallable<?> of(final RetryableRunnable runnable) {
    return new AsyncCallable<Object>() {
      @Override
      public Void call() throws Exception {
        try {
          runnable.run(invocation);
          listener.onCompletion(null, null);
        } catch (Exception e) {
          listener.onCompletion(null, e);
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
          listener.onCompletion(null, null);
        } catch (Exception e) {
          listener.onCompletion(null, e);
        }

        return null;
      }
    };
  }

  void initialize(Invocation invocation, CompletionListener<T> listener) {
    this.invocation = invocation;
    this.listener = listener;
  }
}