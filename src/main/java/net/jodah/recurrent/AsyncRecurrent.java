package net.jodah.recurrent;

import java.util.concurrent.Callable;

public interface AsyncRecurrent {
  /**
   * Invokes the {@code callable} asynchronously until the resulting future is successfully completed or the configured
   * {@link RetryPolicy} is exceeded.
   * 
   * @throws NullPointerException if the {@code callable} is null
   */
  <T> java.util.concurrent.CompletableFuture<T> future(Callable<java.util.concurrent.CompletableFuture<T>> callable);

  /**
   * Invokes the {@code callable} asynchronously until the resulting future is successfully completed or the configured
   * {@link RetryPolicy} is exceeded.
   * 
   * @throws NullPointerException if the {@code callable} is null
   */
  <T> java.util.concurrent.CompletableFuture<T> future(
      ContextualCallable<java.util.concurrent.CompletableFuture<T>> callable);

  /**
   * Invokes the {@code callable} asynchronously until the resulting future is successfully completed or the configured
   * {@link RetryPolicy} is exceeded. This method is intended for integration with asynchronous code. Retries must be
   * manually scheduled via one of the {@code AsyncInvocation.retry} methods.
   * 
   * @throws NullPointerException if the {@code callable} is null
   */
  <T> java.util.concurrent.CompletableFuture<T> futureAsync(
      AsyncCallable<java.util.concurrent.CompletableFuture<T>> callable);

  /**
   * Invokes the {@code callable} asynchronously until a successful result is returned or the configured
   * {@link RetryPolicy} is exceeded.
   * 
   * @throws NullPointerException if the {@code callable} is null
   */
  <T> RecurrentFuture<T> get(Callable<T> callable);

  /**
   * Invokes the {@code callable} asynchronously until a successful result is returned or the configured
   * {@link RetryPolicy} is exceeded.
   * 
   * @throws NullPointerException if the {@code callable} is null
   */
  <T> RecurrentFuture<T> get(ContextualCallable<T> callable);

  /**
   * Invokes the {@code callable} asynchronously until a successful result is returned or the configured
   * {@link RetryPolicy} is exceeded. This method is intended for integration with asynchronous code. Retries must be
   * manually scheduled via one of the {@code AsyncInvocation.retry} methods.
   * 
   * @throws NullPointerException if the {@code callable} is null
   */
  <T> RecurrentFuture<T> getAsync(AsyncCallable<T> callable);

  /**
   * Invokes the {@code runnable} asynchronously until successful or until the configured {@link RetryPolicy} is
   * exceeded.
   * 
   * @throws NullPointerException if the {@code runnable} is null
   */
  RecurrentFuture<Void> run(CheckedRunnable runnable);

  /**
   * Invokes the {@code runnable} asynchronously until successful or until the configured {@link RetryPolicy} is
   * exceeded.
   * 
   * @throws NullPointerException if the {@code runnable} is null
   */
  RecurrentFuture<Void> run(ContextualRunnable runnable);

  /**
   * Invokes the {@code runnable} asynchronously until successful or until the configured {@link RetryPolicy} is
   * exceeded. This method is intended for integration with asynchronous code. Retries must be manually scheduled via
   * one of the {@code AsyncInvocation.retry} methods.
   * 
   * @throws NullPointerException if the {@code runnable} is null
   */
  RecurrentFuture<Void> runAsync(AsyncRunnable runnable);

  /**
   * Configures the {@code listeners} to be called as invocation events occur.
   */
  <T extends Listeners<?>> AsyncRecurrent with(T listeners);
}