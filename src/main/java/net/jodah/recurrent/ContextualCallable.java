package net.jodah.recurrent;

import java.util.concurrent.Callable;

/**
 * A callable that performs a call within the context of an invocation, calling a listener after the call is complete.
 * 
 * @author Jonathan Halterman
 * @param <T> result type
 */
abstract class ContextualCallable<T> implements Callable<T> {
  protected Invocation invocation;
  protected CompletionListener<T> listener;

  static <T> ContextualCallable<T> of(final RetryableCallable<T> callable) {
    return new ContextualCallable<T>() {
      @Override
      public T call() throws Exception {
        T result = null;
        Throwable failure = null;

        try {
          result = callable.call(invocation);
        } catch (Exception e) {
          failure = e;
        }

        listener.onCompletion(result, failure);
        return result;
      }
    };
  }

  static ContextualCallable<?> of(final RetryableRunnable runnable) {
    return new ContextualCallable<Object>() {
      @Override
      public Void call() throws Exception {
        Throwable failure = null;

        try {
          runnable.run(invocation);
        } catch (Exception e) {
          failure = e;
        }

        listener.onCompletion(null, failure);
        return null;
      }
    };
  }

  void initialize(Invocation invocation, CompletionListener<T> listener) {
    this.invocation = invocation;
    this.listener = listener;
  }
}