package net.jodah.recurrent;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import net.jodah.recurrent.internal.util.Assert;

/**
 * An invocation that accepts retry and completion requests.
 * 
 * @author Jonathan Halterman
 */
public class Invocation extends RetryStats {
  private final AsyncCallable<Object> callable;
  private final RecurrentFuture<Object> future;
  private final Scheduler scheduler;
  volatile boolean retried;
  volatile boolean completed;

  @SuppressWarnings("unchecked")
  <T> Invocation(AsyncCallable<T> callable, RetryPolicy retryPolicy, Scheduler scheduler, RecurrentFuture<T> future) {
    super(retryPolicy);
    this.callable = (AsyncCallable<Object>) callable;
    this.scheduler = scheduler;
    this.future = (RecurrentFuture<Object>) future;
  }

  /**
   * Completes the associated {@code RecurrentFuture}.
   *
   * @throws IllegalStateException if a complete method has already been called
   */
  public void complete() {
    complete(null, null, false);
  }

  /**
   * Completes the associated {@code RecurrentFuture} with the {@code result}. Returns true on success, else false if
   * completion failed and should be retried via {@link #retryWhen(Object)}.
   *
   * @throws IllegalStateException if a complete method has already been called
   */
  public boolean complete(Object result) {
    return complete(result, null, true);
  }

  /**
   * Completes the associated {@code RecurrentFuture} with the {@code failure}. Returns true on success, else false if
   * completion failed and should be retried via {@link #canRetryOn(Throwable)}.
   *
   * @throws IllegalStateException if a complete method has already been called
   */
  public boolean completeExceptionally(Throwable failure) {
    return complete(null, failure, true);
  }

  /**
   * Completes the associated {@code RecurrentFuture} with the {@code result} and {@code failure}. Returns true on
   * success, else false if completion failed and should be retried via {@link #canRetryWhen(Object, Throwable)}.
   *
   * @throws IllegalStateException if a complete method has already been called
   */
  public boolean complete(Object result, Throwable failure) {
    return complete(result, failure, true);
  }

  /**
   * Retries a failed invocation. Returns true if the retry can be attempted, else returns false and completes the
   * associated {@code RecurrentFuture} exceptionally if the retry policy has been exceeded.
   *
   * @throws IllegalStateException if a retry method has already been called
   */
  public boolean retry() {
    return retryOrFail(null, null, false);
  }

  /**
   * Retries a failed invocation. Returns true if the retry can be attempted for the {@code failure}, else returns false
   * and completes the associated {@code RecurrentFuture} exceptionally if the retry policy has been exceeded.
   *
   * @throws NullPointerException if {@code failure} is null
   * @throws IllegalStateException if a retry method has already been called
   */
  public boolean retryOn(Throwable failure) {
    Assert.notNull(failure, "failure");
    return retryOrFail(null, failure, true);
  }

  /**
   * Retries a failed invocation. Returns true if the retry can be attempted for the {@code result}, else returns false
   * and completes the associated {@code RecurrentFuture} exceptionally if the retry policy has been exceeded.
   *
   * @throws NullPointerException if {@code result} is null
   * @throws IllegalStateException if a retry method has already been called
   */
  public boolean retryWhen(Object result) {
    Assert.notNull(result, "result");
    return retryOrFail(result, null, true);
  }

  /**
   * Retries a failed invocation. Returns true if the retry can be attempted for the {@code result} and {@code failure},
   * else returns false and completes the associated {@code RecurrentFuture} exceptionally if the retry policy has been
   * exceeded.
   * 
   * @throws IllegalArgumentException if {@code result} and {@code failure} are both null
   * @throws IllegalStateException if a retry method has already been called
   */
  public boolean retryWhen(Object result, Throwable failure) {
    Assert.isTrue(result != null || failure != null, "result or failure must not be null");
    return retryOrFail(result, failure, true);
  }

  /**
   * Resets retry and complete requests.
   */
  void reset() {
    retried = false;
    completed = false;
  }

  /**
   * Retries the invocation if necessary else completes it.
   */
  void retryOrComplete(Object result, Throwable failure) {
    if (retry(result, failure, true))
      return;

    future.complete(result, failure);
  }

  private boolean complete(Object result, Throwable failure, boolean checkArgs) {
    Assert.state(!completed, "Complete has already been called");
    completed = true;

    // Checks if a retry is required
    if (checkArgs && canRetryWhen(result, failure))
      return false;

    future.complete(result, failure);
    return true;
  }

  private boolean retryOrFail(Object result, Throwable failure, boolean checkArgs) {
    Assert.state(!retried, "Retry has already been called");
    retried = true;

    // Check if a retry has been scheduled
    if (retry(result, failure, checkArgs))
      return true;

    completed = true;
    future.complete(result, failure == null ? new RuntimeException("Retry invocations exceeded") : failure);
    return false;
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  private boolean retry(Object result, Throwable failure, boolean checkArgs) {
    if (checkArgs ? canRetryWhen(result, failure) : canRetry()) {
      if (!future.isDone() && !future.isCancelled())
        future.setFuture((Future) scheduler.schedule(callable, waitTime, TimeUnit.NANOSECONDS));
      return true;
    }

    return false;
  }
}