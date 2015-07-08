package net.jodah.recurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import net.jodah.recurrent.event.CompletionListener;

/**
 * Performs invocations with synchronous or asynchronous retries according to a {@link RetryPolicy}. Asynchronous
 * retries can optionally be performed on a {@link ContextualRunnable} or {@link ContextualCallable} which allow
 * invocations to be manually retried or completed.
 * 
 * @author Jonathan Halterman
 */
public final class Recurrent {
  private Recurrent() {
  }

  /**
   * Invokes the {@code callable}, scheduling retries with the {@code executor} according to the {@code retryPolicy}.
   */
  public static <T> java.util.concurrent.CompletableFuture<T> future(
      Callable<java.util.concurrent.CompletableFuture<T>> callable, RetryPolicy retryPolicy,
      ScheduledExecutorService executor) {
    return future(contextual(callable), retryPolicy, executor);
  }

  /**
   * Invokes the {@code callable}, scheduling retries with the {@code executor} according to the {@code retryPolicy}.
   */
  public static <T> java.util.concurrent.CompletableFuture<T> future(
      ContextualCallable<java.util.concurrent.CompletableFuture<T>> callable, RetryPolicy retryPolicy,
      ScheduledExecutorService executor) {
    final java.util.concurrent.CompletableFuture<T> response = new java.util.concurrent.CompletableFuture<T>();
    RecurrentFuture<T> future = new RecurrentFuture<T>(executor).whenComplete(new CompletionListener<T>() {
      @Override
      public void onCompletion(T result, Throwable failure) {
        if (failure == null)
          response.complete(result);
        else
          response.completeExceptionally(failure);
      }
    });

    call(AsyncCallable.ofFuture(callable), retryPolicy, executor, future);
    return response;
  }

  /**
   * Invokes the {@code callable}, sleeping between invocation attempts according to the {@code retryPolicy}.
   * 
   * @throws RuntimeException if the {@code callable} fails and the retry policy is exceeded or if interrupted while
   *           waiting to perform a retry. Checked exceptions, including InterruptedException, are wrapped in
   *           RuntimeException.
   */
  public static <T> T get(Callable<T> callable, RetryPolicy retryPolicy) {
    return call(callable, retryPolicy);
  }

  /**
   * Invokes the {@code callable}, scheduling retries with the {@code executor} according to the {@code retryPolicy}.
   */
  public static <T> RecurrentFuture<T> get(Callable<T> callable, RetryPolicy retryPolicy,
      ScheduledExecutorService executor) {
    return call(AsyncCallable.of(callable), retryPolicy, executor, null);
  }

  /**
   * Invokes the {@code callable}, scheduling retries with the {@code executor} according to the {@code retryPolicy}.
   */
  public static <T> RecurrentFuture<T> get(ContextualCallable<T> callable, RetryPolicy retryPolicy,
      ScheduledExecutorService executor) {
    return call(AsyncCallable.of(callable), retryPolicy, executor, null);
  }

  /**
   * Invokes the {@code runnable}, scheduling retries with the {@code executor} according to the {@code retryPolicy}.
   */
  public static RecurrentFuture<?> run(ContextualRunnable runnable, RetryPolicy retryPolicy,
      ScheduledExecutorService executor) {
    return call(AsyncCallable.of(runnable), retryPolicy, executor, null);
  }

  /**
   * Invokes the {@code runnable}, sleeping between invocation attempts according to the {@code retryPolicy}.
   * 
   * @throws RuntimeException if the {@code callable} fails and the retry policy is exceeded or if interrupted while
   *           waiting to perform a retry. Checked exceptions, including InterruptedException, are wrapped in
   *           RuntimeException.
   */
  public static void run(Runnable runnable, RetryPolicy retryPolicy) {
    call(Callables.of(runnable), retryPolicy);
  }

  /**
   * Invokes the {@code runnable}, scheduling retries with the {@code executor} according to the {@code retryPolicy}.
   */
  public static RecurrentFuture<?> run(Runnable runnable, RetryPolicy retryPolicy, ScheduledExecutorService executor) {
    return call(AsyncCallable.of(runnable), retryPolicy, executor, null);
  }

  /**
   * Calls the {@code callable} via the {@code executor}, performing retries according to the {@code retryPolicy}.
   */
  private static <T> RecurrentFuture<T> call(final AsyncCallable<T> callable, final RetryPolicy retryPolicy,
      final ScheduledExecutorService executor, RecurrentFuture<T> future) {
    if (future == null)
      future = new RecurrentFuture<T>(executor);
    callable.initialize(new Invocation(retryPolicy), future, executor);
    future.setFuture(executor.submit(callable));
    return future;
  }

  /**
   * Calls the {@code callable} synchronously, performing retries according to the {@code retryPolicy}.
   * 
   * @throws RuntimeException if the {@code callable} fails and the retry policy is exceeded or if interrupted while
   *           waiting to perform a retry. Checked exceptions, including InterruptedException, are wrapped in
   *           RuntimeException.
   */
  private static <T> T call(Callable<T> callable, RetryPolicy retryPolicy) {
    Invocation invocation = new Invocation(retryPolicy);

    while (true) {
      try {
        return callable.call();
      } catch (Throwable t) {
        invocation.recordFailedAttempt();
        if (retryPolicy.allowsRetriesFor(t) && !invocation.isPolicyExceeded()) {
          try {
            Thread.sleep(TimeUnit.NANOSECONDS.toMillis(invocation.waitTime));
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
        } else {
          RuntimeException re = t instanceof RuntimeException ? (RuntimeException) t : new RuntimeException(t);
          throw re;
        }
      }
    }
  }

  /**
   * Returns a ContextualCallable for the {@code callable}.
   */
  private static <T> ContextualCallable<T> contextual(final Callable<T> callable) {
    return new ContextualCallable<T>() {
      @Override
      public T call(Invocation invocation) throws Exception {
        return callable.call();
      }
    };
  }
}
