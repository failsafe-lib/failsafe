package net.jodah.failsafe;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import net.jodah.failsafe.Functions.AsyncCallableWrapper;
import net.jodah.failsafe.function.AsyncCallable;
import net.jodah.failsafe.function.AsyncRunnable;
import net.jodah.failsafe.function.CheckedBiFunction;
import net.jodah.failsafe.function.CheckedRunnable;
import net.jodah.failsafe.function.ContextualCallable;
import net.jodah.failsafe.function.ContextualRunnable;
import net.jodah.failsafe.util.concurrent.Scheduler;

/**
 * Performs asynchronous executions with failures handled according to a configured {@link #with(RetryPolicy) retry
 * policy}, {@link #with(CircuitBreaker) circuit breaker} and
 * {@link #withFallback(net.jodah.failsafe.function.CheckedBiFunction) fallback}.
 * 
 * @author Jonathan Halterman
 * @param <R> listener result type
 */
public class AsyncFailsafe<R> extends AsyncFailsafeConfig<R, AsyncFailsafe<R>> {
  AsyncFailsafe(FailsafeConfig<R, ?> config, Scheduler scheduler) {
    super(config, scheduler);
  }

  /**
   * Executes the {@code callable} asynchronously until the resulting future is successfully completed or the configured
   * {@link RetryPolicy} is exceeded.
   * <p>
   * Supported on Java 8 and above.
   * 
   * @throws NullPointerException if the {@code callable} is null
   * @throws CircuitBreakerOpenException if a configured circuit breaker is open
   */
  public <T> java.util.concurrent.CompletableFuture<T> future(
      Callable<java.util.concurrent.CompletableFuture<T>> callable) {
    FailsafeFuture<T> future = new FailsafeFuture<T>();
    java.util.concurrent.CompletableFuture<T> response = Functions.cancellableFutureOf(future);
    future.setCompletableFuture(response);
    call(Functions.asyncOfFuture(callable), future);
    return response;
  }

  /**
   * Executes the {@code callable} asynchronously until the resulting future is successfully completed or the configured
   * {@link RetryPolicy} is exceeded.
   * <p>
   * Supported on Java 8 and above.
   * 
   * @throws NullPointerException if the {@code callable} is null
   * @throws CircuitBreakerOpenException if a configured circuit breaker is open
   */
  public <T> java.util.concurrent.CompletableFuture<T> future(
      ContextualCallable<java.util.concurrent.CompletableFuture<T>> callable) {
    FailsafeFuture<T> future = new FailsafeFuture<T>();
    java.util.concurrent.CompletableFuture<T> response = Functions.cancellableFutureOf(future);
    future.setCompletableFuture(response);
    call(Functions.asyncOfFuture(callable), future);
    return response;
  }

  /**
   * Executes the {@code callable} asynchronously until the resulting future is successfully completed or the configured
   * {@link RetryPolicy} is exceeded. This method is intended for integration with asynchronous code. Retries must be
   * manually scheduled via one of the {@code AsyncExecution.retry} methods.
   * <p>
   * Supported on Java 8 and above.
   * 
   * @throws NullPointerException if the {@code callable} is null
   * @throws CircuitBreakerOpenException if a configured circuit breaker is open
   */
  public <T> java.util.concurrent.CompletableFuture<T> futureAsync(
      AsyncCallable<java.util.concurrent.CompletableFuture<T>> callable) {
    FailsafeFuture<T> future = new FailsafeFuture<T>();
    java.util.concurrent.CompletableFuture<T> response = Functions.cancellableFutureOf(future);
    future.setCompletableFuture(response);
    call(Functions.asyncOfFuture(callable), future);
    return response;
  }

  /**
   * Executes the {@code callable} asynchronously until a successful result is returned or the configured
   * {@link RetryPolicy} is exceeded.
   * 
   * @throws NullPointerException if the {@code callable} is null
   * @throws CircuitBreakerOpenException if a configured circuit breaker is open
   */
  public <T> FailsafeFuture<T> get(Callable<T> callable) {
    return call(Functions.asyncOf(callable), null);
  }

  /**
   * Executes the {@code callable} asynchronously until a successful result is returned or the configured
   * {@link RetryPolicy} is exceeded.
   * 
   * @throws NullPointerException if the {@code callable} is null
   * @throws CircuitBreakerOpenException if a configured circuit breaker is open
   */
  public <T> FailsafeFuture<T> get(ContextualCallable<T> callable) {
    return call(Functions.asyncOf(callable), null);
  }

  /**
   * Executes the {@code callable} asynchronously until a successful result is returned or the configured
   * {@link RetryPolicy} is exceeded. This method is intended for integration with asynchronous code. Retries must be
   * manually scheduled via one of the {@code AsyncExecution.retry} methods.
   * 
   * @throws NullPointerException if the {@code callable} is null
   * @throws CircuitBreakerOpenException if a configured circuit breaker is open
   */
  public <T> FailsafeFuture<T> getAsync(AsyncCallable<T> callable) {
    return call(Functions.asyncOf(callable), null);
  }

  /**
   * Executes the {@code runnable} asynchronously until successful or until the configured {@link RetryPolicy} is
   * exceeded.
   * 
   * @throws NullPointerException if the {@code runnable} is null
   * @throws CircuitBreakerOpenException if a configured circuit breaker is open
   */
  public FailsafeFuture<Void> run(CheckedRunnable runnable) {
    return call(Functions.<Void>asyncOf(runnable), null);
  }

  /**
   * Executes the {@code runnable} asynchronously until successful or until the configured {@link RetryPolicy} is
   * exceeded.
   * 
   * @throws NullPointerException if the {@code runnable} is null
   * @throws CircuitBreakerOpenException if a configured circuit breaker is open
   */
  public FailsafeFuture<Void> run(ContextualRunnable runnable) {
    return call(Functions.<Void>asyncOf(runnable), null);
  }

  /**
   * Executes the {@code runnable} asynchronously until successful or until the configured {@link RetryPolicy} is
   * exceeded. This method is intended for integration with asynchronous code. Retries must be manually scheduled via
   * one of the {@code AsyncExecution.retry} methods.
   * 
   * @throws NullPointerException if the {@code runnable} is null
   * @throws CircuitBreakerOpenException if a configured circuit breaker is open
   */
  public FailsafeFuture<Void> runAsync(AsyncRunnable runnable) {
    return call(Functions.<Void>asyncOf(runnable), null);
  }

  /**
   * Calls the asynchronous {@code callable} via the {@code executor}, performing retries according to the
   * {@code retryPolicy}.
   * 
   * @throws NullPointerException if any argument is null
   * @throws CircuitBreakerOpenException if a configured circuit breaker is open
   */
  @SuppressWarnings("unchecked")
  private <T> FailsafeFuture<T> call(AsyncCallableWrapper<T> callable, FailsafeFuture<T> future) {
    if (future == null)
      future = new FailsafeFuture<T>();

    if (circuitBreaker != null && !circuitBreaker.allowsExecution()) {
      CircuitBreakerOpenException e = new CircuitBreakerOpenException();
      if (fallback == null)
        throw e;
      future.complete(null, e, (CheckedBiFunction<T, Throwable, T>) fallback);
      return future;
    }

    AsyncExecution execution = new AsyncExecution(callable, scheduler, future, (FailsafeConfig<Object, ?>) this);
    callable.inject(execution);

    try {
      future.setFuture((Future<T>) scheduler.schedule(callable, 0, TimeUnit.MILLISECONDS));
    } catch (Throwable t) {
      handleComplete(null, t, execution, false);
      future.complete(null, t, (CheckedBiFunction<T, Throwable, T>) fallback);
    }

    return future;
  }
}