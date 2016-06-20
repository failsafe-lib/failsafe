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
 * Listener configuration.
 * 
 * @author Jonathan Halterman
 * @param <S> source type
 * @param <T> result type
 */
@SuppressWarnings("unchecked")
public class ListenerBindings<S, T> {
  Listeners<T> listeners;
  ListenerConfig<T> listenerConfig;

  ListenerBindings() {
  }

  static class ListenerConfig<T> {
    private FailureListener<Throwable> failedAttemptListener;
    private ResultListener<T, Throwable> failedAttemptResultListener;
    private ContextualResultListener<T, Throwable> ctxFailedAttemptListener;
    private AsyncResultListener<T> asyncFailedAttemptListener;
    private AsyncResultListener<T> asyncFailedAttemptResultListener;
    private AsyncCtxResultListener<T> asyncCtxFailedAttemptListener;

    private FailureListener<Throwable> retryListener;
    private ResultListener<T, Throwable> retryResultListener;
    private ContextualResultListener<T, Throwable> ctxRetryListener;
    private AsyncResultListener<T> asyncRetryListener;
    private AsyncResultListener<T> asyncRetryResultListener;
    private AsyncCtxResultListener<T> asyncCtxRetryListener;

    private ResultListener<T, Throwable> completeListener;
    private ContextualResultListener<T, Throwable> ctxCompleteListener;
    private AsyncResultListener<T> asyncCompleteListener;
    private AsyncCtxResultListener<T> asyncCtxCompleteListener;

    private FailureListener<Throwable> failureListener;
    private ResultListener<T, Throwable> failureResultListener;
    private ContextualResultListener<T, Throwable> ctxFailureListener;
    private AsyncResultListener<T> asyncFailureListener;
    private AsyncResultListener<T> asyncFailureResultListener;
    private AsyncCtxResultListener<T> asyncCtxFailureListener;

    private SuccessListener<T> successListener;
    private ContextualSuccessListener<T> ctxSuccessListener;
    private AsyncResultListener<T> asyncSuccessListener;
    private AsyncCtxResultListener<T> asyncCtxSuccessListener;
  }

  ListenerConfig<T> getConfig() {
    return listenerConfig != null ? listenerConfig : (listenerConfig = new ListenerConfig<T>());
  }

  static class AsyncCtxResultListener<T> {
    ContextualResultListener<T, Throwable> listener;
    ExecutorService executor;

    AsyncCtxResultListener(ContextualResultListener<? extends T, ? extends Throwable> listener) {
      this.listener = (ContextualResultListener<T, Throwable>) Assert.notNull(listener, "listener");
      this.executor = null;
    }

    AsyncCtxResultListener(ContextualResultListener<? extends T, ? extends Throwable> listener,
        ExecutorService executor) {
      this.listener = (ContextualResultListener<T, Throwable>) Assert.notNull(listener, "listener");
      this.executor = Assert.notNull(executor, "executor");
    }
  }

  static class AsyncResultListener<T> {
    ResultListener<T, Throwable> listener;
    ExecutorService executor;

    AsyncResultListener(ResultListener<? extends T, ? extends Throwable> listener) {
      this.listener = (ResultListener<T, Throwable>) Assert.notNull(listener, "listener");
      this.executor = null;
    }

    AsyncResultListener(ResultListener<? extends T, ? extends Throwable> listener, ExecutorService executor) {
      this.listener = (ResultListener<T, Throwable>) Assert.notNull(listener, "listener");
      this.executor = Assert.notNull(executor, "executor");
    }
  }

  /**
   * Registers the {@code listener} to be called when an execution is completed.
   */
  public S onComplete(ContextualResultListener<? extends T, ? extends Throwable> listener) {
    getConfig().ctxCompleteListener = (ContextualResultListener<T, Throwable>) Assert.notNull(listener, "listener");
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution is completed.
   */
  public S onComplete(ResultListener<? extends T, ? extends Throwable> listener) {
    getConfig().completeListener = (ResultListener<T, Throwable>) Assert.notNull(listener, "listener");
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously when an execution is completed.
   */
  public S onCompleteAsync(ContextualResultListener<? extends T, ? extends Throwable> listener,
      ExecutorService executor) {
    getConfig().asyncCtxCompleteListener = new AsyncCtxResultListener<T>(listener, executor);
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously when an execution is completed.
   */
  public S onCompleteAsync(ResultListener<? extends T, ? extends Throwable> listener, ExecutorService executor) {
    getConfig().asyncCompleteListener = new AsyncResultListener<T>(listener, executor);
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called after a failed execution attempt.
   */
  public S onFailedAttempt(ContextualResultListener<? extends T, ? extends Throwable> listener) {
    getConfig().ctxFailedAttemptListener = (ContextualResultListener<T, Throwable>) Assert.notNull(listener,
        "listener");
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called after a failed execution attempt.
   */
  public S onFailedAttempt(FailureListener<? extends Throwable> listener) {
    getConfig().failedAttemptListener = (FailureListener<Throwable>) Assert.notNull(listener, "listener");
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called after a failed execution attempt.
   */
  public S onFailedAttempt(ResultListener<? extends T, ? extends Throwable> listener) {
    getConfig().failedAttemptResultListener = (ResultListener<T, Throwable>) Assert.notNull(listener, "listener");
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} after a failed execution
   * attempt.
   */
  public S onFailedAttemptAsync(ContextualResultListener<? extends T, ? extends Throwable> listener,
      ExecutorService executor) {
    getConfig().asyncCtxFailedAttemptListener = new AsyncCtxResultListener<T>(listener, executor);
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} after a failed execution
   * attempt.
   */
  public S onFailedAttemptAsync(FailureListener<? extends Throwable> listener, ExecutorService executor) {
    getConfig().asyncFailedAttemptListener = new AsyncResultListener<T>(resultListenerOf(listener), executor);
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} after a failed execution
   * attempt.
   */
  public S onFailedAttemptAsync(ResultListener<? extends T, ? extends Throwable> listener, ExecutorService executor) {
    getConfig().asyncFailedAttemptResultListener = new AsyncResultListener<T>(listener, executor);
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called when the retry policy is exceeded and the result is a failure.
   */
  public S onFailure(ContextualResultListener<? extends T, ? extends Throwable> listener) {
    getConfig().ctxFailureListener = (ContextualResultListener<T, Throwable>) Assert.notNull(listener, "listener");
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called when the retry policy is exceeded and the result is a failure.
   */
  public S onFailure(FailureListener<? extends Throwable> listener) {
    getConfig().failureListener = (FailureListener<Throwable>) Assert.notNull(listener, "listener");
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called when the retry policy is exceeded and the result is a failure.
   */
  public S onFailure(ResultListener<? extends T, ? extends Throwable> listener) {
    getConfig().failureResultListener = (ResultListener<T, Throwable>) Assert.notNull(listener, "listener");
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously when the retry policy is exceeded and the result is a
   * failure.
   */
  public S onFailureAsync(ContextualResultListener<? extends T, ? extends Throwable> listener,
      ExecutorService executor) {
    getConfig().asyncCtxFailureListener = new AsyncCtxResultListener<T>(listener, executor);
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously when the retry policy is exceeded and the result is a
   * failure.
   */
  public S onFailureAsync(FailureListener<? extends Throwable> listener, ExecutorService executor) {
    getConfig().asyncFailureListener = new AsyncResultListener<T>(resultListenerOf(listener), executor);
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously when the retry policy is exceeded and the result is a
   * failure.
   */
  public S onFailureAsync(ResultListener<? extends T, ? extends Throwable> listener, ExecutorService executor) {
    getConfig().asyncFailureResultListener = new AsyncResultListener<T>(listener, executor);
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called before a retry is attempted.
   */
  public S onRetry(ContextualResultListener<? extends T, ? extends Throwable> listener) {
    getConfig().ctxRetryListener = (ContextualResultListener<T, Throwable>) Assert.notNull(listener, "listener");
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called before a retry is attempted.
   */
  public S onRetry(FailureListener<? extends Throwable> listener) {
    getConfig().retryListener = (FailureListener<Throwable>) Assert.notNull(listener, "listener");
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called before a retry is attempted.
   */
  public S onRetry(ResultListener<? extends T, ? extends Throwable> listener) {
    getConfig().retryResultListener = (ResultListener<T, Throwable>) Assert.notNull(listener, "listener");
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} before a retry is attempted.
   */
  public S onRetryAsync(ContextualResultListener<? extends T, ? extends Throwable> listener, ExecutorService executor) {
    getConfig().asyncCtxRetryListener = new AsyncCtxResultListener<T>(listener, executor);
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} before a retry is attempted.
   */
  public S onRetryAsync(FailureListener<? extends Throwable> listener, ExecutorService executor) {
    getConfig().asyncRetryListener = new AsyncResultListener<T>(resultListenerOf(listener), executor);
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} before a retry is attempted.
   */
  public S onRetryAsync(ResultListener<? extends T, ? extends Throwable> listener, ExecutorService executor) {
    getConfig().asyncRetryResultListener = new AsyncResultListener<T>(listener, executor);
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called after a successful execution.
   */
  public S onSuccess(ContextualSuccessListener<? extends T> listener) {
    getConfig().ctxSuccessListener = (ContextualSuccessListener<T>) Assert.notNull(listener, "listener");
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called after a successful execution.
   */
  public S onSuccess(SuccessListener<? extends T> listener) {
    getConfig().successListener = (SuccessListener<T>) Assert.notNull(listener, "listener");
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously after a successful execution.
   */
  public S onSuccessAsync(ContextualSuccessListener<? extends T> listener, ExecutorService executor) {
    getConfig().asyncCtxSuccessListener = new AsyncCtxResultListener<T>(resultListenerOf(listener), executor);
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously after a successful execution.
   */
  public S onSuccessAsync(SuccessListener<? extends T> listener, ExecutorService executor) {
    getConfig().asyncSuccessListener = new AsyncResultListener<T>(resultListenerOf(listener), executor);
    return (S) this;
  }

  void complete(T result, Throwable failure, ExecutionContext context, boolean success) {
    if (success)
      handleSuccess(result, context);
    else
      handleFailure(result, failure, context);
    handleComplete(result, failure, context);
  }

  void handleFailedAttempt(T result, Throwable failure, ExecutionContext context) {
    if (listenerConfig != null)
      call(listenerConfig.failedAttemptListener, listenerConfig.failedAttemptResultListener,
          listenerConfig.ctxFailedAttemptListener, listenerConfig.asyncFailedAttemptListener,
          listenerConfig.asyncFailedAttemptResultListener, listenerConfig.asyncCtxFailedAttemptListener, result,
          failure, context);

    if (listeners != null) {
      try {
        listeners.onFailedAttempt(result, failure);
      } catch (Exception ignore) {
      }
      try {
        listeners.onFailedAttempt(result, failure, context);
      } catch (Exception ignore) {
      }
    }
  }

  void handleRetry(T result, Throwable failure, ExecutionContext context) {
    if (listenerConfig != null)
      call(listenerConfig.retryListener, listenerConfig.retryResultListener, listenerConfig.ctxRetryListener,
          listenerConfig.asyncRetryListener, listenerConfig.asyncRetryResultListener,
          listenerConfig.asyncCtxRetryListener, result, failure, context);

    if (listeners != null) {
      try {
        listeners.onRetry(result, failure);
      } catch (Exception ignore) {
      }
      try {
        listeners.onRetry(result, failure, context);
      } catch (Exception ignore) {
      }
    }
  }

  void handleSuccess(T result, ExecutionContext context) {
    if (listenerConfig != null)
      call(listenerConfig.successListener, listenerConfig.ctxSuccessListener, listenerConfig.asyncSuccessListener,
          listenerConfig.asyncCtxSuccessListener, result, context);

    if (listeners != null) {
      try {
        listeners.onSuccess(result);
      } catch (Exception ignore) {
      }
      try {
        listeners.onSuccess(result, context);
      } catch (Exception ignore) {
      }
    }
  }

  void handleComplete(T result, Throwable failure, ExecutionContext context) {
    if (listenerConfig != null)
      call(null, listenerConfig.completeListener, listenerConfig.ctxCompleteListener, null,
          listenerConfig.asyncCompleteListener, listenerConfig.asyncCtxCompleteListener, result, failure, context);

    if (listeners != null) {
      try {
        listeners.onComplete(result, failure);
      } catch (Exception ignore) {
      }
      try {
        listeners.onComplete(result, failure, context);
      } catch (Exception ignore) {
      }
    }
  }

  void handleFailure(T result, Throwable failure, ExecutionContext context) {
    if (listenerConfig != null)
      call(listenerConfig.failureListener, listenerConfig.failureResultListener, listenerConfig.ctxFailureListener,
          listenerConfig.asyncFailureListener, listenerConfig.asyncFailureResultListener,
          listenerConfig.asyncCtxFailureListener, result, failure, context);

    if (listeners != null) {
      try {
        listeners.onFailure(result, failure);
      } catch (Exception ignore) {
      }
      try {
        listeners.onFailure(result, failure, context);
      } catch (Exception ignore) {
      }
    }
  }

  static <T> ContextualResultListener<T, Throwable> resultListenerOf(final ContextualSuccessListener<T> listener) {
    Assert.notNull(listener, "listener");
    return new ContextualResultListener<T, Throwable>() {
      @Override
      public void onResult(T result, Throwable failure, ExecutionContext context) {
        listener.onSuccess(result, context);
      }
    };
  }

  static <T> ResultListener<T, Throwable> resultListenerOf(final FailureListener<? extends Throwable> listener) {
    Assert.notNull(listener, "listener");
    return new ResultListener<T, Throwable>() {
      @Override
      public void onResult(T result, Throwable failure) {
        ((FailureListener<Throwable>) listener).onFailure(failure);
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

  private static <T> void call(FailureListener<Throwable> l1, ResultListener<T, Throwable> l2,
      ContextualResultListener<T, Throwable> l3, AsyncResultListener<T> l4, AsyncResultListener<T> l5,
      AsyncCtxResultListener<T> l6, T result, Throwable failure, ExecutionContext context) {
    if (l1 != null)
      call(l1, failure);
    if (l2 != null)
      call(l2, result, failure);
    if (l3 != null)
      call(l3, result, failure, context);
    if (l4 != null)
      call(l4, result, failure, null);
    if (l5 != null)
      call(l5, result, failure, null);
    if (l6 != null)
      call(l6, result, failure, context, null);
  }

  private static <T> void call(SuccessListener<T> l1, ContextualSuccessListener<T> l2, AsyncResultListener<T> l3,
      AsyncCtxResultListener<T> l4, T result, ExecutionContext context) {
    if (l1 != null)
      call(l1, result);
    if (l2 != null)
      call(l2, result, context);
    if (l3 != null)
      call(l3, result, null, null);
    if (l4 != null)
      call(l4, result, null, context, null);
  }

  private static <T> void call(FailureListener<Throwable> listener, Throwable failure) {
    try {
      listener.onFailure(failure);
    } catch (Exception ignore) {
    }
  }

  private static <T> void call(ResultListener<T, Throwable> listener, T result, Throwable failure) {
    try {
      listener.onResult(result, failure);
    } catch (Exception ignore) {
    }
  }

  private static <T> void call(ContextualResultListener<T, Throwable> listener, T result, Throwable failure,
      ExecutionContext context) {
    try {
      listener.onResult(result, failure, context);
    } catch (Exception ignore) {
    }
  }

  private static <T> void call(SuccessListener<T> listener, T result) {
    try {
      listener.onSuccess(result);
    } catch (Exception ignore) {
    }
  }

  private static <T> void call(ContextualSuccessListener<T> listener, T result, ExecutionContext context) {
    try {
      listener.onSuccess(result, context);
    } catch (Exception ignore) {
    }
  }

  static <T> void call(AsyncResultListener<T> listener, T result, Throwable failure, Scheduler scheduler) {
    call(Callables.of(listener.listener, result, failure), listener.executor, scheduler);
  }

  static <T> void call(AsyncCtxResultListener<T> listener, T result, Throwable failure, ExecutionContext context,
      Scheduler scheduler) {
    call(Callables.of(listener.listener, result, failure, context.copy()), listener.executor, scheduler);
  }

  private static void call(Callable<?> callable, ExecutorService executor, Scheduler scheduler) {
    if (executor != null)
      executor.submit(callable);
    else
      scheduler.schedule(callable, 0, TimeUnit.MILLISECONDS);
  }
}
