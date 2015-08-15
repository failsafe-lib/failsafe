package net.jodah.recurrent;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import net.jodah.recurrent.internal.util.Assert;

/**
 * Tracks asynchronous invocations and allows retries to be scheduled according to a {@link RetryPolicy}.
 * 
 * @author Jonathan Halterman
 */
public class AsyncInvocation extends Invocation {
  private final AsyncCallable<Object> callable;
  private final RecurrentFuture<Object> future;
  private final Scheduler scheduler;
  volatile boolean retried;

  @SuppressWarnings("unchecked")
  <T> AsyncInvocation(AsyncCallable<T> callable, RetryPolicy retryPolicy, Scheduler scheduler,
      RecurrentFuture<T> future) {
    super(retryPolicy);
    this.callable = (AsyncCallable<Object>) callable;
    this.scheduler = scheduler;
    this.future = (RecurrentFuture<Object>) future;
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
    return retryInternal(lastResult, lastFailure);
  }

  /**
   * Attempts to retry a failed invocation. Returns true if the retry can be attempted for the {@code result}, else
   * returns false and completes the invocation and associated {@code RecurrentFuture}.
   *
   * @throws IllegalStateException if a retry method has already been called or the invocation is already complete
   */
  public boolean retryFor(Object result) {
    return retryInternal(result, null);
  }

  /**
   * Attempts to retry a failed invocation. Returns true if the retry can be attempted for the {@code result} and
   * {@code failure}, else returns false and completes the invocation and associated {@code RecurrentFuture}.
   * 
   * @throws IllegalStateException if a retry method has already been called or the invocation is already complete
   */
  public boolean retryFor(Object result, Throwable failure) {
    return retryInternal(result, failure);
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
    return retryInternal(null, failure);
  }

  /**
   * Resets the retry flag.
   */
  void reset() {
    retried = false;
  }

  /**
   * Retries the invocation if necessary else completes it.
   */
  void retryOrComplete(Object result, Throwable failure) {
    if (!retry(result, failure))
      future.complete(result, failure);
  }

  private boolean completeInternal(Object result, Throwable failure, boolean checkArgs) {
    boolean complete = super.complete(result, failure, checkArgs);
    if (complete)
      future.complete(result, failure);
    return complete;
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  private boolean retry(Object result, Throwable failure) {
    boolean canRetry = canRetryFor(result, failure);
    if (canRetry && !future.isDone() && !future.isCancelled())
      future.setFuture((Future) scheduler.schedule(callable, waitTime, TimeUnit.NANOSECONDS));
    return canRetry;
  }

  private boolean retryInternal(Object result, Throwable failure) {
    Assert.state(!retried, "Retry has already been called");
    retried = true;
    boolean retrying = retry(result, failure);
    if (!retrying)
      future.complete(result, failure);
    return retrying;
  }
}