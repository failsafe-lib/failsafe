package net.jodah.failsafe;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import net.jodah.failsafe.internal.util.Assert;
import net.jodah.failsafe.util.concurrent.Scheduler;

/**
 * Tracks asynchronous executions and allows retries to be scheduled according to a {@link RetryPolicy}.
 * 
 * @author Jonathan Halterman
 */
public final class AsyncExecution extends AbstractExecution {
  private final Callable<Object> callable;
  private final FailsafeFuture<Object> future;
  private final Listeners<Object> listeners;
  private final Scheduler scheduler;
  volatile boolean completeCalled;
  volatile boolean retryCalled;

  @SuppressWarnings("unchecked")
  <T> AsyncExecution(Callable<T> callable, RetryPolicy retryPolicy, CircuitBreaker circuitBreaker, Scheduler scheduler,
      FailsafeFuture<T> future, Listeners<?> listeners) {
    super(retryPolicy, circuitBreaker);
    this.callable = (Callable<Object>) callable;
    this.scheduler = scheduler;
    this.future = (FailsafeFuture<Object>) future;
    this.listeners = (Listeners<Object>) listeners;
  }

  /**
   * Completes the execution and the associated {@code FutureResult}.
   *
   * @throws IllegalStateException if the execution is already complete
   */
  public void complete() {
    complete(null, null, false);
  }

  /**
   * Attempts to complete the execution and the associated {@code FutureResult} with the {@code result}. Returns true on
   * success, else false if completion failed and the execution should be retried via {@link #retry()}.
   *
   * @throws IllegalStateException if the execution is already complete
   */
  public boolean complete(Object result) {
    return complete(result, null, true);
  }

  /**
   * Attempts to complete the execution and the associated {@code FutureResult} with the {@code result} and
   * {@code failure}. Returns true on success, else false if completion failed and the execution should be retried via
   * {@link #retry()}.
   * <p>
   * Note: the execution may be completed even when the {@code failure} is not {@code null}, such as when the
   * RetryPolicy does not allow retries for the {@code failure}.
   *
   * @throws IllegalStateException if the execution is already complete
   */
  public boolean complete(Object result, Throwable failure) {
    return complete(result, failure, true);
  }

  /**
   * Records an execution and returns true if a retry has been scheduled for else returns returns false and completes
   * the execution and associated {@code FutureResult}.
   *
   * @throws IllegalStateException if a retry method has already been called or the execution is already complete
   */
  public boolean retry() {
    Assert.state(!retryCalled, "Retry has already been called");
    retryCalled = true;
    return completeOrRetry(lastResult, lastFailure);
  }

  /**
   * Records an execution and returns true if a retry has been scheduled for the {@code result}, else returns false and
   * marks the execution and associated {@code FutureResult} as complete.
   *
   * @throws IllegalStateException if a retry method has already been called or the execution is already complete
   */
  public boolean retryFor(Object result) {
    return retryFor(result, null);
  }

  /**
   * Records an execution and returns true if a retry has been scheduled for the {@code result} or {@code failure}, else
   * returns false and marks the execution and associated {@code FutureResult} as complete.
   * 
   * @throws IllegalStateException if a retry method has already been called or the execution is already complete
   */
  public boolean retryFor(Object result, Throwable failure) {
    Assert.state(!retryCalled, "Retry has already been called");
    retryCalled = true;
    return completeOrRetry(result, failure);
  }

  /**
   * Records an execution and returns true if a retry has been scheduled for the {@code failure}, else returns false and
   * marks the execution and associated {@code FutureResult} as complete.
   *
   * @throws NullPointerException if {@code failure} is null
   * @throws IllegalStateException if a retry method has already been called or the execution is already complete
   */
  public boolean retryOn(Throwable failure) {
    Assert.notNull(failure, "failure");
    return retryFor(null, failure);
  }

  /**
   * Prepares for an execution retry by recording the start time, checking if the circuit is open, resetting internal
   * flags, and calling the retry listeners.
   */
  void before() {
    if (circuitBreaker != null && !circuitBreaker.allowsExecution()) {
      completed = true;
      future.complete(null, new CircuitBreakerOpenException(), false);
      return;
    }

    if (completeCalled && listeners != null)
      listeners.handleRetry(lastResult, lastFailure, listeners instanceof AsyncListeners ? copy() : this, scheduler);

    super.before();
    completeCalled = false;
    retryCalled = false;
  }

  /**
   * Attempts to complete the parent execution, calls failure handlers, and completes the future if needed.
   * 
   * @throws IllegalStateException if the execution is already complete
   */
  @Override
  synchronized boolean complete(Object result, Throwable failure, boolean checkArgs) {
    if (!completeCalled) {
      super.complete(result, failure, checkArgs);

      // Handle failure
      if (!success && listeners != null)
        listeners.handleFailedAttempt(result, failure, this, scheduler);

      // Handle completed
      if (completed)
        future.complete(result, failure, success);

      completeCalled = true;
    }

    return completed;
  }

  /**
   * Attempts to complete the execution else schedule a retry, returning whether a retry has been scheduled or not.
   * 
   * @throws IllegalStateException if the execution is already complete
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  synchronized boolean completeOrRetry(Object result, Throwable failure) {
    if (!complete(result, failure, true) && !future.isDone() && !future.isCancelled()) {
      try {
        future.setFuture((Future) scheduler.schedule(callable, waitNanos, TimeUnit.NANOSECONDS));
        return true;
      } catch (Throwable t) {
        failure = t;
        future.complete(null, failure, false);
      }
    }

    return false;
  }
}