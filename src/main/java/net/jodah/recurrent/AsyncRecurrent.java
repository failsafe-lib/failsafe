package net.jodah.recurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import net.jodah.recurrent.internal.util.Assert;
import net.jodah.recurrent.util.concurrent.Scheduler;

/**
 * Performs asynchronous executions with retries according to a {@link RetryPolicy}.
 * 
 * @author Jonathan Halterman
 */
public class AsyncRecurrent {
  private final RetryPolicy retryPolicy;
  private final Scheduler scheduler;
  private Listeners<?> listeners;

  AsyncRecurrent(RetryPolicy retryPolicy, Scheduler scheduler) {
    this.retryPolicy = retryPolicy;
    this.scheduler = scheduler;
  }

  /**
   * Invokes the {@code callable} asynchronously until a successful result is returned or the configured
   * {@link RetryPolicy} is exceeded.
   * 
   * @throws NullPointerException if the {@code callable} is null
   */
  public <T> RecurrentFuture<T> get(Callable<T> callable) {
    return call(AsyncContextualCallable.of(callable), null);
  }

  /**
   * Invokes the {@code callable} asynchronously until a successful result is returned or the configured
   * {@link RetryPolicy} is exceeded.
   * 
   * @throws NullPointerException if the {@code callable} is null
   */
  public <T> RecurrentFuture<T> get(ContextualCallable<T> callable) {
    return call(AsyncContextualCallable.of(callable), null);
  }

  /**
   * Invokes the {@code callable} asynchronously until a successful result is returned or the configured
   * {@link RetryPolicy} is exceeded. This method is intended for integration with asynchronous code. Retries must be
   * manually scheduled via one of the {@code AsyncExecution.retry} methods.
   * 
   * @throws NullPointerException if the {@code callable} is null
   */
  public <T> RecurrentFuture<T> getAsync(AsyncCallable<T> callable) {
    return call(AsyncContextualCallable.of(callable), null);
  }

  /**
   * Invokes the {@code runnable} asynchronously until successful or until the configured {@link RetryPolicy} is
   * exceeded.
   * 
   * @throws NullPointerException if the {@code runnable} is null
   */
  public RecurrentFuture<Void> run(CheckedRunnable runnable) {
    return call(AsyncContextualCallable.<Void>of(runnable), null);
  }

  /**
   * Invokes the {@code runnable} asynchronously until successful or until the configured {@link RetryPolicy} is
   * exceeded.
   * 
   * @throws NullPointerException if the {@code runnable} is null
   */
  public RecurrentFuture<Void> run(ContextualRunnable runnable) {
    return call(AsyncContextualCallable.<Void>of(runnable), null);
  }

  /**
   * Invokes the {@code runnable} asynchronously until successful or until the configured {@link RetryPolicy} is
   * exceeded. This method is intended for integration with asynchronous code. Retries must be manually scheduled via
   * one of the {@code AsyncExecution.retry} methods.
   * 
   * @throws NullPointerException if the {@code runnable} is null
   */
  public RecurrentFuture<Void> runAsync(AsyncRunnable runnable) {
    return call(AsyncContextualCallable.<Void>of(runnable), null);
  }

  /**
   * Invokes the {@code callable} asynchronously until the resulting future is successfully completed or the configured
   * {@link RetryPolicy} is exceeded.
   * <p>
   * Supported on Java 8 and above.
   * 
   * @throws NullPointerException if the {@code callable} is null
   */
  @SuppressWarnings("unchecked")
  public <T> java.util.concurrent.CompletableFuture<T> future(
      Callable<java.util.concurrent.CompletableFuture<T>> callable) {
    java.util.concurrent.CompletableFuture<T> response = new java.util.concurrent.CompletableFuture<T>();
    call(AsyncContextualCallable.ofFuture(callable), RecurrentFuture.of(response, scheduler, (Listeners<T>) listeners));
    return response;
  }

  /**
   * Invokes the {@code callable} asynchronously until the resulting future is successfully completed or the configured
   * {@link RetryPolicy} is exceeded.
   * <p>
   * Supported on Java 8 and above.
   * 
   * @throws NullPointerException if the {@code callable} is null
   */
  @SuppressWarnings("unchecked")
  public <T> java.util.concurrent.CompletableFuture<T> future(
      ContextualCallable<java.util.concurrent.CompletableFuture<T>> callable) {
    java.util.concurrent.CompletableFuture<T> response = new java.util.concurrent.CompletableFuture<T>();
    call(AsyncContextualCallable.ofFuture(callable), RecurrentFuture.of(response, scheduler, (Listeners<T>) listeners));
    return response;
  }

  /**
   * Invokes the {@code callable} asynchronously until the resulting future is successfully completed or the configured
   * {@link RetryPolicy} is exceeded. This method is intended for integration with asynchronous code. Retries must be
   * manually scheduled via one of the {@code AsyncExecution.retry} methods.
   * <p>
   * Supported on Java 8 and above.
   * 
   * @throws NullPointerException if the {@code callable} is null
   */
  @SuppressWarnings("unchecked")
  public <T> java.util.concurrent.CompletableFuture<T> futureAsync(
      AsyncCallable<java.util.concurrent.CompletableFuture<T>> callable) {
    java.util.concurrent.CompletableFuture<T> response = new java.util.concurrent.CompletableFuture<T>();
    call(AsyncContextualCallable.ofFuture(callable), RecurrentFuture.of(response, scheduler, (Listeners<T>) listeners));
    return response;
  }

  /**
   * Configures the {@code listeners} to be called as execution events occur.
   */
  public <T extends Listeners<?>> AsyncRecurrent with(T listeners) {
    this.listeners = Assert.notNull(listeners, "listeners");
    return this;
  }

  /**
   * Calls the asynchronous {@code callable} via the {@code executor}, performing retries according to the
   * {@code retryPolicy}.
   * 
   * @throws NullPointerException if any argument is null
   */
  @SuppressWarnings("unchecked")
  private <T> RecurrentFuture<T> call(AsyncContextualCallable<T> callable, RecurrentFuture<T> future) {
    Listeners<T> typedListeners = (Listeners<T>) listeners;
    if (future == null)
      future = new RecurrentFuture<T>(scheduler, typedListeners);
    AsyncExecution execution = new AsyncExecution(callable, retryPolicy, scheduler, future, typedListeners);
    future.initialize(execution);
    callable.initialize(execution);
    try {
      future.setFuture((Future<T>) scheduler.schedule(callable, 0, TimeUnit.MILLISECONDS));
    } catch (Throwable t) {
      future.complete(null, t, false);
    }

    return future;
  }
}