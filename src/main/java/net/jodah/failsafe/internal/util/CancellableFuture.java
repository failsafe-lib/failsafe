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

  private CancellableFuture(FailsafeFuture<T> future) {
    this.future = future;
  }

  /**
   * We use a static factory method instead of the constructor to avoid the class from being accidentally loaded on
   * pre-Java 8 VMs when it's not available.
   */
  public static <T> java.util.concurrent.CompletableFuture<T> of(FailsafeFuture<T> future) {
    return new CancellableFuture<T>(future);
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    future.cancel(mayInterruptIfRunning);
    return super.cancel(mayInterruptIfRunning);
  }
};
