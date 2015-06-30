package net.jodah.recurrent;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.jodah.recurrent.event.CompletionListener;
import net.jodah.recurrent.event.FailureListener;
import net.jodah.recurrent.event.SuccessListener;
import net.jodah.recurrent.internal.util.concurrent.InterruptableWaiter;

/**
 * A future that is completable.
 */
class CompletableFuture<T> implements ListenableFuture<T> {
  private ScheduledExecutorService executor;
  private volatile Future<T> delegate;
  private volatile boolean done;
  private volatile CompletionListener<T> completionListener;
  private volatile CompletionListener<T> asyncCompletionListener;
  private volatile SuccessListener<T> successListener;
  private volatile SuccessListener<T> asyncSuccessListener;
  private volatile FailureListener failureListener;
  private volatile FailureListener asyncFailureListener;
  private volatile InterruptableWaiter waiter;
  private volatile T result;
  private volatile Throwable failure;

  CompletableFuture(ScheduledExecutorService executor) {
    this.executor = executor;
  }

  void setFuture(Future<T> delegate) {
    this.delegate = delegate;
  }

  void complete(T result, Throwable failure) {
    this.result = result;
    this.failure = failure;
    done = true;
    if (waiter != null)
      waiter.interruptWaiters();

    // Async callbacks
    if (asyncCompletionListener != null)
      executor.schedule(Callables.of(asyncCompletionListener, result, failure), 0, TimeUnit.MILLISECONDS);
    if (failure == null) {
      if (asyncSuccessListener != null)
        executor.schedule(Callables.of(asyncSuccessListener, result), 0, TimeUnit.MILLISECONDS);
    } else if (asyncFailureListener != null)
      executor.schedule(Callables.of(asyncFailureListener, failure), 0, TimeUnit.MILLISECONDS);

    // Sync callbacks
    if (completionListener != null)
      completionListener.onCompletion(result, failure);
    if (failure == null) {
      if (successListener != null)
        successListener.onSuccess(result);
    } else if (failureListener != null)
      failureListener.onFailure(failure);
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    boolean result = delegate.cancel(mayInterruptIfRunning);
    if (waiter != null)
      waiter.interruptWaiters();
    return result;
  }

  @Override
  public T get() throws InterruptedException, ExecutionException {
    if (!done) {
      if (waiter == null)
        waiter = new InterruptableWaiter();
      waiter.await();
    }

    if (failure != null)
      throw new ExecutionException(failure);
    return result;
  }

  @Override
  public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
    if (!done) {
      if (waiter == null)
        waiter = new InterruptableWaiter();
      if (!waiter.await(timeout, unit))
        throw new TimeoutException();
    }

    if (failure != null)
      throw new ExecutionException(failure);
    return result;
  }

  @Override
  public boolean isCancelled() {
    return delegate.isCancelled();
  }

  @Override
  public boolean isDone() {
    return done;
  }

  @Override
  public ListenableFuture<T> whenComplete(CompletionListener<T> completionListener) {
    if (!done)
      this.completionListener = completionListener;
    else
      completionListener.onCompletion(result, failure);
    return this;
  }

  @Override
  public ListenableFuture<T> whenCompleteAsync(CompletionListener<T> completionListener) {
    if (done)
      executor.schedule(Callables.of(completionListener, result, failure), 0, TimeUnit.MILLISECONDS);
    else
      this.completionListener = completionListener;
    return this;
  }

  @Override
  public ListenableFuture<T> whenCompleteAsync(CompletionListener<T> completionListener,
      ScheduledExecutorService executor) {
    if (done)
      executor.schedule(Callables.of(completionListener, result, failure), 0, TimeUnit.MILLISECONDS);
    else {
      this.asyncCompletionListener = completionListener;
      this.executor = executor;
    }
    return this;
  }

  @Override
  public ListenableFuture<T> whenFailure(FailureListener failureListener) {
    if (!done)
      this.failureListener = failureListener;
    else
      failureListener.onFailure(failure);
    return this;
  }

  @Override
  public ListenableFuture<T> whenFailureAsync(FailureListener failureListener) {
    if (done)
      executor.schedule(Callables.of(failureListener, failure), 0, TimeUnit.MILLISECONDS);
    else
      this.failureListener = failureListener;
    return this;
  }

  @Override
  public ListenableFuture<T> whenFailureAsync(FailureListener failureListener, ScheduledExecutorService executor) {
    if (done)
      executor.schedule(Callables.of(failureListener, failure), 0, TimeUnit.MILLISECONDS);
    else {
      this.asyncFailureListener = failureListener;
      this.executor = executor;
    }
    return this;
  }

  @Override
  public ListenableFuture<T> whenSuccess(SuccessListener<T> successListener) {
    if (!done)
      this.successListener = successListener;
    else
      successListener.onSuccess(result);
    return this;
  }

  @Override
  public ListenableFuture<T> whenSuccessAsync(SuccessListener<T> successListener) {
    if (done)
      executor.schedule(Callables.of(successListener, result), 0, TimeUnit.MILLISECONDS);
    else
      this.successListener = successListener;
    return this;
  }

  @Override
  public ListenableFuture<T> whenSuccessAsync(SuccessListener<T> successListener, ScheduledExecutorService executor) {
    if (done)
      executor.schedule(Callables.of(successListener, result), 0, TimeUnit.MILLISECONDS);
    else {
      this.asyncSuccessListener = successListener;
      this.executor = executor;
    }
    return this;
  }
}