package net.jodah.recurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import net.jodah.recurrent.internal.util.Assert;
import net.jodah.recurrent.util.concurrent.Scheduler;
import net.jodah.recurrent.util.concurrent.Schedulers;

/**
 * Performs invocations with synchronous or asynchronous retries according to a {@link RetryPolicy}.
 * 
 * @author Jonathan Halterman
 */
public class Recurrent<T> {
  private static class AsyncRecurrentInternal implements AsyncRecurrent {
    private final RetryPolicy retryPolicy;
    private final Scheduler scheduler;
    private Listeners<?> listeners;

    private AsyncRecurrentInternal(RetryPolicy retryPolicy, Scheduler scheduler) {
      this.retryPolicy = retryPolicy;
      this.scheduler = scheduler;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<T> future(Callable<CompletableFuture<T>> callable) {
      java.util.concurrent.CompletableFuture<T> response = new java.util.concurrent.CompletableFuture<T>();
      call(AsyncContextualCallable.ofFuture(callable),
          RecurrentFuture.of(response, scheduler, (Listeners<T>) listeners));
      return response;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<T> future(ContextualCallable<CompletableFuture<T>> callable) {
      java.util.concurrent.CompletableFuture<T> response = new java.util.concurrent.CompletableFuture<T>();
      call(AsyncContextualCallable.ofFuture(callable),
          RecurrentFuture.of(response, scheduler, (Listeners<T>) listeners));
      return response;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<T> futureAsync(AsyncCallable<CompletableFuture<T>> callable) {
      java.util.concurrent.CompletableFuture<T> response = new java.util.concurrent.CompletableFuture<T>();
      call(AsyncContextualCallable.ofFuture(callable),
          RecurrentFuture.of(response, scheduler, (Listeners<T>) listeners));
      return response;
    }

    @Override
    public <T> RecurrentFuture<T> get(Callable<T> callable) {
      return call(AsyncContextualCallable.of(callable), null);
    }

    @Override
    public <T> RecurrentFuture<T> get(ContextualCallable<T> callable) {
      return call(AsyncContextualCallable.of(callable), null);
    }

    @Override
    public <T> RecurrentFuture<T> getAsync(AsyncCallable<T> callable) {
      return call(AsyncContextualCallable.of(callable), null);
    }

    @Override
    public RecurrentFuture<Void> run(CheckedRunnable runnable) {
      return call(AsyncContextualCallable.<Void>of(runnable), null);
    }

    @Override
    public RecurrentFuture<Void> run(ContextualRunnable runnable) {
      return call(AsyncContextualCallable.<Void>of(runnable), null);
    }

    @Override
    public RecurrentFuture<Void> runAsync(AsyncRunnable runnable) {
      return call(AsyncContextualCallable.<Void>of(runnable), null);
    }

    @Override
    public <T extends Listeners<?>> AsyncRecurrent with(T listeners) {
      this.listeners = Assert.notNull(listeners, "listeners");
      return this;
    }

    /**
     * Calls the asynchronous {@code callable} via the {@code executor}, performing retries according to the
     * {@code retryPolicy}.
     * 
     * @throws NullPointerException if any argument is null
     */
    @SuppressWarnings("unchecked")
    private <T> RecurrentFuture<T> call(AsyncContextualCallable<T> callable, RecurrentFuture<T> future) {
      Listeners<T> typedListeners = (Listeners<T>) listeners;
      if (future == null)
        future = new RecurrentFuture<T>(scheduler, typedListeners);
      AsyncInvocation invocation = new AsyncInvocation(callable, retryPolicy, scheduler, future, typedListeners);
      future.initialize(invocation);
      callable.initialize(invocation);
      future.setFuture((Future<T>) scheduler.schedule(callable, 0, TimeUnit.MILLISECONDS));
      return future;
    }
  }

  private static class SyncRecurrentInternal implements SyncRecurrent {
    private final RetryPolicy retryPolicy;
    private Listeners<?> listeners;

    private SyncRecurrentInternal(RetryPolicy retryPolicy) {
      this.retryPolicy = retryPolicy;
    }

    @Override
    public <T> T get(Callable<T> callable) {
      return call(Assert.notNull(callable, "callable"));
    }

    @Override
    public <T> T get(ContextualCallable<T> callable) {
      return call(SyncContextualCallable.of(callable));
    }

    @Override
    public void run(CheckedRunnable runnable) {
      call(Callables.of(runnable));
    }

    @Override
    public void run(ContextualRunnable runnable) {
      call(SyncContextualCallable.of(runnable));
    }

    @Override
    public SyncRecurrent with(Listeners<?> listeners) {
      this.listeners = Assert.notNull(listeners, "listeners");
      return this;
    }

    /**
     * Calls the {@code callable} synchronously, performing retries according to the {@code retryPolicy}.
     * 
     * @throws RecurrentException if the {@code callable} fails with a Throwable and the retry policy is exceeded or if
     *           interrupted while waiting to perform a retry.
     */
    @SuppressWarnings("unchecked")
    private <T> T call(Callable<T> callable) {
      Invocation invocation = new Invocation(retryPolicy);

      // Handle contextual calls
      if (callable instanceof SyncContextualCallable)
        ((SyncContextualCallable<T>) callable).initialize(invocation);

      Listeners<T> typedListeners = (Listeners<T>) listeners;
      T result = null;
      Throwable failure;

      while (true) {
        try {
          failure = null;
          result = callable.call();
        } catch (Throwable t) {
          failure = t;
        }

        boolean completed = invocation.complete(result, failure, true);
        boolean success = completed && failure == null;
        boolean shouldRetry = completed ? false : invocation.canRetryForInternal(result, failure);

        // Handle failure
        if (!success && typedListeners != null)
          typedListeners.handleFailedAttempt(result, failure, invocation, null);

        // Handle retry needed
        if (shouldRetry) {
          try {
            Thread.sleep(TimeUnit.NANOSECONDS.toMillis(invocation.waitTime));
          } catch (InterruptedException e) {
            throw new RecurrentException(e);
          }

          if (typedListeners != null)
            typedListeners.handleRetry(result, failure, invocation, null);
        }

        // Handle completion
        if (completed || !shouldRetry) {
          if (typedListeners != null)
            typedListeners.complete(result, failure, invocation, success);
          if (success || failure == null)
            return result;
          RecurrentException re = failure instanceof RecurrentException ? (RecurrentException) failure
              : new RecurrentException(failure);
          throw re;
        }
      }
    }
  }

  /**
   * Creates and returns a new Recurrent instance that will perform invocations and retries synchronously according to
   * the {@code retryPolicy}.
   */
  public static SyncRecurrent with(RetryPolicy retryPolicy) {
    return new SyncRecurrentInternal(retryPolicy);
  }

  /**
   * Creates and returns a new Recurrent instance that will perform invocations and retries asynchronously via the
   * {@code executor} according to the {@code retryPolicy}.
   */
  public static AsyncRecurrent with(RetryPolicy retryPolicy, ScheduledExecutorService executor) {
    return new AsyncRecurrentInternal(retryPolicy, Schedulers.of(executor));
  }

  /**
   * Creates and returns a new Recurrent instance that will perform invocations and retries asynchronously via the
   * {@code scheduler} according to the {@code retryPolicy}.
   */
  public static AsyncRecurrent with(RetryPolicy retryPolicy, Scheduler scheduler) {
    return new AsyncRecurrentInternal(retryPolicy, scheduler);
  }
}
