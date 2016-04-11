package net.jodah.recurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import net.jodah.recurrent.event.ContextualResultListener;
import net.jodah.recurrent.event.ResultListener;
import net.jodah.recurrent.internal.util.Assert;
import net.jodah.recurrent.util.concurrent.Scheduler;

/**
 * Recurrent event listeners that are called asynchronously on the {@link Scheduler} or {@link ScheduledExecutorService}
 * associated with the Recurrent call. To handle completion and failure events, see {@link RecurrentFuture}.
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

  static <T> void call(AsyncCtxResultListener<T> listener, T result, Throwable failure, InvocationStats stats,
      Scheduler scheduler) {
    call(Callables.of(listener.listener, result, failure, stats), listener.executor, scheduler);
  }

  static <T> void call(AsyncResultListener<T> listener, AsyncCtxResultListener<T> ctxListener, T result,
      Throwable failure, InvocationStats stats, Scheduler scheduler) {
    if (listener != null)
      call(listener, result, failure, stats, scheduler);
    if (ctxListener != null)
      call(ctxListener, result, failure, stats, scheduler);
  }

  static <T> void call(AsyncResultListener<T> listener, T result, Throwable failure, InvocationStats stats,
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
   * Registers the {@code listener} to be called asynchronously after a failed invocation attempt.
   */
  public AsyncListeners<T> whenFailedAttemptAsync(ContextualResultListener<? super T, ? extends Throwable> listener) {
    asyncCtxFailedAttemptListener = new AsyncCtxResultListener<T>(listener);
    return this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} after a failed invocation
   * attempt.
   */
  public AsyncListeners<T> whenFailedAttemptAsync(ContextualResultListener<? super T, ? extends Throwable> listener,
      ExecutorService executor) {
    asyncCtxFailedAttemptListener = new AsyncCtxResultListener<T>(listener, executor);
    return this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously after a failed invocation attempt.
   */
  public AsyncListeners<T> whenFailedAttemptAsync(ResultListener<? super T, ? extends Throwable> listener) {
    asyncFailedAttemptListener = new AsyncResultListener<T>(listener);
    return this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} after a failed invocation
   * attempt.
   */
  public AsyncListeners<T> whenFailedAttemptAsync(ResultListener<? super T, ? extends Throwable> listener,
      ExecutorService executor) {
    asyncFailedAttemptListener = new AsyncResultListener<T>(listener, executor);
    return this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously before a retry is attempted.
   */
  public AsyncListeners<T> whenRetryAsync(ContextualResultListener<? super T, ? extends Throwable> listener) {
    asyncCtxRetryListener = new AsyncCtxResultListener<T>(listener);
    return this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} before a retry is attempted.
   */
  public AsyncListeners<T> whenRetryAsync(ContextualResultListener<? super T, ? extends Throwable> listener,
      ExecutorService executor) {
    asyncCtxRetryListener = new AsyncCtxResultListener<T>(listener, executor);
    return this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously before a retry is attempted.
   */
  public AsyncListeners<T> whenRetryAsync(ResultListener<? super T, ? extends Throwable> listener) {
    asyncRetryListener = new AsyncResultListener<T>(listener);
    return this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} before a retry is attempted.
   */
  public AsyncListeners<T> whenRetryAsync(ResultListener<? super T, ? extends Throwable> listener,
      ExecutorService executor) {
    asyncRetryListener = new AsyncResultListener<T>(listener, executor);
    return this;
  }

  @Override
  void handleFailedAttempt(T result, Throwable failure, InvocationStats stats, Scheduler scheduler) {
    call(asyncFailedAttemptListener, asyncCtxFailedAttemptListener, result, failure, stats, scheduler);
    super.handleFailedAttempt(result, failure, stats, scheduler);
  }

  @Override
  void handleRetry(T result, Throwable failure, InvocationStats stats, Scheduler scheduler) {
    call(asyncRetryListener, asyncCtxRetryListener, result, failure, stats, scheduler);
    super.handleRetry(result, failure, stats, scheduler);
  }
}
