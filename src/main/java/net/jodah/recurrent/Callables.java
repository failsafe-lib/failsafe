package net.jodah.recurrent;

import java.util.concurrent.Callable;

/**
 * Utilities for creating callables.
 * 
 * @author Jonathan Halterman
 */
final class Callables {
  static <T> Callable<T> callable(CompletionListener<T> listener, final T result, final Throwable failure) {
    return new Callable<T>() {
      @Override
      public T call() {
        listener.onCompletion(result, failure);
        return null;
      }
    };
  }

  static Callable<?> callable(final Runnable runnable) {
    return new Callable<Void>() {
      @Override
      public Void call() {
        runnable.run();
        return null;
      }
    };
  }
}
