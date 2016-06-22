package net.jodah.failsafe;

import java.util.concurrent.ScheduledExecutorService;

import net.jodah.failsafe.event.ContextualResultListener;
import net.jodah.failsafe.event.ContextualSuccessListener;
import net.jodah.failsafe.event.FailureListener;
import net.jodah.failsafe.event.ResultListener;
import net.jodah.failsafe.event.SuccessListener;
import net.jodah.failsafe.util.concurrent.Scheduler;

/**
 * Failsafe execution event listeners that are called asynchronously on the {@link Scheduler} or
 * {@link ScheduledExecutorService} associated with the Failsafe call.
 * 
 * @author Jonathan Halterman
 * @param <S> source type
 * @param <T> result type
 */
@SuppressWarnings("unchecked")
public class AsyncListenerBindings<S, T> extends ListenerBindings<S, T> {
  AsyncListenerConfig<T> asyncListenerConfig;
  final Scheduler scheduler;

  AsyncListenerBindings(Scheduler scheduler) {
    this.scheduler = scheduler;
  }

  static class AsyncListenerConfig<T> {
    private AsyncResultListener<T> asyncFailedAttemptListener;
    private AsyncResultListener<T> asyncFailedAttemptResultListener;
    private AsyncCtxResultListener<T> asyncCtxFailedAttemptListener;

    private AsyncResultListener<T> asyncRetryListener;
    private AsyncResultListener<T> asyncRetryResultListener;
    private AsyncCtxResultListener<T> asyncCtxRetryListener;

    private AsyncResultListener<T> asyncCompleteListener;
    private AsyncCtxResultListener<T> asyncCtxCompleteListener;

    private AsyncResultListener<T> asyncAbortListener;
    private AsyncResultListener<T> asyncAbortResultListener;
    private AsyncCtxResultListener<T> asyncCtxAbortListener;

    private AsyncResultListener<T> asyncFailureListener;
    private AsyncResultListener<T> asyncFailureResultListener;
    private AsyncCtxResultListener<T> asyncCtxFailureListener;

    private AsyncResultListener<T> asyncSuccessListener;
    private AsyncCtxResultListener<T> asyncCtxSuccessListener;
  }

  AsyncListenerConfig<T> getAsyncConfig() {
    return asyncListenerConfig != null ? asyncListenerConfig : (asyncListenerConfig = new AsyncListenerConfig<T>());
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the configured Failsafe executor or Scheduler when
   * retries are aborted according to the retry policy.
   */
  public S onAbortAsync(ContextualResultListener<? extends T, ? extends Throwable> listener) {
    getAsyncConfig().asyncCtxAbortListener = new AsyncCtxResultListener<T>(listener);
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the configured Failsafe executor or Scheduler when
   * retries are aborted according to the retry policy.
   */
  public S onAbortAsync(FailureListener<? extends Throwable> listener) {
    getAsyncConfig().asyncAbortListener = new AsyncResultListener<T>(ListenerBindings.resultListenerOf(listener));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the configured Failsafe executor or Scheduler when
   * retries are aborted according to the retry policy.
   */
  public S onAbortAsync(ResultListener<? extends T, ? extends Throwable> listener) {
    getAsyncConfig().asyncAbortResultListener = new AsyncResultListener<T>(listener);
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the configured Failsafe executor or Scheduler when an
   * execution is completed.
   */
  public S onCompleteAsync(ContextualResultListener<? extends T, ? extends Throwable> listener) {
    getAsyncConfig().asyncCtxCompleteListener = new AsyncCtxResultListener<T>(listener);
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the configured Failsafe executor or Scheduler when an
   * execution is completed.
   */
  public S onCompleteAsync(ResultListener<? extends T, ? extends Throwable> listener) {
    getAsyncConfig().asyncCompleteListener = new AsyncResultListener<T>(listener);
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the configured Failsafe executor or Scheduler after a
   * failed execution attempt.
   */
  public S onFailedAttemptAsync(ContextualResultListener<? extends T, ? extends Throwable> listener) {
    getAsyncConfig().asyncCtxFailedAttemptListener = new AsyncCtxResultListener<T>(listener);
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the configured Failsafe executor or Scheduler after a
   * failed execution attempt.
   */
  public S onFailedAttemptAsync(FailureListener<? extends Throwable> listener) {
    getAsyncConfig().asyncFailedAttemptListener = new AsyncResultListener<T>(
        ListenerBindings.resultListenerOf(listener));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the configured Failsafe executor or Scheduler after a
   * failed execution attempt.
   */
  public S onFailedAttemptAsync(ResultListener<? extends T, ? extends Throwable> listener) {
    getAsyncConfig().asyncFailedAttemptResultListener = new AsyncResultListener<T>(listener);
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the configured Failsafe executor or Scheduler when
   * the retry policy is exceeded and the result is a failure.
   */
  public S onFailureAsync(ContextualResultListener<? extends T, ? extends Throwable> listener) {
    getAsyncConfig().asyncCtxFailureListener = new AsyncCtxResultListener<T>(listener);
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the configured Failsafe executor or Scheduler when
   * the retry policy is exceeded and the result is a failure.
   */
  public S onFailureAsync(FailureListener<? extends Throwable> listener) {
    getAsyncConfig().asyncFailureListener = new AsyncResultListener<T>(ListenerBindings.resultListenerOf(listener));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the configured Failsafe executor or Scheduler when
   * the retry policy is exceeded and the result is a failure.
   */
  public S onFailureAsync(ResultListener<? extends T, ? extends Throwable> listener) {
    getAsyncConfig().asyncFailureResultListener = new AsyncResultListener<T>(listener);
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the configured Failsafe executor or Scheduler before
   * a retry is attempted.
   */
  public S onRetryAsync(ContextualResultListener<? extends T, ? extends Throwable> listener) {
    getAsyncConfig().asyncCtxRetryListener = new AsyncCtxResultListener<T>(listener);
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the configured Failsafe executor or Scheduler before
   * a retry is attempted.
   */
  public S onRetryAsync(FailureListener<? extends Throwable> listener) {
    getAsyncConfig().asyncRetryListener = new AsyncResultListener<T>(ListenerBindings.resultListenerOf(listener));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the configured Failsafe executor or Scheduler before
   * a retry is attempted.
   */
  public S onRetryAsync(ResultListener<? extends T, ? extends Throwable> listener) {
    getAsyncConfig().asyncRetryResultListener = new AsyncResultListener<T>(listener);
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the configured Failsafe executor or Scheduler after a
   * successful execution.
   */
  public S onSuccessAsync(ContextualSuccessListener<? extends T> listener) {
    getAsyncConfig().asyncCtxSuccessListener = new AsyncCtxResultListener<T>(
        ListenerBindings.resultListenerOf(listener));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the configured Failsafe executor or Scheduler after a
   * successful execution.
   */
  public S onSuccessAsync(SuccessListener<? extends T> listener) {
    getAsyncConfig().asyncSuccessListener = new AsyncResultListener<T>(ListenerBindings.resultListenerOf(listener));
    return (S) this;
  }

  @Override
  void handleAbort(T result, Throwable failure, ExecutionContext context) {
    super.handleAbort(result, failure, context);
    if (asyncListenerConfig != null)
      call(asyncListenerConfig.asyncAbortListener, asyncListenerConfig.asyncAbortResultListener,
          asyncListenerConfig.asyncCtxAbortListener, result, failure, context);
  }

  @Override
  void handleFailedAttempt(T result, Throwable failure, ExecutionContext context) {
    super.handleFailedAttempt(result, failure, context);
    if (asyncListenerConfig != null)
      call(asyncListenerConfig.asyncFailedAttemptListener, asyncListenerConfig.asyncFailedAttemptResultListener,
          asyncListenerConfig.asyncCtxFailedAttemptListener, result, failure, context);
  }

  @Override
  void handleRetry(T result, Throwable failure, ExecutionContext context) {
    super.handleRetry(result, failure, context);
    if (asyncListenerConfig != null)
      call(asyncListenerConfig.asyncRetryListener, asyncListenerConfig.asyncRetryResultListener,
          asyncListenerConfig.asyncCtxRetryListener, result, failure, context);
  }

  @Override
  void handleComplete(T result, Throwable failure, ExecutionContext context) {
    super.handleComplete(result, failure, context);
    if (asyncListenerConfig != null)
      call(null, asyncListenerConfig.asyncCompleteListener, asyncListenerConfig.asyncCtxCompleteListener, result,
          failure, context);
  }

  @Override
  void handleFailure(T result, Throwable failure, ExecutionContext context) {
    super.handleFailure(result, failure, context);
    if (asyncListenerConfig != null)
      call(asyncListenerConfig.asyncFailureListener, asyncListenerConfig.asyncFailureResultListener,
          asyncListenerConfig.asyncCtxFailureListener, result, failure, context);
  }

  @Override
  void handleSuccess(T result, ExecutionContext context) {
    super.handleSuccess(result, context);
    if (asyncListenerConfig != null)
      call(null, asyncListenerConfig.asyncSuccessListener, asyncListenerConfig.asyncCtxSuccessListener, result, null,
          context);
  }

  private void call(AsyncResultListener<T> l1, AsyncResultListener<T> l2, AsyncCtxResultListener<T> l3, T result,
      Throwable failure, ExecutionContext context) {
    if (l1 != null)
      call(l1, result, failure, scheduler);
    if (l2 != null)
      call(l2, result, failure, scheduler);
    if (l3 != null)
      call(l3, result, failure, context, scheduler);
  }
}
