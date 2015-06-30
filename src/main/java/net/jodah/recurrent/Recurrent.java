package net.jodah.recurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import net.jodah.recurrent.event.CompletionListener;

/**
 * Performs invocations with synchronous or asynchronous retries according to a {@link RetryPolicy}.
 * 
 * @author Jonathan Halterman
 */
public final class Recurrent {
  private Recurrent() {
  }

  /**
   * Returns a RetryableCallable for the {@code callable}.
   */
  public static <T> RetryableCallable<T> retryable(final Callable<T> callable) {
    return new RetryableCallable<T>() {
      @Override
      public T call(Invocation invocation) throws Exception {
        return callable.call();
      }
    };
  }

  /**
   * Returns a RetryableRunnable for the {@code runnable}.
   */
  public static RetryableRunnable retryable(final Runnable runnable) {
    return new RetryableRunnable() {
      @Override
      public void run(Invocation invocation) {
        runnable.run();
      }
    };
  }

  /**
   * Invokes the {@code callable}, sleeping between invocation attempts according to the {@code retryPolicy}.
   */
  public static <T> T withRetries(Callable<T> callable, RetryPolicy retryPolicy) {
    return call(callable, retryPolicy);
  }

  /**
   * Invokes the {@code callable}, scheduling retries with the {@code executor} according to the {@code retryPolicy}.
   */
  public static <T> ListenableFuture<T> withRetries(Callable<T> callable, RetryPolicy retryPolicy,
      ScheduledExecutorService executor) {
    return call(AsyncCallable.of(callable), retryPolicy, executor, false);
  }

  /**
   * Invokes the {@code callable}, performing the initial call synchronously and scheduling retries with the
   * {@code executor} according to the {@code retryPolicy}.
   */
  public static <T> ListenableFuture<T> withRetries(Callable<T> callable, RetryPolicy retryPolicy,
      ScheduledExecutorService executor, boolean initialSynchronousCall) {
    return call(AsyncCallable.of(callable), retryPolicy, executor, initialSynchronousCall);
  }

  /**
   * Invokes the {@code callable}, scheduling retries with the {@code executor} according to the {@code retryPolicy}.
   */
  public static <T> ListenableFuture<T> withRetries(RetryableCallable<T> callable, RetryPolicy retryPolicy,
      ScheduledExecutorService executor) {
    return call(AsyncCallable.of(callable), retryPolicy, executor, false);
  }

  /**
   * Invokes the {@code callable}, performing the initial call synchronously and scheduling retries with the
   * {@code executor} according to the {@code retryPolicy}.
   */
  public static <T> ListenableFuture<T> withRetries(RetryableCallable<T> callable, RetryPolicy retryPolicy,
      ScheduledExecutorService executor, boolean initialSynchronousCall) {
    return call(AsyncCallable.of(callable), retryPolicy, executor, initialSynchronousCall);
  }

  /**
   * Invokes the {@code runnable}, scheduling retries with the {@code executor} according to the {@code retryPolicy}.
   */
  public static ListenableFuture<?> withRetries(RetryableRunnable runnable, RetryPolicy retryPolicy,
      ScheduledExecutorService executor) {
    return call(AsyncCallable.of(runnable), retryPolicy, executor, false);
  }

  /**
   * Invokes the {@code runnable}, performing the initial call synchronously and scheduling retries with the
   * {@code executor} according to the {@code retryPolicy}.
   */
  public static ListenableFuture<?> withRetries(RetryableRunnable runnable, RetryPolicy retryPolicy,
      ScheduledExecutorService executor, boolean initialSynchronousCall) {
    return call(AsyncCallable.of(runnable), retryPolicy, executor, initialSynchronousCall);
  }

  /**
   * Invokes the {@code runnable}, sleeping between invocation attempts according to the {@code retryPolicy}.
   */
  public static void withRetries(Runnable runnable, RetryPolicy retryPolicy) {
    call(Callables.of(runnable), retryPolicy);
  }

  /**
   * Invokes the {@code runnable}, scheduling retries with the {@code executor} according to the {@code retryPolicy}.
   */
  public static ListenableFuture<?> withRetries(Runnable runnable, RetryPolicy retryPolicy,
      ScheduledExecutorService executor) {
    return call(AsyncCallable.of(runnable), retryPolicy, executor, false);
  }

  /**
   * Invokes the {@code runnable}, performing the initial call synchronously and scheduling retries with the
   * {@code executor} according to the {@code retryPolicy}.
   */
  public static ListenableFuture<?> withRetries(Runnable runnable, RetryPolicy retryPolicy,
      ScheduledExecutorService executor, boolean initialSynchronousCall) {
    return call(AsyncCallable.of(runnable), retryPolicy, executor, initialSynchronousCall);
  }

  /**
   * Calls the {@code callable} synchronously, performing retries according to the {@code retryPolicy}.
   */
  private static <T> T call(Callable<T> callable, RetryPolicy retryPolicy) {
    Invocation invocation = new Invocation(callable, retryPolicy, null);

    while (true) {
      try {
        return callable.call();
      } catch (Throwable t) {
        invocation.recordFailedAttempt();
        // TODO fail on specific exceptions
        if (invocation.isPolicyExceeded()) {
          RuntimeException re = t instanceof RuntimeException ? (RuntimeException) t : new RuntimeException(t);
          throw re;
        }

        try {
          Thread.sleep(TimeUnit.NANOSECONDS.toMillis(invocation.waitTime));
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
    }
  }

  /**
   * Calls the {@code callable} via the {@code executor}, performing retries according to the {@code retryPolicy}.
   */
  private static <T> ListenableFuture<T> call(final AsyncCallable<T> callable, final RetryPolicy retryPolicy,
      final ScheduledExecutorService executor, boolean initialSynchronousCall) {
    final CompletableFuture<T> future = new CompletableFuture<T>(executor);
    final Invocation invocation = new Invocation(callable, retryPolicy, executor);

    callable.initialize(invocation, new CompletionListener<T>() {
      public void onCompletion(T result, Throwable failure) {
        if (failure == null)
          future.complete(result, null);
        else {
          // TODO fail on specific exceptions
          invocation.recordFailedAttempt();
          if (invocation.isPolicyExceeded())
            future.complete(null, failure);
          else
            future.setFuture(executor.schedule(callable, invocation.waitTime, TimeUnit.NANOSECONDS));
        }
      }
    });

    if (initialSynchronousCall) {
      try {
        callable.call();
      } catch (Throwable unreachable) {
      }
    } else {
      future.setFuture(executor.schedule(callable, invocation.waitTime, TimeUnit.NANOSECONDS));
    }

    return future;
  }
}
