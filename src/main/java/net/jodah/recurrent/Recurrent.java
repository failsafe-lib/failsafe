package net.jodah.recurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

  public static <T> T withRetries(Callable<T> callable, RetryPolicy retryPolicy) {
    return call(callable, retryPolicy);
  }

  public static <T> ListenableFuture<T> withRetries(Callable<T> callable, RetryPolicy retryPolicy,
      ScheduledExecutorService executor) {
    return call(ContextualCallable.of(retryable(callable)), retryPolicy, executor, false);
  }

  public static <T> ListenableFuture<T> withRetries(Callable<T> callable, RetryPolicy retryPolicy,
      ScheduledExecutorService executor, boolean initialCallSynchronous) {
    return call(ContextualCallable.of(retryable(callable)), retryPolicy, executor, true);
  }

  public static <T> ListenableFuture<T> withRetries(RetryableCallable<T> callable, RetryPolicy retryPolicy,
      ScheduledExecutorService executor) {
    return call(ContextualCallable.of(callable), retryPolicy, executor, false);
  }

  public static <T> ListenableFuture<T> withRetries(RetryableCallable<T> callable, RetryPolicy retryPolicy,
      ScheduledExecutorService executor, boolean initialCallSynchronous) {
    return call(ContextualCallable.of(callable), retryPolicy, executor, true);
  }

  public static ListenableFuture<?> withRetries(RetryableRunnable runnable, RetryPolicy retryPolicy,
      ScheduledExecutorService executor) {
    return call(ContextualCallable.of(runnable), retryPolicy, executor, false);
  }

  public static ListenableFuture<?> withRetries(RetryableRunnable runnable, RetryPolicy retryPolicy,
      ScheduledExecutorService executor, boolean initialCallSynchronous) {
    return call(ContextualCallable.of(runnable), retryPolicy, executor, true);
  }

  /**
   * Invokes the {@code runnable}, sleeping between invocation attempts according to the {@code retryPolicy}.
   */
  public static void withRetries(Runnable runnable, RetryPolicy retryPolicy) {
    call(Callables.callable(runnable), retryPolicy);
  }

  /**
   * Invokes the {@code runnable}, scheduling retries with the {@code executor} according to the {@code retryPolicy}.
   */
  public static ListenableFuture<?> withRetries(Runnable runnable, RetryPolicy retryPolicy,
      ScheduledExecutorService executor) {
    return call(ContextualCallable.of(retryable(runnable)), retryPolicy, executor, false);
  }

  public static ListenableFuture<?> withRetries(Runnable runnable, RetryPolicy retryPolicy,
      ScheduledExecutorService executor, boolean initialCallSynchronous) {
    return call(ContextualCallable.of(retryable(runnable)), retryPolicy, executor, true);
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
  private static <T> ListenableFuture<T> call(final ContextualCallable<T> callable, final RetryPolicy retryPolicy,
      final ScheduledExecutorService executor, boolean scheduleInitialCall) {
    final CompletableFuture<T> future = new CompletableFuture<T>(executor);
    final Invocation invocation = new Invocation(callable, retryPolicy, executor);

    callable.initialize(invocation, new CompletionListener<T>() {
      public void onCompletion(T result, Throwable failure) {
        if (failure == null) {
          future.complete(result, failure);
          return;
        }

        invocation.recordFailedAttempt();

        // TODO fail on specific exceptions
        if (invocation.isPolicyExceeded())
          future.complete(result, failure);
        else
          future.setFuture(executor.schedule(callable, invocation.waitTime, TimeUnit.NANOSECONDS));
      }
    });

    if (scheduleInitialCall) {
      future.setFuture(executor.schedule(callable, invocation.waitTime, TimeUnit.NANOSECONDS));
    } else {
      try {
        callable.call();
      } catch (Throwable t) {
        invocation.recordFailedAttempt();
        if (invocation.isPolicyExceeded())
          future.complete(null, t);

        // Asynchronous retry
        future.setFuture(executor.schedule(callable, invocation.waitTime, TimeUnit.NANOSECONDS));
      }
    }

    return future;
  }
}
