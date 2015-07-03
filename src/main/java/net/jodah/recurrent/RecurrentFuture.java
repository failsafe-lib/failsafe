package net.jodah.recurrent;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.jodah.recurrent.event.CompletionListener;
import net.jodah.recurrent.event.FailureListener;
import net.jodah.recurrent.event.SuccessListener;
import net.jodah.recurrent.internal.util.concurrent.AwakableWaiter;

public class RecurrentFuture<T> implements Future<T> {
  private final ExecutorService executor;
  private volatile Future<T> delegate;
  private volatile boolean done;
  private volatile CompletionListener<T> completionListener;
  private volatile CompletionListener<T> asyncCompletionListener;
  private volatile ExecutorService completionExecutor;
  private volatile SuccessListener<T> successListener;
  private volatile SuccessListener<T> asyncSuccessListener;
  private volatile ExecutorService successExecutor;
  private volatile FailureListener failureListener;
  private volatile FailureListener asyncFailureListener;
  private volatile ExecutorService failureExecutor;
  private volatile AwakableWaiter waiter;
  private volatile T result;
  private volatile Throwable failure;

  RecurrentFuture(ScheduledExecutorService executor) {
    this.executor = executor;
  }

  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    boolean result = delegate.cancel(mayInterruptIfRunning);
    if (waiter != null)
      waiter.awakenWaiters();
    return result;
  }

  @Override
  public T get() throws InterruptedException, ExecutionException {
    if (!done) {
      if (waiter == null)
        waiter = new AwakableWaiter();
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
        waiter = new AwakableWaiter();
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

  public RecurrentFuture<T> whenComplete(CompletionListener<T> completionListener) {
    if (!done)
      this.completionListener = completionListener;
    else
      completionListener.onCompletion(result, failure);
    return this;
  }

  public RecurrentFuture<T> whenCompleteAsync(CompletionListener<T> completionListener) {
    if (done)
      executor.submit(Callables.of(completionListener, result, failure));
    else
      this.completionListener = completionListener;
    return this;
  }

  public RecurrentFuture<T> whenCompleteAsync(CompletionListener<T> completionListener, ExecutorService executor) {
    if (done)
      executor.submit(Callables.of(completionListener, result, failure));
    else {
      this.asyncCompletionListener = completionListener;
      this.completionExecutor = executor;
    }
    return this;
  }

  public RecurrentFuture<T> whenFailure(FailureListener failureListener) {
    if (!done)
      this.failureListener = failureListener;
    else
      failureListener.onFailure(failure);
    return this;
  }

  public RecurrentFuture<T> whenFailureAsync(FailureListener failureListener) {
    if (done)
      executor.submit(Callables.of(failureListener, failure));
    else
      this.failureListener = failureListener;
    return this;
  }

  public RecurrentFuture<T> whenFailureAsync(FailureListener failureListener, ScheduledExecutorService executor) {
    if (done)
      executor.submit(Callables.of(failureListener, failure));
    else {
      this.asyncFailureListener = failureListener;
      this.failureExecutor = executor;
    }
    return this;
  }

  public RecurrentFuture<T> whenSuccess(SuccessListener<T> successListener) {
    if (!done)
      this.successListener = successListener;
    else
      successListener.onSuccess(result);
    return this;
  }

  public RecurrentFuture<T> whenSuccessAsync(SuccessListener<T> successListener) {
    if (done)
      executor.submit(Callables.of(successListener, result));
    else
      this.successListener = successListener;
    return this;
  }

  public RecurrentFuture<T> whenSuccessAsync(SuccessListener<T> successListener, ScheduledExecutorService executor) {
    if (done)
      executor.submit(Callables.of(successListener, result));
    else {
      this.asyncSuccessListener = successListener;
      this.successExecutor = executor;
    }
    return this;
  }

  void complete(T result, Throwable failure) {
    this.result = result;
    this.failure = failure;
    done = true;
    if (waiter != null)
      waiter.awakenWaiters();

    // Async callbacks
    if (asyncCompletionListener != null)
      (completionExecutor == null ? executor : completionExecutor)
          .submit(Callables.of(asyncCompletionListener, result, failure));
    if (failure == null) {
      if (asyncSuccessListener != null)
        (successExecutor == null ? executor : successExecutor).submit(Callables.of(asyncSuccessListener, result));
    } else if (asyncFailureListener != null)
      (failureExecutor == null ? executor : failureExecutor).submit(Callables.of(asyncFailureListener, failure));

    // Sync callbacks
    if (completionListener != null)
      completionListener.onCompletion(result, failure);
    if (failure == null) {
      if (successListener != null)
        successListener.onSuccess(result);
    } else if (failureListener != null)
      failureListener.onFailure(failure);
  }

  void setFuture(Future<T> delegate) {
    this.delegate = delegate;
  }
}
