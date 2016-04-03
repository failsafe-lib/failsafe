package net.jodah.recurrent;

import java.util.concurrent.Callable;

import net.jodah.recurrent.internal.util.Assert;

/**
 * A synchronous callable with a reference to recurrent invocation information.
 * 
 * @author Jonathan Halterman
 * @param <T> result type
 */
abstract class SyncCallable<T> implements Callable<T> {
  protected InvocationStats stats;

  void initialize(InvocationStats stats) {
    this.stats = stats;
  }

  static <T> SyncCallable<T> of(final ContextualCallable<T> callable) {
    Assert.notNull(callable, "callable");
    return new SyncCallable<T>() {
      @Override
      public T call() throws Exception {
        T result = callable.call(stats);
        return result;
      }
    };
  }

  static <T> SyncCallable<T> of(final ContextualRunnable runnable) {
    Assert.notNull(runnable, "runnable");
    return new SyncCallable<T>() {
      @Override
      public T call() throws Exception {
        runnable.run(stats);
        return null;
      }
    };
  }
}
