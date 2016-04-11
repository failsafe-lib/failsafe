package net.jodah.recurrent;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import net.jodah.recurrent.internal.util.Assert;
import net.jodah.recurrent.util.concurrent.Scheduler;

/**
 * Tracks asynchronous invocations and allows retries to be scheduled according to a {@link RetryPolicy}.
 * 
 * @author Jonathan Halterman
 */
public final class AsyncInvocation extends Invocation {
  private final AsyncContextualCallable<Object> callable;
  private final RecurrentFuture<Object> future;
  private final Listeners<Object> listeners;
  private final Scheduler scheduler;
  volatile boolean completeCalled;
  volatile boolean retryCalled;
  volatile boolean shouldRetry;

  @SuppressWarnings("unchecked")
  <T> AsyncInvocation(AsyncContextualCallable<T> callable, RetryPolicy retryPolicy, Scheduler scheduler,
      RecurrentFuture<T> future, Listeners<T> listeners) {
    super(retryPolicy);
    this.callable = (AsyncContextualCallable<Object>) callable;
    this.scheduler = scheduler;
    this.future = (RecurrentFuture<Object>) future;
    this.listeners = (Listeners<Object>) listeners;
  }

  /**
   * Completes the invocation and the associated {@code RecurrentFuture}.
   *
   * @throws IllegalStateException if the invocation is already complete
   */
  @Override
  public void complete() {
    completeInternal(null, null, false);
  }

  /**
   * Attempts to complete the invocation and the associated {@code RecurrentFuture} with the {@code result}. Returns
   * true on success, else false if completion failed and should be retried via {@link #retry()}.
   *
   * @throws IllegalStateException if the invocation is already complete
   */
  public boolean complete(Object result) {
    return completeInternal(result, null, true);
  }

  /**
   * Attempts to complete the invocation and the associated {@code RecurrentFuture} with the {@code result} and
   * {@code failure}. Returns true on success, else false if completion failed and should be retried via
   * {@link #retry()}.
   * <p>
   * Note: the invocation may be completed even when the {@code failure} is not {@code null}, such as when the
   * RetryPolicy does not allow retries for the {@code failure}.
   *
   * @throws IllegalStateException if the invocation is already complete
   */
  public boolean complete(Object result, Throwable failure) {
    return completeInternal(result, failure, true);
  }

  /**
   * Attempts to retry a failed invocation. Returns true if the retry can be attempted, else returns returns false and
   * completes the invocation and associated {@code RecurrentFuture}.
   *
   * @throws IllegalStateException if a retry method has already been called or the invocation is already complete
   */
  public boolean retry() {
    Assert.state(!retryCalled, "Retry has already been called");
    retryCalled = true;
    return completeOrRetry(lastResult, lastFailure);
  }

  /**
   * Attempts to retry a failed invocation. Returns true if the retry can be attempted for the {@code result}, else
   * returns false and completes the invocation and associated {@code RecurrentFuture}.
   *
   * @throws IllegalStateException if a retry method has already been called or the invocation is already complete
   */
  public boolean retryFor(Object result) {
    return retryFor(result, null);
  }

  /**
   * Attempts to retry a failed invocation. Returns true if the retry can be attempted for the {@code result} and
   * {@code failure}, else returns false and completes the invocation and associated {@code RecurrentFuture}.
   * 
   * @throws IllegalStateException if a retry method has already been called or the invocation is already complete
   */
  public boolean retryFor(Object result, Throwable failure) {
    Assert.state(!retryCalled, "Retry has already been called");
    retryCalled = true;
    return completeOrRetry(result, failure);
  }

  /**
   * Attempts to retry a failed invocation. Returns true if the retry can be attempted for the {@code failure}, else
   * returns false and completes the invocation and associated {@code RecurrentFuture} exceptionally.
   *
   * @throws NullPointerException if {@code failure} is null
   * @throws IllegalStateException if a retry method has already been called or the invocation is already complete
   */
  public boolean retryOn(Throwable failure) {
    Assert.notNull(failure, "failure");
    return retryFor(null, failure);
  }

  /**
   * Prepares for a retry by resetting internal flags and calling the retry listeners.
   */
  void prepare() {
    if (completeCalled && listeners != null)
      listeners.handleRetry(lastResult, lastFailure, listeners instanceof AsyncListeners ? copy() : this, scheduler);
    completeCalled = false;
    retryCalled = false;
  }

  /**
   * Attempts to complete the parent invocation followed by the future.
   * 
   * @throws IllegalStateException if the invocation is already complete
   */
  private synchronized boolean completeInternal(Object result, Throwable failure, boolean checkArgs) {
    super.complete(result, failure, checkArgs);
    boolean success = completed && failure == null;

    // Handle failure
    if (!success && !completeCalled && listeners != null)
      listeners.handleFailedAttempt(result, failure, this, scheduler);

    // Handle completed
    if (completed)
      future.complete(result, failure, success);

    completeCalled = true;
    return completed;
  }

  /**
   * Attempts to complete the invocation else schedule a retry.
   * 
   * @throws IllegalStateException if the invocation is already complete
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  synchronized boolean completeOrRetry(Object result, Throwable failure) {
    boolean completed = super.complete(result, failure, true);
    boolean success = completed && failure == null;
    shouldRetry = completed ? false : canRetryForInternal(result, failure) && !future.isDone() && !future.isCancelled();

    // Handle failure
    if (!success && !completeCalled && listeners != null)
      listeners.handleFailedAttempt(result, failure, this, scheduler);

    // Handle retry needed
    if (shouldRetry)
      future.setFuture((Future) scheduler.schedule(callable, waitTime, TimeUnit.NANOSECONDS));

    // Handle completed
    if (completed || !shouldRetry)
      future.complete(result, failure, success);

    completeCalled = true;
    return shouldRetry;
  }
}