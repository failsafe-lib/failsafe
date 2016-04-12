package net.jodah.recurrent;

import java.util.concurrent.Callable;

import net.jodah.recurrent.internal.util.Assert;

/**
 * A synchronous callable with a reference to execution information.
 * 
 * @author Jonathan Halterman
 * @param <T> result type
 */
abstract class SyncContextualCallable<T> implements Callable<T> {
  protected ExecutionStats stats;

  void initialize(ExecutionStats stats) {
    this.stats = stats;
  }

  static <T> SyncContextualCallable<T> of(final ContextualCallable<T> callable) {
    Assert.notNull(callable, "callable");
    return new SyncContextualCallable<T>() {
      @Override
      public T call() throws Exception {
        T result = callable.call(stats);
        return result;
      }
    };
  }

  static <T> SyncContextualCallable<T> of(final ContextualRunnable runnable) {
    Assert.notNull(runnable, "runnable");
    return new SyncContextualCallable<T>() {
      @Override
      public T call() throws Exception {
        runnable.run(stats);
        return null;
      }
    };
  }
}
