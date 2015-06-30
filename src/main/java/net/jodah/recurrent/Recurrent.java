package net.jodah.recurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public final class Recurrent {
  private Recurrent() {
  }

  public static ListenableFuture<?> doScheduledWithRetries(RetryableRunnable runnable, RetryPolicy retryPolicy,
      Scheduler scheduler) {
    return call(ContextualCallable.of(runnable), retryPolicy, scheduler, true);
  }

  public static ListenableFuture<?> doScheduledWithRetries(Runnable runnable, RetryPolicy retryPolicy,
      Scheduler scheduler) {
    return call(ContextualCallable.of(retryable(runnable)), retryPolicy, scheduler, true);
  }

  public static ListenableFuture<?> doWithRetries(RetryableRunnable runnable, RetryPolicy retryPolicy,
      Scheduler scheduler) {
    return call(ContextualCallable.of(runnable), retryPolicy, scheduler, false);
  }

  /**
   * Invokes the {@code runnable}, sleeping between invocation attempts according to the {@code retryPolicy}.
   */
  public static void doWithRetries(Runnable runnable, RetryPolicy retryPolicy) {
    call(Callables.callable(runnable), retryPolicy);
  }

  /**
   * Invokes the {@code runnable}, scheduling retries with the {@code scheduler} according to the {@code retryPolicy}.
   */
  public static ListenableFuture<?> doWithRetries(Runnable runnable, RetryPolicy retryPolicy, Scheduler scheduler) {
    return call(ContextualCallable.of(retryable(runnable)), retryPolicy, scheduler, false);
  }

  public static <T> ListenableFuture<T> getScheduledGetWithRetries(RetryableCallable<T> callable,
      RetryPolicy retryPolicy, Scheduler scheduler) {
    return call(ContextualCallable.of(callable), retryPolicy, scheduler, true);
  }

  public static <T> ListenableFuture<T> getScheduledWithRetries(Callable<T> callable, RetryPolicy retryPolicy,
      Scheduler scheduler) {
    return call(ContextualCallable.of(retryable(callable)), retryPolicy, scheduler, true);
  }

  public static <T> T getWithRetries(Callable<T> callable, RetryPolicy retryPolicy) {
    return call(callable, retryPolicy);
  }

  public static <T> ListenableFuture<T> getWithRetries(Callable<T> callable, RetryPolicy retryPolicy,
      Scheduler scheduler) {
    return call(ContextualCallable.of(retryable(callable)), retryPolicy, scheduler, false);
  }

  public static <T> ListenableFuture<T> getWithRetries(RetryableCallable<T> callable, RetryPolicy retryPolicy,
      Scheduler scheduler) {
    return call(ContextualCallable.of(callable), retryPolicy, scheduler, false);
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
   * Calls the {@code callable} via the {@code scheduler}, performing retries according to the {@code retryPolicy}.
   */
  private static <T> ListenableFuture<T> call(final ContextualCallable<T> callable, final RetryPolicy retryPolicy,
      final Scheduler scheduler, boolean scheduleInitialCall) {
    final CompletableFuture<T> future = new CompletableFuture<T>(scheduler);
    final Invocation invocation = new Invocation(callable, retryPolicy, scheduler);

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
          future.setFuture(scheduler.schedule(callable, invocation.waitTime, TimeUnit.NANOSECONDS));
      }
    });

    if (scheduleInitialCall) {
      future.setFuture(scheduler.schedule(callable, invocation.waitTime, TimeUnit.NANOSECONDS));
    } else {
      try {
        callable.call();
      } catch (Throwable t) {
        invocation.recordFailedAttempt();
        if (invocation.isPolicyExceeded())
          future.complete(null, t);

        // Asynchronous retry
        future.setFuture(scheduler.schedule(callable, invocation.waitTime, TimeUnit.NANOSECONDS));
      }
    }

    return future;
  }
}
