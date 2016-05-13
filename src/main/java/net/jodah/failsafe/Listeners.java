package net.jodah.failsafe;

import net.jodah.failsafe.event.ContextualResultListener;
import net.jodah.failsafe.event.ContextualSuccessListener;
import net.jodah.failsafe.event.ResultListener;
import net.jodah.failsafe.event.SuccessListener;
import net.jodah.failsafe.internal.util.Assert;
import net.jodah.failsafe.util.concurrent.Scheduler;

/**
 * Failsafe execution event listeners.
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
      public void onResult(T result, Throwable failure, ExecutionContext context) {
        listener.onSuccess(result, context);
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
   * Called when an execution is completed.
   */
  public void onComplete(T result, Throwable failure) {
    if (completeListener != null)
      completeListener.onResult(result, failure);
  }

  /**
   * Called when an execution is completed.
   */
  public void onComplete(T result, Throwable failure, ExecutionContext context) {
    if (ctxCompleteListener != null)
      ctxCompleteListener.onResult(result, failure, context);
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
  public void onFailedAttempt(T result, Throwable failure, ExecutionContext context) {
    if (ctxFailedAttemptListener != null)
      ctxFailedAttemptListener.onResult(result, failure, context);
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
  public void onFailure(T result, Throwable failure, ExecutionContext context) {
    if (ctxFailureListener != null)
      ctxFailureListener.onResult(result, failure, context);
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
  public void onRetry(T result, Throwable failure, ExecutionContext context) {
    if (ctxRetryListener != null)
      ctxRetryListener.onResult(result, failure, context);
  }

  /**
   * Called after a successful execution.
   */
  public void onSuccess(T result) {
    if (successListener != null)
      successListener.onSuccess(result);
  }

  /**
   * Called after a successful execution.
   */
  public void onSuccess(T result, ExecutionContext context) {
    if (ctxSuccessListener != null)
      ctxSuccessListener.onSuccess(result, context);
  }

  /**
   * Registers the {@code listener} to be called when an execution is completed.
   */
  @SuppressWarnings("unchecked")
  public <L extends Listeners<T>> L onComplete(ContextualResultListener<? super T, ? extends Throwable> listener) {
    ctxCompleteListener = (ContextualResultListener<T, Throwable>) Assert.notNull(listener, "listener");
    return (L) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution is completed.
   */
  @SuppressWarnings("unchecked")
  public <L extends Listeners<T>> L onComplete(ResultListener<? super T, ? extends Throwable> listener) {
    completeListener = (ResultListener<T, Throwable>) Assert.notNull(listener, "listener");
    return (L) this;
  }

  /**
   * Registers the {@code listener} to be called after a failed execution attempt.
   */
  @SuppressWarnings("unchecked")
  public <L extends Listeners<T>> L onFailedAttempt(ContextualResultListener<? super T, ? extends Throwable> listener) {
    ctxFailedAttemptListener = (ContextualResultListener<T, Throwable>) Assert.notNull(listener, "listener");
    return (L) this;
  }

  /**
   * Registers the {@code listener} to be called after a failed execution attempt.
   */
  @SuppressWarnings("unchecked")
  public <L extends Listeners<T>> L onFailedAttempt(ResultListener<? super T, ? extends Throwable> listener) {
    failedAttemptListener = (ResultListener<T, Throwable>) Assert.notNull(listener, "listener");
    return (L) this;
  }

  /**
   * Registers the {@code listener} to be called when the retry policy is exceeded and the result is a failure.
   */
  @SuppressWarnings("unchecked")
  public <L extends Listeners<T>> L onFailure(ContextualResultListener<? super T, ? extends Throwable> listener) {
    ctxFailureListener = (ContextualResultListener<T, Throwable>) Assert.notNull(listener, "listener");
    return (L) this;
  }

  /**
   * Registers the {@code listener} to be called when the retry policy is exceeded and the result is a failure.
   */
  @SuppressWarnings("unchecked")
  public <L extends Listeners<T>> L onFailure(ResultListener<? super T, ? extends Throwable> listener) {
    failureListener = (ResultListener<T, Throwable>) Assert.notNull(listener, "listener");
    return (L) this;
  }

  /**
   * Registers the {@code listener} to be called before a retry is attempted.
   */
  @SuppressWarnings("unchecked")
  public <L extends Listeners<T>> L onRetry(ContextualResultListener<? super T, ? extends Throwable> listener) {
    ctxRetryListener = (ContextualResultListener<T, Throwable>) Assert.notNull(listener, "listener");
    return (L) this;
  }

  /**
   * Registers the {@code listener} to be called before a retry is attempted.
   */
  @SuppressWarnings("unchecked")
  public <L extends Listeners<T>> L onRetry(ResultListener<? super T, ? extends Throwable> listener) {
    retryListener = (ResultListener<T, Throwable>) Assert.notNull(listener, "listener");
    return (L) this;
  }

  /**
   * Registers the {@code listener} to be called after a successful execution.
   */
  @SuppressWarnings("unchecked")
  public <L extends Listeners<T>> L onSuccess(ContextualSuccessListener<? super T> listener) {
    ctxSuccessListener = (ContextualSuccessListener<T>) Assert.notNull(listener, "listener");
    return (L) this;
  }

  /**
   * Registers the {@code listener} to be called after a successful execution.
   */
  @SuppressWarnings("unchecked")
  public Listeners<T> onSuccess(SuccessListener<? super T> listener) {
    successListener = (SuccessListener<T>) Assert.notNull(listener, "listener");
    return this;
  }

  void complete(T result, Throwable failure, ExecutionContext context, boolean success) {
    if (success) {
      handleSuccess(result);
      handleSuccess(result, context);
    } else {
      handleFailure(result, failure);
      handleFailure(result, failure, context);
    }
    handleComplete(result, failure);
    handleComplete(result, failure, context);
  }

  void handleComplete(T result, Throwable failure) {
    try {
      onComplete(result, failure);
    } catch (Exception ignore) {
    }
  }

  void handleComplete(T result, Throwable failure, ExecutionContext context) {
    try {
      onComplete(result, failure, context);
    } catch (Exception ignore) {
    }
  }

  void handleFailedAttempt(T result, Throwable failure, ExecutionContext context, Scheduler scheduler) {
    try {
      onFailedAttempt(result, failure);
    } catch (Exception ignore) {
    }
    try {
      onFailedAttempt(result, failure, context);
    } catch (Exception ignore) {
    }
  }

  void handleFailure(T result, Throwable failure) {
    try {
      onFailure(result, failure);
    } catch (Exception ignore) {
    }
  }

  void handleFailure(T result, Throwable failure, ExecutionContext context) {
    try {
      onFailure(result, failure, context);
    } catch (Exception ignore) {
    }
  }

  void handleRetry(T result, Throwable failure, ExecutionContext context, Scheduler scheduler) {
    try {
      onRetry(result, failure);
    } catch (Exception ignore) {
    }
    try {
      onRetry(result, failure, context);
    } catch (Exception ignore) {
    }
  }

  void handleSuccess(T result) {
    try {
      onSuccess(result);
    } catch (Exception ignore) {
    }
  }

  void handleSuccess(T result, ExecutionContext context) {
    try {
      onSuccess(result, context);
    } catch (Exception ignore) {
    }
  }
}
