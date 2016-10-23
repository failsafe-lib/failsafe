/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package net.jodah.failsafe;

import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;

import net.jodah.failsafe.Functions.ContextualCallableWrapper;
import net.jodah.failsafe.function.CheckedRunnable;
import net.jodah.failsafe.function.ContextualCallable;
import net.jodah.failsafe.function.ContextualRunnable;
import net.jodah.failsafe.internal.util.Assert;
import net.jodah.failsafe.util.concurrent.Scheduler;
import net.jodah.failsafe.util.concurrent.Schedulers;

/**
 * Performs synchronous executions with failures handled according to a configured {@link #with(RetryPolicy) retry
 * policy}, {@link #with(CircuitBreaker) circuit breaker} and
 * {@link #withFallback(net.jodah.failsafe.function.BiFunction) fallback}.
 * 
 * @author Jonathan Halterman
 * @param <R> listener result type
 */
public class SyncFailsafe<R> extends FailsafeConfig<R, SyncFailsafe<R>> {
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
    return call(Functions.callableOf(callable));
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
    call(Functions.callableOf(runnable));
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
    call(Functions.callableOf(runnable));
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
   * Calls the {@code callable} synchronously, performing retries according to the {@code retryPolicy}.
   * 
   * @throws FailsafeException if the {@code callable} fails with a Throwable and the retry policy is exceeded or if
   *           interrupted while waiting to perform a retry
   * @throws CircuitBreakerOpenException if a configured circuit breaker is open
   */
  @SuppressWarnings("unchecked")
  private <T> T call(Callable<T> callable) {
    Execution execution = new Execution((FailsafeConfig<Object, ?>) this);

    // Handle contextual calls
    if (callable instanceof ContextualCallableWrapper)
      ((ContextualCallableWrapper<T>) callable).inject(execution);

    T result = null;
    Throwable failure;

    while (true) {
      if (circuitBreaker != null && !circuitBreaker.allowsExecution()) {
        CircuitBreakerOpenException e = new CircuitBreakerOpenException();
        if (fallback != null)
          return fallbackFor((R) result, e);
        throw e;
      }

      try {
        execution.before();
        failure = null;
        result = callable.call();
      } catch (Throwable t) {
        // Re-throw nested execution interruptions
        if (t instanceof FailsafeException && InterruptedException.class.isInstance(t.getCause()))
          throw (FailsafeException) t;
        failure = t;
      }

      // Attempt to complete execution
      if (execution.complete(result, failure, true)) {
        if (execution.success || (failure == null && fallback == null))
          return result;
        if (fallback != null)
          return fallbackFor((R) result, failure);
        throw failure instanceof FailsafeException ? (FailsafeException) failure : new FailsafeException(failure);
      } else {
        try {
          Thread.sleep(execution.getWaitTime().toMillis());
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new FailsafeException(e);
        }

        handleRetry((R) result, failure, execution);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private <T> T fallbackFor(R result, Throwable failure) {
    try {
      return (T) fallback.apply(result, failure);
    } catch (Exception e) {
      throw e instanceof FailsafeException ? (FailsafeException) e : new FailsafeException(e);
    }
  }
}