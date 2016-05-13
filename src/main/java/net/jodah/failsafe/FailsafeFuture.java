package net.jodah.failsafe;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.jodah.failsafe.AsyncListeners.AsyncCtxResultListener;
import net.jodah.failsafe.AsyncListeners.AsyncResultListener;
import net.jodah.failsafe.event.ContextualResultListener;
import net.jodah.failsafe.event.ContextualSuccessListener;
import net.jodah.failsafe.event.ResultListener;
import net.jodah.failsafe.event.SuccessListener;
import net.jodah.failsafe.internal.util.Assert;
import net.jodah.failsafe.internal.util.ReentrantCircuit;
import net.jodah.failsafe.util.concurrent.Scheduler;

/**
 * The future result of an asynchronous execution.
 * 
 * @author Jonathan Halterman
 * @param <T> result type
 */
public class FailsafeFuture<T> implements Future<T> {
  private final ReentrantCircuit circuit = new ReentrantCircuit();
  private final Scheduler scheduler;
  private ExecutionContext context;
  private volatile Future<T> delegate;
  private volatile boolean done;
  private volatile boolean cancelled;
  private volatile boolean success;
  private volatile T result;
  private volatile Throwable failure;

  // Listeners
  private final Listeners<T> listeners;
  private volatile AsyncResultListener<T> asyncCompleteListener;
  private volatile AsyncCtxResultListener<T> asyncCtxCompleteListener;
  private volatile AsyncResultListener<T> asyncFailureListener;
  private volatile AsyncCtxResultListener<T> asyncCtxFailureListener;
  private volatile AsyncResultListener<T> asyncSuccessListener;
  private volatile AsyncCtxResultListener<T> asyncCtxSuccessListener;

  FailsafeFuture(Scheduler scheduler, Listeners<T> listeners) {
    this.scheduler = scheduler;
    this.listeners = listeners == null ? new Listeners<T>() : listeners;
    circuit.open();
  }

  static <T> FailsafeFuture<T> of(final java.util.concurrent.CompletableFuture<T> future, Scheduler scheduler,
      Listeners<T> listeners) {
    Assert.notNull(future, "future");
    Assert.notNull(scheduler, "scheduler");
    return new FailsafeFuture<T>(scheduler, listeners).onComplete(new ResultListener<T, Throwable>() {
      @Override
      public void onResult(T result, Throwable failure) {
        if (failure == null)
          future.complete(result);
        else
          future.completeExceptionally(failure);
      }
    });
  }

  @Override
  public synchronized boolean cancel(boolean mayInterruptIfRunning) {
    boolean result = delegate.cancel(mayInterruptIfRunning);
    cancelled = true;
    circuit.close();
    return result;
  }

  @Override
  public T get() throws InterruptedException, ExecutionException {
    circuit.await();
    if (failure != null)
      throw new ExecutionException(failure);
    return result;
  }

  @Override
  public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
    if (!circuit.await(timeout, Assert.notNull(unit, "unit")))
      throw new TimeoutException();
    if (failure != null)
      throw new ExecutionException(failure);
    return result;
  }

  @Override
  public boolean isCancelled() {
    return cancelled;
  }

  @Override
  public boolean isDone() {
    return done;
  }

  /**
   * Registers the {@code listener} to be called when an execution is completed.
   */
  public synchronized FailsafeFuture<T> onComplete(ContextualResultListener<? super T, ? extends Throwable> listener) {
    listeners.onComplete(listener);
    if (done)
      listeners.handleComplete(result, failure, context);
    return this;
  }

  /**
   * Registers the {@code listener} to be called when an execution is completed.
   */
  public synchronized FailsafeFuture<T> onComplete(ResultListener<? super T, ? extends Throwable> listener) {
    listeners.onComplete(listener);
    if (done)
      listeners.handleComplete(result, failure);
    return this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously when an execution is completed.
   */
  public synchronized FailsafeFuture<T> onCompleteAsync(
      ContextualResultListener<? super T, ? extends Throwable> listener) {
    call(done, asyncCtxCompleteListener = new AsyncCtxResultListener<T>(listener));
    return this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously when an execution is completed.
   */
  public synchronized FailsafeFuture<T> onCompleteAsync(
      ContextualResultListener<? super T, ? extends Throwable> listener, ExecutorService executor) {
    call(done, asyncCtxCompleteListener = new AsyncCtxResultListener<T>(listener, executor));
    return this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously when an execution is completed.
   */
  public synchronized FailsafeFuture<T> onCompleteAsync(ResultListener<? super T, ? extends Throwable> listener) {
    call(done, asyncCompleteListener = new AsyncResultListener<T>(listener));
    return this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously when an execution is completed.
   */
  public synchronized FailsafeFuture<T> onCompleteAsync(ResultListener<? super T, ? extends Throwable> listener,
      ExecutorService executor) {
    call(done, asyncCompleteListener = new AsyncResultListener<T>(listener, executor));
    return this;
  }

  /**
   * Registers the {@code listener} to be called when the retry policy is exceeded and the result is a failure.
   */
  public synchronized FailsafeFuture<T> onFailure(ContextualResultListener<? super T, ? extends Throwable> listener) {
    listeners.onFailure(listener);
    if (done && !success)
      listeners.handleFailure(result, failure, context);
    return this;
  }

  /**
   * Registers the {@code listener} to be called when the retry policy is exceeded and the result is a failure.
   */
  public synchronized FailsafeFuture<T> onFailure(ResultListener<? super T, ? extends Throwable> listener) {
    listeners.onFailure(listener);
    if (done && !success)
      listeners.handleFailure(result, failure);
    return this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously when the retry policy is exceeded and the result is a
   * failure.
   */
  public synchronized FailsafeFuture<T> onFailureAsync(
      ContextualResultListener<? super T, ? extends Throwable> listener) {
    call(done && !success, asyncCtxFailureListener = new AsyncCtxResultListener<T>(listener));
    return this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously when the retry policy is exceeded and the result is a
   * failure.
   */
  public synchronized FailsafeFuture<T> onFailureAsync(
      ContextualResultListener<? super T, ? extends Throwable> listener, ExecutorService executor) {
    call(done && !success, asyncCtxFailureListener = new AsyncCtxResultListener<T>(listener, executor));
    return this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously when the retry policy is exceeded and the result is a
   * failure.
   */
  public synchronized FailsafeFuture<T> onFailureAsync(ResultListener<? super T, ? extends Throwable> listener) {
    call(done && !success, asyncFailureListener = new AsyncResultListener<T>(listener));
    return this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously when the retry policy is exceeded and the result is a
   * failure.
   */
  public synchronized FailsafeFuture<T> onFailureAsync(ResultListener<? super T, ? extends Throwable> listener,
      ExecutorService executor) {
    call(done && !success, asyncFailureListener = new AsyncResultListener<T>(listener, executor));
    return this;
  }

  /**
   * Registers the {@code listener} to be called after a successful execution.
   */
  public synchronized FailsafeFuture<T> onSuccess(ContextualSuccessListener<? super T> listener) {
    listeners.onSuccess(listener);
    if (done && success)
      listeners.handleSuccess(result, context);
    return this;
  }

  /**
   * Registers the {@code listener} to be called after a successful execution.
   */
  public synchronized FailsafeFuture<T> onSuccess(SuccessListener<? super T> listener) {
    listeners.onSuccess(listener);
    if (done && success)
      listeners.handleSuccess(result);
    return this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously after a successful execution.
   */
  public synchronized FailsafeFuture<T> onSuccessAsync(ContextualSuccessListener<? super T> listener) {
    call(done && success,
        asyncCtxSuccessListener = new AsyncCtxResultListener<T>(Listeners.resultListenerOf(listener)));
    return this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously after a successful execution.
   */
  public synchronized FailsafeFuture<T> onSuccessAsync(ContextualSuccessListener<? super T> listener,
      ExecutorService executor) {
    call(done && success,
        asyncCtxSuccessListener = new AsyncCtxResultListener<T>(Listeners.resultListenerOf(listener), executor));
    return this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously after a successful execution.
   */
  public synchronized FailsafeFuture<T> onSuccessAsync(SuccessListener<? super T> listener) {
    call(done && success, asyncSuccessListener = new AsyncResultListener<T>(Listeners.resultListenerOf(listener)));
    return this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously after a successful execution.
   */
  public synchronized FailsafeFuture<T> onSuccessAsync(SuccessListener<? super T> listener, ExecutorService executor) {
    call(done && success,
        asyncSuccessListener = new AsyncResultListener<T>(Listeners.resultListenerOf(listener), executor));
    return this;
  }

  synchronized void complete(T result, Throwable failure, boolean success) {
    this.result = result;
    this.failure = failure;
    this.success = success;
    done = true;
    if (success)
      AsyncListeners.call(asyncSuccessListener, asyncCtxSuccessListener, result, failure, context, scheduler);
    else
      AsyncListeners.call(asyncFailureListener, asyncCtxFailureListener, result, failure, context, scheduler);
    AsyncListeners.call(asyncCompleteListener, asyncCtxCompleteListener, result, failure, context, scheduler);
    listeners.complete(result, failure, context, success);
    circuit.close();
  }

  void inject(ExecutionContext context) {
    this.context = context;
  }

  void setFuture(Future<T> delegate) {
    this.delegate = delegate;
  }

  private void call(boolean condition, AsyncCtxResultListener<T> listener) {
    if (condition)
      AsyncListeners.call(listener, result, failure, context, scheduler);
  }

  private void call(boolean condition, AsyncResultListener<T> listener) {
    if (condition)
      AsyncListeners.call(listener, result, failure, context, scheduler);
  }
}
