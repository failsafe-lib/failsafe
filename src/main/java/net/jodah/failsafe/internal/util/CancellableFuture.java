package net.jodah.failsafe.internal.util;

import net.jodah.failsafe.FailsafeFuture;

/**
 * A CompletableFuture that forwards cancellation requests to an associated FailsafeFuture.
 * 
 * @author Jonathan Halterman
 * @param <T>
 */
public class CancellableFuture<T> extends java.util.concurrent.CompletableFuture<T> {
  final FailsafeFuture<T> future;

  public CancellableFuture(FailsafeFuture<T> future) {
    this.future = future;
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    future.cancel(mayInterruptIfRunning);
    return super.cancel(mayInterruptIfRunning);
  }
};
