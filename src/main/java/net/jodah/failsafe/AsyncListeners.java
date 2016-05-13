package net.jodah.failsafe;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import net.jodah.failsafe.event.ContextualResultListener;
import net.jodah.failsafe.event.ResultListener;
import net.jodah.failsafe.internal.util.Assert;
import net.jodah.failsafe.util.concurrent.Scheduler;

/**
 * Failsafe execution event listeners that are called asynchronously on the {@link Scheduler} or
 * {@link ScheduledExecutorService} associated with the Failsafe call. To handle completion and failure events, see
 * {@link FailsafeFuture}.
 * 
 * @author Jonathan Halterman
 * @param <T> result type
 */
public class AsyncListeners<T> extends Listeners<T> {
  private volatile AsyncResultListener<T> asyncFailedAttemptListener;
  private volatile AsyncCtxResultListener<T> asyncCtxFailedAttemptListener;
  private volatile AsyncResultListener<T> asyncRetryListener;
  private volatile AsyncCtxResultListener<T> asyncCtxRetryListener;

  static class AsyncCtxResultListener<T> {
    ContextualResultListener<T, Throwable> listener;
    ExecutorService executor;

    @SuppressWarnings("unchecked")
    AsyncCtxResultListener(ContextualResultListener<? super T, ? extends Throwable> listener) {
      this.listener = (ContextualResultListener<T, Throwable>) Assert.notNull(listener, "listener");
      this.executor = null;
    }

    @SuppressWarnings("unchecked")
    AsyncCtxResultListener(ContextualResultListener<? super T, ? extends Throwable> listener,
        ExecutorService executor) {
      this.listener = (ContextualResultListener<T, Throwable>) Assert.notNull(listener, "listener");
      this.executor = Assert.notNull(executor, "executor");
    }
  }

  static class AsyncResultListener<T> {
    ResultListener<T, Throwable> listener;
    ExecutorService executor;

    @SuppressWarnings("unchecked")
    AsyncResultListener(ResultListener<? super T, ? extends Throwable> listener) {
      this.listener = (ResultListener<T, Throwable>) Assert.notNull(listener, "listener");
      this.executor = null;
    }

    @SuppressWarnings("unchecked")
    AsyncResultListener(ResultListener<? super T, ? extends Throwable> listener, ExecutorService executor) {
      this.listener = (ResultListener<T, Throwable>) Assert.notNull(listener, "listener");
      this.executor = Assert.notNull(executor, "executor");
    }
  }

  static <T> void call(AsyncCtxResultListener<T> listener, T result, Throwable failure, ExecutionContext context,
      Scheduler scheduler) {
    call(Callables.of(listener.listener, result, failure, context), listener.executor, scheduler);
  }

  static <T> void call(AsyncResultListener<T> listener, AsyncCtxResultListener<T> ctxListener, T result,
      Throwable failure, ExecutionContext context, Scheduler scheduler) {
    if (listener != null)
      call(listener, result, failure, context, scheduler);
    if (ctxListener != null)
      call(ctxListener, result, failure, context, scheduler);
  }

  static <T> void call(AsyncResultListener<T> listener, T result, Throwable failure, ExecutionContext context,
      Scheduler scheduler) {
    call(Callables.of(listener.listener, result, failure), listener.executor, scheduler);
  }

  private static void call(Callable<?> callable, ExecutorService executor, Scheduler scheduler) {
    if (executor != null)
      executor.submit(callable);
    else
      scheduler.schedule(callable, 0, TimeUnit.MILLISECONDS);
  }

  /**
   * Registers the {@code listener} to be called asynchronously after a failed execution attempt.
   */
  public AsyncListeners<T> onFailedAttemptAsync(ContextualResultListener<? super T, ? extends Throwable> listener) {
    asyncCtxFailedAttemptListener = new AsyncCtxResultListener<T>(listener);
    return this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} after a failed execution
   * attempt.
   */
  public AsyncListeners<T> onFailedAttemptAsync(ContextualResultListener<? super T, ? extends Throwable> listener,
      ExecutorService executor) {
    asyncCtxFailedAttemptListener = new AsyncCtxResultListener<T>(listener, executor);
    return this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously after a failed execution attempt.
   */
  public AsyncListeners<T> onFailedAttemptAsync(ResultListener<? super T, ? extends Throwable> listener) {
    asyncFailedAttemptListener = new AsyncResultListener<T>(listener);
    return this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} after a failed execution
   * attempt.
   */
  public AsyncListeners<T> onFailedAttemptAsync(ResultListener<? super T, ? extends Throwable> listener,
      ExecutorService executor) {
    asyncFailedAttemptListener = new AsyncResultListener<T>(listener, executor);
    return this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously before a retry is attempted.
   */
  public AsyncListeners<T> onRetryAsync(ContextualResultListener<? super T, ? extends Throwable> listener) {
    asyncCtxRetryListener = new AsyncCtxResultListener<T>(listener);
    return this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} before a retry is attempted.
   */
  public AsyncListeners<T> onRetryAsync(ContextualResultListener<? super T, ? extends Throwable> listener,
      ExecutorService executor) {
    asyncCtxRetryListener = new AsyncCtxResultListener<T>(listener, executor);
    return this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously before a retry is attempted.
   */
  public AsyncListeners<T> onRetryAsync(ResultListener<? super T, ? extends Throwable> listener) {
    asyncRetryListener = new AsyncResultListener<T>(listener);
    return this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} before a retry is attempted.
   */
  public AsyncListeners<T> onRetryAsync(ResultListener<? super T, ? extends Throwable> listener,
      ExecutorService executor) {
    asyncRetryListener = new AsyncResultListener<T>(listener, executor);
    return this;
  }

  @Override
  void handleFailedAttempt(T result, Throwable failure, ExecutionContext context, Scheduler scheduler) {
    call(asyncFailedAttemptListener, asyncCtxFailedAttemptListener, result, failure, context, scheduler);
    super.handleFailedAttempt(result, failure, context, scheduler);
  }

  @Override
  void handleRetry(T result, Throwable failure, ExecutionContext context, Scheduler scheduler) {
    call(asyncRetryListener, asyncCtxRetryListener, result, failure, context, scheduler);
    super.handleRetry(result, failure, context, scheduler);
  }
}
