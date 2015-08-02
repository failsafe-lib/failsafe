package net.jodah.recurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.jodah.recurrent.event.CompletionListener;
import net.jodah.recurrent.event.FailureListener;
import net.jodah.recurrent.event.SuccessListener;
import net.jodah.recurrent.internal.util.concurrent.ReentrantCircuit;

/**
 * A future result of an asynchronous operation.
 * 
 * @author Jonathan Halterman
 * @param <T> result type
 */
public class RecurrentFuture<T> implements Future<T> {
  private final Scheduler scheduler;
  private volatile Future<T> delegate;
  private volatile boolean done;
  private volatile boolean cancelled;
  private volatile ReentrantCircuit circuit = new ReentrantCircuit();
  private volatile T result;
  private volatile Throwable failure;

  // Listeners
  private volatile CompletionListener<T> completionListener;
  private volatile CompletionListener<T> asyncCompletionListener;
  private volatile ExecutorService completionExecutor;
  private volatile SuccessListener<T> successListener;
  private volatile SuccessListener<T> asyncSuccessListener;
  private volatile ExecutorService successExecutor;
  private volatile FailureListener failureListener;
  private volatile FailureListener asyncFailureListener;
  private volatile ExecutorService failureExecutor;

  RecurrentFuture(Scheduler scheduler) {
    this.scheduler = scheduler;
    circuit.open();
  }

  static <T> RecurrentFuture<T> of(final java.util.concurrent.CompletableFuture<T> future, Scheduler scheduler) {
    return new RecurrentFuture<T>(scheduler).whenComplete(new CompletionListener<T>() {
      @Override
      public void onCompletion(T result, Throwable failure) {
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
    if (!circuit.await(timeout, unit))
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

  public RecurrentFuture<T> whenComplete(CompletionListener<T> completionListener) {
    if (done)
      completionListener.onCompletion(result, failure);
    else
      this.completionListener = completionListener;
    return this;
  }

  public RecurrentFuture<T> whenCompleteAsync(CompletionListener<T> completionListener) {
    if (done)
      scheduler.schedule(Callables.of(completionListener, result, failure), 0, TimeUnit.MILLISECONDS);
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
    if (done)
      failureListener.onFailure(failure);
    else
      this.failureListener = failureListener;
    return this;
  }

  public RecurrentFuture<T> whenFailureAsync(FailureListener failureListener) {
    if (done)
      scheduler.schedule(Callables.of(failureListener, failure), 0, TimeUnit.MILLISECONDS);
    else
      this.failureListener = failureListener;
    return this;
  }

  public RecurrentFuture<T> whenFailureAsync(FailureListener failureListener, ExecutorService executor) {
    if (done)
      executor.submit(Callables.of(failureListener, failure));
    else {
      this.asyncFailureListener = failureListener;
      this.failureExecutor = executor;
    }
    return this;
  }

  public RecurrentFuture<T> whenSuccess(SuccessListener<T> successListener) {
    if (done)
      successListener.onSuccess(result);
    else
      this.successListener = successListener;
    return this;
  }

  public RecurrentFuture<T> whenSuccessAsync(SuccessListener<T> successListener) {
    if (done)
      scheduler.schedule(Callables.of(successListener, result), 0, TimeUnit.MILLISECONDS);
    else
      this.successListener = successListener;
    return this;
  }

  public RecurrentFuture<T> whenSuccessAsync(SuccessListener<T> successListener, ExecutorService executor) {
    if (done)
      executor.submit(Callables.of(successListener, result));
    else {
      this.asyncSuccessListener = successListener;
      this.successExecutor = executor;
    }
    return this;
  }

  synchronized void complete(T result, Throwable failure) {
    this.result = result;
    this.failure = failure;
    done = true;
    circuit.close();

    // Async callbacks
    if (asyncCompletionListener != null)
      performAsyncCallback(Callables.of(asyncCompletionListener, result, failure), completionExecutor);
    if (failure == null) {
      if (asyncSuccessListener != null)
        performAsyncCallback(Callables.of(asyncSuccessListener, result), successExecutor);
    } else if (asyncFailureListener != null)
      performAsyncCallback(Callables.<T>of(asyncFailureListener, failure), failureExecutor);

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

  private void performAsyncCallback(Callable<T> callable, ExecutorService executor) {
    if (executor != null)
      executor.submit(callable);
    else
      scheduler.schedule(callable, 0, TimeUnit.MILLISECONDS);
  }
}
