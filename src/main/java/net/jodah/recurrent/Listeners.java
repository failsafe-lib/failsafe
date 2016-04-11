package net.jodah.recurrent;

import net.jodah.recurrent.event.ContextualResultListener;
import net.jodah.recurrent.event.ContextualSuccessListener;
import net.jodah.recurrent.event.ResultListener;
import net.jodah.recurrent.event.SuccessListener;
import net.jodah.recurrent.internal.util.Assert;
import net.jodah.recurrent.util.concurrent.Scheduler;

/**
 * Recurrent event listeners.
 * 
 * @author Jonathan Halterman
 * @param <T> result type
 */
public class Listeners<T> {
  private volatile ResultListener<T, Throwable> completeListener;
  private volatile ContextualResultListener<T, Throwable> ctxCompleteListener;
  private volatile ResultListener<T, Throwable> failedAttemptListener;
  private volatile ContextualResultListener<T, Throwable> ctxFailedAttemptListener;
  private volatile ResultListener<T, Throwable> failureListener;
  private volatile ContextualResultListener<T, Throwable> ctxFailureListener;
  private volatile ResultListener<T, Throwable> retryListener;
  private volatile ContextualResultListener<T, Throwable> ctxRetryListener;
  private volatile SuccessListener<T> successListener;
  private volatile ContextualSuccessListener<T> ctxSuccessListener;

  static <T> ContextualResultListener<T, Throwable> resultListenerOf(final ContextualSuccessListener<T> listener) {
    Assert.notNull(listener, "listener");
    return new ContextualResultListener<T, Throwable>() {
      @Override
      public void onResult(T result, Throwable failure, InvocationStats stats) {
        listener.onSuccess(result, stats);
      }
    };
  }

  static <T> ResultListener<T, Throwable> resultListenerOf(final SuccessListener<T> listener) {
    Assert.notNull(listener, "listener");
    return new ResultListener<T, Throwable>() {
      @Override
      public void onResult(T result, Throwable failure) {
        listener.onSuccess(result);
      }
    };
  }

  /**
   * Called when an invocation is completed.
   */
  public void onComplete(T result, Throwable failure) {
    if (completeListener != null)
      completeListener.onResult(result, failure);
  }

  /**
   * Called when an invocation is completed.
   */
  public void onComplete(T result, Throwable failure, InvocationStats stats) {
    if (ctxCompleteListener != null)
      ctxCompleteListener.onResult(result, failure, stats);
  }

  /**
   * Called after a failed attempt.
   */
  public void onFailedAttempt(T result, Throwable failure) {
    if (failedAttemptListener != null)
      failedAttemptListener.onResult(result, failure);
  }

  /**
   * Called after a failed attempt.
   */
  public void onFailedAttempt(T result, Throwable failure, InvocationStats stats) {
    if (ctxFailedAttemptListener != null)
      ctxFailedAttemptListener.onResult(result, failure, stats);
  }

  /**
   * Called after the retry policy is exceeded and the result is a failure.
   */
  public void onFailure(T result, Throwable failure) {
    if (failureListener != null)
      failureListener.onResult(result, failure);
  }

  /**
   * Called after the retry policy is exceeded and the result is a failure.
   */
  public void onFailure(T result, Throwable failure, InvocationStats stats) {
    if (ctxFailureListener != null)
      ctxFailureListener.onResult(result, failure, stats);
  }

  /**
   * Called before a retry is attempted.
   */
  public void onRetry(T result, Throwable failure) {
    if (retryListener != null)
      retryListener.onResult(result, failure);
  }

  /**
   * Called before a retry is attempted.
   */
  public void onRetry(T result, Throwable failure, InvocationStats stats) {
    if (ctxRetryListener != null)
      ctxRetryListener.onResult(result, failure, stats);
  }

  /**
   * Called after a successful invocation.
   */
  public void onSuccess(T result) {
    if (successListener != null)
      successListener.onSuccess(result);
  }

  /**
   * Called after a successful invocation.
   */
  public void onSuccess(T result, InvocationStats stats) {
    if (ctxSuccessListener != null)
      ctxSuccessListener.onSuccess(result, stats);
  }

  /**
   * Registers the {@code listener} to be called when an invocation is completed.
   */
  @SuppressWarnings("unchecked")
  public <L extends Listeners<T>> L whenComplete(ContextualResultListener<? super T, ? extends Throwable> listener) {
    ctxCompleteListener = (ContextualResultListener<T, Throwable>) Assert.notNull(listener, "listener");
    return (L) this;
  }

  /**
   * Registers the {@code listener} to be called when an invocation is completed.
   */
  @SuppressWarnings("unchecked")
  public <L extends Listeners<T>> L whenComplete(ResultListener<? super T, ? extends Throwable> listener) {
    completeListener = (ResultListener<T, Throwable>) Assert.notNull(listener, "listener");
    return (L) this;
  }

  /**
   * Registers the {@code listener} to be called after a failed invocation attempt.
   */
  @SuppressWarnings("unchecked")
  public <L extends Listeners<T>> L whenFailedAttempt(
      ContextualResultListener<? super T, ? extends Throwable> listener) {
    ctxFailedAttemptListener = (ContextualResultListener<T, Throwable>) Assert.notNull(listener, "listener");
    return (L) this;
  }

  /**
   * Registers the {@code listener} to be called after a failed invocation attempt.
   */
  @SuppressWarnings("unchecked")
  public <L extends Listeners<T>> L whenFailedAttempt(ResultListener<? super T, ? extends Throwable> listener) {
    failedAttemptListener = (ResultListener<T, Throwable>) Assert.notNull(listener, "listener");
    return (L) this;
  }

  /**
   * Registers the {@code listener} to be called when the retry policy is exceeded and the result is a failure.
   */
  @SuppressWarnings("unchecked")
  public <L extends Listeners<T>> L whenFailure(ContextualResultListener<? super T, ? extends Throwable> listener) {
    ctxFailureListener = (ContextualResultListener<T, Throwable>) Assert.notNull(listener, "listener");
    return (L) this;
  }

  /**
   * Registers the {@code listener} to be called when the retry policy is exceeded and the result is a failure.
   */
  @SuppressWarnings("unchecked")
  public <L extends Listeners<T>> L whenFailure(ResultListener<? super T, ? extends Throwable> listener) {
    failureListener = (ResultListener<T, Throwable>) Assert.notNull(listener, "listener");
    return (L) this;
  }

  /**
   * Registers the {@code listener} to be called before a retry is attempted.
   */
  @SuppressWarnings("unchecked")
  public <L extends Listeners<T>> L whenRetry(ContextualResultListener<? super T, ? extends Throwable> listener) {
    ctxRetryListener = (ContextualResultListener<T, Throwable>) Assert.notNull(listener, "listener");
    return (L) this;
  }

  /**
   * Registers the {@code listener} to be called before a retry is attempted.
   */
  @SuppressWarnings("unchecked")
  public <L extends Listeners<T>> L whenRetry(ResultListener<? super T, ? extends Throwable> listener) {
    retryListener = (ResultListener<T, Throwable>) Assert.notNull(listener, "listener");
    return (L) this;
  }

  /**
   * Registers the {@code listener} to be called after a successful invocation.
   */
  @SuppressWarnings("unchecked")
  public <L extends Listeners<T>> L whenSuccess(ContextualSuccessListener<? super T> listener) {
    ctxSuccessListener = (ContextualSuccessListener<T>) Assert.notNull(listener, "listener");
    return (L) this;
  }

  /**
   * Registers the {@code listener} to be called after a successful invocation.
   */
  @SuppressWarnings("unchecked")
  public Listeners<T> whenSuccess(SuccessListener<? super T> listener) {
    successListener = (SuccessListener<T>) Assert.notNull(listener, "listener");
    return this;
  }

  void complete(T result, Throwable failure, InvocationStats stats, boolean success) {
    if (success) {
      handleSuccess(result);
      handleSuccess(result, stats);
    } else {
      handleFailure(result, failure);
      handleFailure(result, failure, stats);
    }
    handleComplete(result, failure);
    handleComplete(result, failure, stats);
  }

  void handleComplete(T result, Throwable failure) {
    try {
      onComplete(result, failure);
    } catch (Exception ignore) {
    }
  }

  void handleComplete(T result, Throwable failure, InvocationStats stats) {
    try {
      onComplete(result, failure, stats);
    } catch (Exception ignore) {
    }
  }

  void handleFailedAttempt(T result, Throwable failure, InvocationStats stats, Scheduler scheduler) {
    try {
      onFailedAttempt(result, failure);
    } catch (Exception ignore) {
    }
    try {
      onFailedAttempt(result, failure, stats);
    } catch (Exception ignore) {
    }
  }

  void handleFailure(T result, Throwable failure) {
    try {
      onFailure(result, failure);
    } catch (Exception ignore) {
    }
  }

  void handleFailure(T result, Throwable failure, InvocationStats stats) {
    try {
      onFailure(result, failure, stats);
    } catch (Exception ignore) {
    }
  }

  void handleRetry(T result, Throwable failure, InvocationStats stats, Scheduler scheduler) {
    try {
      onRetry(result, failure);
    } catch (Exception ignore) {
    }
    try {
      onRetry(result, failure, stats);
    } catch (Exception ignore) {
    }
  }

  void handleSuccess(T result) {
    try {
      onSuccess(result);
    } catch (Exception ignore) {
    }
  }

  void handleSuccess(T result, InvocationStats stats) {
    try {
      onSuccess(result, stats);
    } catch (Exception ignore) {
    }
  }
}
