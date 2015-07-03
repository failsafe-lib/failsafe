package net.jodah.recurrent;

import java.util.concurrent.Callable;

/**
 * A synchronous runnable with references to recurrent invocation information.
 * 
 * @author Jonathan Halterman
 * @param <T> result type
 */
abstract class SyncCallable<T> implements Callable<T> {
  protected Invocation invocation;

  static <T> SyncCallable<T> of(final ContextualCallable<T> callable) {
    return new SyncCallable<T>() {
      @Override
      public T call() throws Exception {
        return callable.call(invocation);
      }
    };
  }

  static SyncCallable<?> of(final ContextualRunnable runnable) {
    return new SyncCallable<Object>() {
      @Override
      public Void call() throws Exception {
        runnable.run(invocation);
        return null;
      }
    };
  }

  void initialize(Invocation invocation) {
    this.invocation = invocation;
  }
}