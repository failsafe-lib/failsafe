package net.jodah.failsafe;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import net.jodah.failsafe.event.ContextualResultListener;
import net.jodah.failsafe.event.ContextualSuccessListener;
import net.jodah.failsafe.event.FailureListener;
import net.jodah.failsafe.event.ResultListener;
import net.jodah.failsafe.event.SuccessListener;
import net.jodah.failsafe.internal.util.Assert;
import net.jodah.failsafe.util.concurrent.Scheduler;

/**
 * Failsafe execution event listeners.
 * 
 * @author Jonathan Halterman
 * @param <R> result type
 */
public class Listeners<R> {
  /**
   * Called when an execution is aborted.
   */
  public void onAbort(R result, Throwable failure) {
  }

  /**
   * Called when an execution is aborted.
   */
  public void onAbort(R result, Throwable failure, ExecutionContext context) {
  }

  /**
   * Called when an execution is completed.
   */
  public void onComplete(R result, Throwable failure) {
  }

  /**
   * Called when an execution is completed.
   */
  public void onComplete(R result, Throwable failure, ExecutionContext context) {
  }

  /**
   * Called after a failed attempt.
   */
  public void onFailedAttempt(R result, Throwable failure) {
  }

  /**
   * Called after a failed attempt.
   */
  public void onFailedAttempt(R result, Throwable failure, ExecutionContext context) {
  }

  /**
   * Called after a failure occurs that cannot be retried.
   */
  public void onFailure(R result, Throwable failure) {
  }

  /**
   * Called after a failure occurs that cannot be retried.
   */
  public void onFailure(R result, Throwable failure, ExecutionContext context) {
  }

  /**
   * Called after the retry policy is exceeded and the result is a failure.
   */
  public void onRetriesExceeded(R result, Throwable failure) {
  }

  /**
   * Called before a retry is attempted.
   */
  public void onRetry(R result, Throwable failure) {
  }

  /**
   * Called before a retry is attempted.
   */
  public void onRetry(R result, Throwable failure, ExecutionContext context) {
  }

  /**
   * Called after a successful execution.
   */
  public void onSuccess(R result) {
  }

  /**
   * Called after a successful execution.
   */
  public void onSuccess(R result, ExecutionContext context) {
  }

  @SuppressWarnings("unchecked")
  static <T> ContextualResultListener<T, Throwable> of(
      ContextualResultListener<? extends T, ? extends Throwable> listener, ExecutorService executor,
      Scheduler scheduler) {
    return new ContextualResultListener<T, Throwable>() {
      @Override
      public void onResult(T result, Throwable failure, ExecutionContext context) {
        Callable<T> callable = new Callable<T>() {
          public T call() {
            ((ContextualResultListener<T, Throwable>) listener).onResult(result, failure, context);
            return null;
          }
        };

        try {
          if (executor != null)
            executor.submit(callable);
          else
            scheduler.schedule(callable, 0, TimeUnit.MILLISECONDS);
        } catch (Exception ignore) {
        }
      }
    };
  }

  @SuppressWarnings("unchecked")
  static <T> ContextualResultListener<T, Throwable> of(ContextualSuccessListener<? extends T> listener) {
    Assert.notNull(listener, "listener");
    return new ContextualResultListener<T, Throwable>() {
      @Override
      public void onResult(T result, Throwable failure, ExecutionContext context) {
        ((ContextualSuccessListener<T>) listener).onSuccess(result, context);
      }
    };
  }

  @SuppressWarnings("unchecked")
  static <T> ContextualResultListener<T, Throwable> of(FailureListener<? extends Throwable> listener) {
    Assert.notNull(listener, "listener");
    return new ContextualResultListener<T, Throwable>() {
      @Override
      public void onResult(T result, Throwable failure, ExecutionContext context) {
        ((FailureListener<Throwable>) listener).onFailure(failure);
      }
    };
  }

  @SuppressWarnings("unchecked")
  static <T> ContextualResultListener<T, Throwable> of(ResultListener<? extends T, ? extends Throwable> listener) {
    Assert.notNull(listener, "listener");
    return new ContextualResultListener<T, Throwable>() {
      @Override
      public void onResult(T result, Throwable failure, ExecutionContext context) {
        ((ResultListener<T, Throwable>) listener).onResult(result, failure);
      }
    };
  }

  @SuppressWarnings("unchecked")
  static <T> ContextualResultListener<T, Throwable> of(SuccessListener<? extends T> listener) {
    Assert.notNull(listener, "listener");
    return new ContextualResultListener<T, Throwable>() {
      @Override
      public void onResult(T result, Throwable failure, ExecutionContext context) {
        ((SuccessListener<T>) listener).onSuccess(result);
      }
    };
  }
}
