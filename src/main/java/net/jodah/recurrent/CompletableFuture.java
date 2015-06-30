package net.jodah.recurrent;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.jodah.recurrent.internal.util.concurrent.InterruptableWaiter;

/**
 * A future that is completable.
 */
class CompletableFuture<T> implements ListenableFuture<T> {
  private Scheduler scheduler;
  private volatile Future<T> delegate;
  private volatile boolean done;
  private volatile CompletionListener<T> completionListener;
  private volatile CompletionListener<T> asyncCompletionListener;
  private volatile InterruptableWaiter waiter;
  private volatile T result;
  private volatile Throwable failure;

  CompletableFuture(Scheduler scheduler) {
    this.scheduler = scheduler;
  }

  void setFuture(Future<T> delegate) {
    this.delegate = delegate;
  }

  public void complete(T result, Throwable failure) {
    this.result = result;
    this.failure = failure;
    done = true;
    if (waiter != null)
      waiter.interruptWaiters();

    if (asyncCompletionListener != null)
      scheduler.schedule(Callables.callable(asyncCompletionListener, result, failure), 0, TimeUnit.MILLISECONDS);
    if (completionListener != null)
      completionListener.onCompletion(result, failure);
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
      scheduler.schedule(Callables.callable(completionListener, result, failure), 0, TimeUnit.MILLISECONDS);
    else
      this.completionListener = completionListener;
    return this;
  }

  @Override
  public ListenableFuture<T> whenCompleteAsync(CompletionListener<T> completionListener, Scheduler scheduler) {
    if (done)
      scheduler.schedule(Callables.callable(completionListener, result, failure), 0, TimeUnit.MILLISECONDS);
    else {
      this.asyncCompletionListener = completionListener;
      this.scheduler = scheduler;
    }
    return this;
  }
}