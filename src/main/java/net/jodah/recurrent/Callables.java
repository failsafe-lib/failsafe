package net.jodah.recurrent;

import java.util.concurrent.Callable;

import net.jodah.recurrent.event.ContextualResultListener;
import net.jodah.recurrent.event.ResultListener;
import net.jodah.recurrent.internal.util.Assert;

/**
 * Utilities for creating callables.
 * 
 * @author Jonathan Halterman
 */
final class Callables {
  static <T> Callable<T> of(final ContextualResultListener<T, Throwable> listener, final T result,
      final Throwable failure, final InvocationStats stats) {
    return new Callable<T>() {
      @Override
      public T call() {
        listener.onResult(result, failure, stats);
        return null;
      }
    };
  }

  static <T> Callable<T> of(final ResultListener<T, Throwable> listener, final T result, final Throwable failure) {
    return new Callable<T>() {
      @Override
      public T call() {
        listener.onResult(result, failure);
        return null;
      }
    };
  }

  static Callable<Object> of(final Runnable runnable) {
    Assert.notNull(runnable, "runnable");
    return new Callable<Object>() {
      @Override
      public Void call() {
        runnable.run();
        return null;
      }
    };
  }
  
  static Callable<Object> of(final CheckedRunnable runnable) {
    Assert.notNull(runnable, "runnable");
    return new Callable<Object>() {
      @Override
      public Void call() {
        try {
          runnable.run();
          return null;
        } catch (Exception e) {
          throw new RecurrentException(e);
        }
      }
    };
  }
}
