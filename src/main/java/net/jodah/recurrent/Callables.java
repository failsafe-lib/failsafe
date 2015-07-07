package net.jodah.recurrent;

import java.util.concurrent.Callable;

import net.jodah.recurrent.event.CompletionListener;
import net.jodah.recurrent.event.FailureListener;
import net.jodah.recurrent.event.SuccessListener;

/**
 * Utilities for creating callables.
 * 
 * @author Jonathan Halterman
 */
final class Callables {
  static <T> Callable<T> of(final CompletionListener<T> listener, final T result, final Throwable failure) {
    return new Callable<T>() {
      @Override
      public T call() {
        listener.onCompletion(result, failure);
        return null;
      }
    };
  }

  static <T> Callable<T> of(final FailureListener listener, final Throwable failure) {
    return new Callable<T>() {
      @Override
      public T call() {
        listener.onFailure(failure);
        return null;
      }
    };
  }

  static Callable<?> of(final Runnable runnable) {
    return new Callable<Void>() {
      @Override
      public Void call() {
        runnable.run();
        return null;
      }
    };
  }

  static <T> Callable<T> of(final SuccessListener<T> listener, final T result) {
    return new Callable<T>() {
      @Override
      public T call() {
        listener.onSuccess(result);
        return null;
      }
    };
  }
}
