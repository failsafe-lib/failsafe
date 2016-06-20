package net.jodah.failsafe;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import net.jodah.failsafe.Callables.AsyncCallableWrapper;
import net.jodah.failsafe.function.AsyncCallable;
import net.jodah.failsafe.function.AsyncRunnable;
import net.jodah.failsafe.function.CheckedRunnable;
import net.jodah.failsafe.function.ContextualCallable;
import net.jodah.failsafe.function.ContextualRunnable;
import net.jodah.failsafe.internal.util.Assert;
import net.jodah.failsafe.util.concurrent.Scheduler;

/**
 * Performs asynchronous executions according to a {@link RetryPolicy} and {@link CircuitBreaker}.
 * 
 * @author Jonathan Halterman
 * @param <L> listener result type
 */
public class AsyncFailsafe<L> extends AsyncListenerBindings<AsyncFailsafe<L>, L> {
  private RetryPolicy retryPolicy;
  private CircuitBreaker circuitBreaker;

  AsyncFailsafe(SyncFailsafe<L> failsafe, Scheduler scheduler) {
    super(scheduler);
    this.retryPolicy = failsafe.retryPolicy;
    this.circuitBreaker = failsafe.circuitBreaker;
    this.listeners = failsafe.listeners;
    this.listenerConfig = failsafe.listenerConfig;
  }

  /**
   * Executes the {@code callable} asynchronously until a successful result is returned or the configured
   * {@link RetryPolicy} is exceeded.
   * 
   * @throws NullPointerException if the {@code callable} is null
   * @throws CircuitBreakerOpenException if a configured circuit breaker is open
   */
  public <T> FailsafeFuture<T> get(Callable<T> callable) {
    return call(Callables.asyncOf(callable), null);
  }

  /**
   * Executes the {@code callable} asynchronously until a successful result is returned or the configured
   * {@link RetryPolicy} is exceeded.
   * 
   * @throws NullPointerException if the {@code callable} is null
   * @throws CircuitBreakerOpenException if a configured circuit breaker is open
   */
  public <T> FailsafeFuture<T> get(ContextualCallable<T> callable) {
    return call(Callables.asyncOf(callable), null);
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
    return call(Callables.asyncOf(callable), null);
  }

  /**
   * Executes the {@code runnable} asynchronously until successful or until the configured {@link RetryPolicy} is
   * exceeded.
   * 
   * @throws NullPointerException if the {@code runnable} is null
   * @throws CircuitBreakerOpenException if a configured circuit breaker is open
   */
  public FailsafeFuture<Void> run(CheckedRunnable runnable) {
    return call(Callables.<Void>asyncOf(runnable), null);
  }

  /**
   * Executes the {@code runnable} asynchronously until successful or until the configured {@link RetryPolicy} is
   * exceeded.
   * 
   * @throws NullPointerException if the {@code runnable} is null
   * @throws CircuitBreakerOpenException if a configured circuit breaker is open
   */
  public FailsafeFuture<Void> run(ContextualRunnable runnable) {
    return call(Callables.<Void>asyncOf(runnable), null);
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
    return call(Callables.<Void>asyncOf(runnable), null);
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
  @SuppressWarnings("unchecked")
  public <T> java.util.concurrent.CompletableFuture<T> future(
      Callable<java.util.concurrent.CompletableFuture<T>> callable) {
    java.util.concurrent.CompletableFuture<T> response = new java.util.concurrent.CompletableFuture<T>();
    call(Callables.ofFuture(callable), new FailsafeFuture<T>(response, (ListenerBindings<?, T>) this));
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
  @SuppressWarnings("unchecked")
  public <T> java.util.concurrent.CompletableFuture<T> future(
      ContextualCallable<java.util.concurrent.CompletableFuture<T>> callable) {
    java.util.concurrent.CompletableFuture<T> response = new java.util.concurrent.CompletableFuture<T>();
    call(Callables.ofFuture(callable), new FailsafeFuture<T>(response, (ListenerBindings<?, T>) this));
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
  @SuppressWarnings("unchecked")
  public <T> java.util.concurrent.CompletableFuture<T> futureAsync(
      AsyncCallable<java.util.concurrent.CompletableFuture<T>> callable) {
    java.util.concurrent.CompletableFuture<T> response = new java.util.concurrent.CompletableFuture<T>();
    call(Callables.ofFuture(callable), new FailsafeFuture<T>(response, (ListenerBindings<?, T>) this));
    return response;
  }

  /**
   * Configures the {@code circuitBreaker} to be used to control the rate of event execution.
   * 
   * @throws NullPointerException if {@code circuitBreaker} is null
   * @throws IllegalStateException if a circuit breaker is already configured
   */
  public AsyncFailsafe<L> with(CircuitBreaker circuitBreaker) {
    Assert.state(this.circuitBreaker == null, "A circuit breaker has already been configurd");
    this.circuitBreaker = Assert.notNull(circuitBreaker, "circuitBreaker");
    return this;
  }

  /**
   * Configures the {@code retryPolicy} to be used for retrying failed executions.
   * 
   * @throws NullPointerException if {@code retryPolicy} is null
   * @throws IllegalStateException if a retry policy is already configured
   */
  public AsyncFailsafe<L> with(RetryPolicy retryPolicy) {
    Assert.state(this.retryPolicy == RetryPolicy.NEVER, "A retry policy has already been configurd");
    this.retryPolicy = Assert.notNull(retryPolicy, "retryPolicy");
    return this;
  }

  /**
   * Configures the {@code listeners} to be called as execution events occur.
   * 
   * @throws NullPointerException if {@code listeners} is null
   */
  @SuppressWarnings("unchecked")
  public <T> AsyncFailsafe<T> with(Listeners<T> listeners) {
    this.listeners = (Listeners<L>) Assert.notNull(listeners, "listeners");
    return (AsyncFailsafe<T>) this;
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
    if (circuitBreaker != null) {
      circuitBreaker.initialize();
      if (!circuitBreaker.allowsExecution())
        throw new CircuitBreakerOpenException();
    }

    if (future == null)
      future = new FailsafeFuture<T>((ListenerBindings<?, T>) this);
    AsyncExecution execution = new AsyncExecution(callable, retryPolicy, circuitBreaker, scheduler, future,
        (ListenerBindings<?, ?>) this);
    callable.inject(execution);
    future.inject(execution);

    try {
      future.setFuture((Future<T>) scheduler.schedule(callable, 0, TimeUnit.MILLISECONDS));
    } catch (Throwable t) {
      future.complete(null, t, false);
    }

    return future;
  }
}