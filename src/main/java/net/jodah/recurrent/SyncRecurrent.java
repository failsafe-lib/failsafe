package net.jodah.recurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import net.jodah.recurrent.internal.util.Assert;

/**
 * Performs synchronous invocations with retries according to a {@link RetryPolicy}.
 * 
 * @author Jonathan Halterman
 */
public class SyncRecurrent {
  private final RetryPolicy retryPolicy;
  private Listeners<?> listeners;

  SyncRecurrent(RetryPolicy retryPolicy) {
    this.retryPolicy = retryPolicy;
  }

  /**
   * Invokes the {@code callable} until a successful result is returned or the configured {@link RetryPolicy} is
   * exceeded.
   * 
   * @throws NullPointerException if the {@code callable} is null
   * @throws RecurrentException if the {@code callable} fails with a Throwable and the retry policy is exceeded, or if
   *           interrupted while waiting to perform a retry.
   */
  public <T> T get(Callable<T> callable) {
    return call(Assert.notNull(callable, "callable"));
  }

  /**
   * Invokes the {@code callable} until a successful result is returned or the configured {@link RetryPolicy} is
   * exceeded.
   * 
   * @throws NullPointerException if the {@code callable} is null
   * @throws RecurrentException if the {@code callable} fails with a Throwable and the retry policy is exceeded, or if
   *           interrupted while waiting to perform a retry.
   */
  public <T> T get(ContextualCallable<T> callable) {
    return call(SyncContextualCallable.of(callable));
  }

  /**
   * Invokes the {@code runnable} until successful or until the configured {@link RetryPolicy} is exceeded.
   * 
   * @throws NullPointerException if the {@code runnable} is null
   * @throws RecurrentException if the {@code callable} fails with a Throwable and the retry policy is exceeded, or if
   *           interrupted while waiting to perform a retry.
   */
  public void run(CheckedRunnable runnable) {
    call(Callables.of(runnable));
  }

  /**
   * Invokes the {@code runnable} until successful or until the configured {@link RetryPolicy} is exceeded.
   * 
   * @throws NullPointerException if the {@code runnable} is null
   * @throws RecurrentException if the {@code callable} fails with a Throwable and the retry policy is exceeded, or if
   *           interrupted while waiting to perform a retry.
   */
  public void run(ContextualRunnable runnable) {
    call(SyncContextualCallable.of(runnable));
  }

  /**
   * Configures the {@code listeners} to be called as invocation events occur.
   */
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