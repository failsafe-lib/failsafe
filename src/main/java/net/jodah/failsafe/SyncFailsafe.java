package net.jodah.failsafe;

import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;

import net.jodah.failsafe.Callables.ContextualCallableWrapper;
import net.jodah.failsafe.function.BiFunction;
import net.jodah.failsafe.function.CheckedRunnable;
import net.jodah.failsafe.function.ContextualCallable;
import net.jodah.failsafe.function.ContextualRunnable;
import net.jodah.failsafe.function.Function;
import net.jodah.failsafe.internal.util.Assert;
import net.jodah.failsafe.util.concurrent.Scheduler;
import net.jodah.failsafe.util.concurrent.Schedulers;

/**
 * Performs synchronous executions according to a {@link RetryPolicy} and {@link CircuitBreaker}.
 * 
 * @author Jonathan Halterman
 * @param <R> listener result type
 */
public class SyncFailsafe<R> extends ListenerConfig<SyncFailsafe<R>, R> {
  RetryPolicy retryPolicy = RetryPolicy.NEVER;
  CircuitBreaker circuitBreaker;
  BiFunction<R, Throwable, R> fallback;

  SyncFailsafe(CircuitBreaker circuitBreaker) {
    this.circuitBreaker = circuitBreaker;
  }

  SyncFailsafe(RetryPolicy retryPolicy) {
    this.retryPolicy = retryPolicy;
  }

  /**
   * Executes the {@code callable} until a successful result is returned or the configured {@link RetryPolicy} is
   * exceeded.
   * 
   * @throws NullPointerException if the {@code callable} is null
   * @throws FailsafeException if the {@code callable} fails with a Throwable and the retry policy is exceeded, or if
   *           interrupted while waiting to perform a retry.
   * @throws CircuitBreakerOpenException if a configured circuit is open.
   */
  public <T> T get(Callable<T> callable) {
    return call(Assert.notNull(callable, "callable"));
  }

  /**
   * Executes the {@code callable} until a successful result is returned or the configured {@link RetryPolicy} is
   * exceeded.
   * 
   * @throws NullPointerException if the {@code callable} is null
   * @throws FailsafeException if the {@code callable} fails with a Throwable and the retry policy is exceeded, or if
   *           interrupted while waiting to perform a retry.
   * @throws CircuitBreakerOpenException if a configured circuit is open.
   */
  public <T> T get(ContextualCallable<T> callable) {
    return call(Callables.of(callable));
  }

  /**
   * Executes the {@code runnable} until successful or until the configured {@link RetryPolicy} is exceeded.
   * 
   * @throws NullPointerException if the {@code runnable} is null
   * @throws FailsafeException if the {@code callable} fails with a Throwable and the retry policy is exceeded, or if
   *           interrupted while waiting to perform a retry.
   * @throws CircuitBreakerOpenException if a configured circuit is open.
   */
  public void run(CheckedRunnable runnable) {
    call(Callables.of(runnable));
  }

  /**
   * Executes the {@code runnable} until successful or until the configured {@link RetryPolicy} is exceeded.
   * 
   * @throws NullPointerException if the {@code runnable} is null
   * @throws FailsafeException if the {@code callable} fails with a Throwable and the retry policy is exceeded, or if
   *           interrupted while waiting to perform a retry.
   * @throws CircuitBreakerOpenException if a configured circuit is open.
   */
  public void run(ContextualRunnable runnable) {
    call(Callables.of(runnable));
  }

  /**
   * Configures the {@code circuitBreaker} to be used to control the rate of event execution.
   * 
   * @throws NullPointerException if {@code circuitBreaker} is null
   * @throws IllegalStateException if a circuit breaker is already configured
   */
  public SyncFailsafe<R> with(CircuitBreaker circuitBreaker) {
    Assert.state(this.circuitBreaker == null, "A circuit breaker has already been configured");
    this.circuitBreaker = Assert.notNull(circuitBreaker, "circuitBreaker");
    return this;
  }

  /**
   * Configures the {@code listeners} to be called as execution events occur.
   * 
   * @throws NullPointerException if {@code listeners} is null
   */
  @SuppressWarnings("unchecked")
  public <T> SyncFailsafe<T> with(Listeners<T> listeners) {
    this.listeners = (Listeners<R>) Assert.notNull(listeners, "listeners");
    return (SyncFailsafe<T>) this;
  }

  /**
   * Configures the {@code retryPolicy} to be used for retrying failed executions.
   * 
   * @throws NullPointerException if {@code retryPolicy} is null
   * @throws IllegalStateException if a retry policy is already configured
   */
  public SyncFailsafe<R> with(RetryPolicy retryPolicy) {
    Assert.state(this.retryPolicy == RetryPolicy.NEVER, "A retry policy has already been configured");
    this.retryPolicy = Assert.notNull(retryPolicy, "retryPolicy");
    return this;
  }

  /**
   * Creates and returns a new AsyncFailsafe instance that will perform executions and retries asynchronously via the
   * {@code executor}.
   * 
   * @throws NullPointerException if {@code executor} is null
   */
  public AsyncFailsafe<R> with(ScheduledExecutorService executor) {
    return new AsyncFailsafe<R>(this, Schedulers.of(executor));
  }

  /**
   * Creates and returns a new AsyncFailsafe instance that will perform executions and retries asynchronously via the
   * {@code scheduler}.
   * 
   * @throws NullPointerException if {@code scheduler} is null
   */
  public AsyncFailsafe<R> with(Scheduler scheduler) {
    return new AsyncFailsafe<R>(this, Assert.notNull(scheduler, "scheduler"));
  }

  /**
   * Configures the {@code fallback} action to be executed if execution fails.
   * 
   * @throws NullPointerException if {@code fallback} is null
   * @throws IllegalStateException if {@code withFallback} method has already been called
   */
  @SuppressWarnings("unchecked")
  public SyncFailsafe<R> withFallback(BiFunction<? extends R, ? extends Throwable, ? extends R> fallback) {
    Assert.state(this.fallback == null, "withFallback has already been called");
    this.fallback = (BiFunction<R, Throwable, R>) Assert.notNull(fallback, "fallback");
    return this;
  }

  /**
   * Configures the {@code fallback} action to be executed if execution fails.
   * 
   * @throws NullPointerException if {@code fallback} is null
   * @throws IllegalStateException if {@code withFallback} method has already been called
   */
  @SuppressWarnings("unchecked")
  public SyncFailsafe<R> withFallback(Function<? extends Throwable, ? extends R> fallback) {
    Assert.state(this.fallback == null, "withFallback has already been called");
    this.fallback = (BiFunction<R, Throwable, R>) Callables
        .<R, Throwable, R>of((Function<Throwable, R>) Assert.notNull(fallback, "fallback"));
    return this;
  }

  /**
   * Configures the {@code fallback} result to be returned if execution fails.
   * 
   * @throws NullPointerException if {@code fallback} is null
   * @throws IllegalStateException if {@code withFallback} method has already been called
   */
  public SyncFailsafe<R> withFallback(R fallback) {
    Assert.state(this.fallback == null, "withFallback has already been called");
    this.fallback = Callables.of(Assert.notNull(fallback, "fallback"));
    return this;
  }

  /**
   * Calls the {@code callable} synchronously, performing retries according to the {@code retryPolicy}.
   * 
   * @throws FailsafeException if the {@code callable} fails with a Throwable and the retry policy is exceeded or if
   *           interrupted while waiting to perform a retry
   * @throws CircuitBreakerOpenException if a configured circuit breaker is open
   */
  @SuppressWarnings("unchecked")
  private <T> T call(Callable<T> callable) {
    Execution execution = new Execution(retryPolicy, circuitBreaker, (ListenerConfig<?, Object>) this);

    // Handle contextual calls
    if (callable instanceof ContextualCallableWrapper)
      ((ContextualCallableWrapper<T>) callable).inject(execution);

    T result = null;
    Throwable failure;

    while (true) {
      if (circuitBreaker != null && !circuitBreaker.allowsExecution()) {
        CircuitBreakerOpenException e = new CircuitBreakerOpenException();
        if (fallback == null)
          throw e;
        return (T) fallback.apply((R) result, e);
      }

      try {
        execution.before();
        failure = null;
        result = callable.call();
      } catch (Throwable t) {
        failure = t;
      }

      // Attempt to complete execution
      if (execution.complete(result, failure, true)) {
        if (execution.success || failure == null)
          return result;
        if (fallback != null)
          return (T) fallback.apply((R) result, failure);
        throw failure instanceof FailsafeException ? (FailsafeException) failure : new FailsafeException(failure);
      } else {
        try {
          Thread.sleep(execution.getWaitTime().toMillis());
        } catch (InterruptedException e) {
          throw new FailsafeException(e);
        }

        handleRetry((R) result, failure, execution);
      }
    }
  }
}