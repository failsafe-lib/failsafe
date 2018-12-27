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

import net.jodah.failsafe.Listeners.ListenerRegistry;
import net.jodah.failsafe.event.ContextualResultListener;
import net.jodah.failsafe.function.*;
import net.jodah.failsafe.internal.util.Assert;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

/**
 * Failsafe configuration.
 *
 * @author Jonathan Halterman
 * @param <R> result type
 * @param <F> failsafe type - {@link SyncFailsafe} or {@link AsyncFailsafe}
 */
@SuppressWarnings({"WeakerAccess", "UnusedReturnValue", "unchecked"})
public class FailsafeConfig<R, F> {
  RetryPolicy retryPolicy = RetryPolicy.NEVER;
  CircuitBreaker circuitBreaker;
  Fallback fallback;
  /** Policies sorted outer-most first */
  List<Policy> policies;
  ListenerRegistry<R> listeners = new ListenerRegistry<>();

  FailsafeConfig() {
  }

  FailsafeConfig(List<Policy> policies) {
    Assert.isTrue(policies.size() > 0, "At least one policy must be supplied");
    this.policies = policies;
  }

  FailsafeConfig(FailsafeConfig<R, ?> config) {
    retryPolicy = config.retryPolicy;
    circuitBreaker = config.circuitBreaker;
    fallback = config.fallback;
    policies = config.policies;
    listeners = config.listeners;
  }

  /**
   * Registers the {@code listener} to be called when an execution is aborted.
   */
  public F onAbort(CheckedBiConsumer<? extends R, ? extends Throwable> listener) {
    listeners.abort().add(Listeners.of(listener));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution is aborted.
   */
  public F onAbort(CheckedConsumer<? extends Throwable> listener) {
    listeners.abort().add(Listeners.of(listener));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution is aborted.
   */
  public F onAbort(ContextualResultListener<? extends R, ? extends Throwable> listener) {
    listeners.abort().add((ContextualResultListener<R, Throwable>) Assert.notNull(listener, "listener"));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution is aborted.
   */
  public F onAbortAsync(CheckedBiConsumer<? extends R, ? extends Throwable> listener, ExecutorService executor) {
    listeners.abort().add(Listeners.of(Listeners.of(listener), Assert.notNull(executor, "executor"), null));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution is aborted.
   */
  public F onAbortAsync(CheckedConsumer<? extends Throwable> listener, ExecutorService executor) {
    listeners.abort().add(Listeners.of(Listeners.of(listener), Assert.notNull(executor, "executor"), null));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution is aborted.
   */
  public F onAbortAsync(ContextualResultListener<? extends R, ? extends Throwable> listener, ExecutorService executor) {
    listeners.abort().add(Listeners.of(listener, Assert.notNull(executor, "executor"), null));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution is completed.
   */
  public F onComplete(CheckedBiConsumer<? extends R, ? extends Throwable> listener) {
    listeners.complete().add(Listeners.of(listener));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution is completed.
   */
  public F onComplete(ContextualResultListener<? extends R, ? extends Throwable> listener) {
    listeners.complete().add((ContextualResultListener<R, Throwable>) Assert.notNull(listener, "listener"));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution is completed.
   */
  public F onCompleteAsync(CheckedBiConsumer<? extends R, ? extends Throwable> listener, ExecutorService executor) {
    listeners.complete().add(Listeners.of(Listeners.of(listener), Assert.notNull(executor, "executor"), null));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution is completed.
   */
  public F onCompleteAsync(ContextualResultListener<? extends R, ? extends Throwable> listener,
      ExecutorService executor) {
    listeners.complete().add(Listeners.of(listener, Assert.notNull(executor, "executor"), null));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution attempt fails.
   */
  public F onFailedAttempt(CheckedBiConsumer<? extends R, ? extends Throwable> listener) {
    listeners.failedAttempt().add(Listeners.of(listener));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution attempt fails.
   */
  public F onFailedAttempt(CheckedConsumer<? extends Throwable> listener) {
    listeners.failedAttempt().add(Listeners.of(listener));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution attempt fails.
   */
  public F onFailedAttempt(ContextualResultListener<? extends R, ? extends Throwable> listener) {
    listeners.failedAttempt().add((ContextualResultListener<R, Throwable>) Assert.notNull(listener, "listener"));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution attempt fails.
   */
  public F onFailedAttemptAsync(CheckedBiConsumer<? extends R, ? extends Throwable> listener,
      ExecutorService executor) {
    listeners.failedAttempt().add(Listeners.of(Listeners.of(listener), Assert.notNull(executor, "executor"), null));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution attempt fails.
   */
  public F onFailedAttemptAsync(CheckedConsumer<? extends Throwable> listener, ExecutorService executor) {
    listeners.failedAttempt().add(Listeners.of(Listeners.of(listener), Assert.notNull(executor, "executor"), null));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution attempt fails.
   */
  public F onFailedAttemptAsync(ContextualResultListener<? extends R, ? extends Throwable> listener,
      ExecutorService executor) {
    listeners.failedAttempt().add(Listeners.of(listener, Assert.notNull(executor, "executor"), null));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution fails and cannot be retried. If multiple policies,
   * are configured, this handler is called when the outer-most policy fails.
   */
  public F onFailure(CheckedBiConsumer<? extends R, ? extends Throwable> listener) {
    listeners.failure().add(Listeners.of(listener));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution fails and cannot be retried. If multiple policies,
   * are configured, this handler is called when the outer-most policy fails.
   */
  public F onFailure(CheckedConsumer<? extends Throwable> listener) {
    listeners.failure().add(Listeners.of(listener));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution fails and cannot be retried. If multiple policies,
   * are configured, this handler is called when the outer-most policy fails.
   */
  public F onFailure(ContextualResultListener<? extends R, ? extends Throwable> listener) {
    listeners.failure().add((ContextualResultListener<R, Throwable>) Assert.notNull(listener, "listener"));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution fails and
   * cannot be retried. If multiple policies, are configured, this handler is called when the outer-most policy fails.
   */
  public F onFailureAsync(CheckedBiConsumer<? extends R, ? extends Throwable> listener, ExecutorService executor) {
    listeners.failure().add(Listeners.of(Listeners.of(listener), Assert.notNull(executor, "executor"), null));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution fails and
   * cannot be retried. If multiple policies, are configured, this handler is called when the outer-most policy fails.
   */
  public F onFailureAsync(CheckedConsumer<? extends Throwable> listener, ExecutorService executor) {
    listeners.failure().add(Listeners.of(Listeners.of(listener), Assert.notNull(executor, "executor"), null));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution fails and
   * cannot be retried. If multiple policies, are configured, this handler is called when the outer-most policy fails.
   */
  public F onFailureAsync(ContextualResultListener<? extends R, ? extends Throwable> listener,
      ExecutorService executor) {
    listeners.failure().add(Listeners.of(listener, Assert.notNull(executor, "executor"), null));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution fails and the {@link RetryPolicy#withMaxRetries(int)
   * max retry attempts} or {@link RetryPolicy#withMaxDuration(long, java.util.concurrent.TimeUnit) max duration} are
   * exceeded.
   */
  public F onRetriesExceeded(CheckedBiConsumer<? extends R, ? extends Throwable> listener) {
    listeners.retriesExceeded().add(Listeners.of(listener));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution fails and the {@link RetryPolicy#withMaxRetries(int)
   * max retry attempts} or {@link RetryPolicy#withMaxDuration(long, java.util.concurrent.TimeUnit) max duration} are
   * exceeded.
   */
  public F onRetriesExceeded(CheckedConsumer<? extends Throwable> listener) {
    listeners.retriesExceeded().add(Listeners.of(listener));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution fails and the
   * {@link RetryPolicy#withMaxRetries(int) max retry attempts} or
   * {@link RetryPolicy#withMaxDuration(long, java.util.concurrent.TimeUnit) max duration} are exceeded.
   */
  public F onRetriesExceededAsync(CheckedBiConsumer<? extends R, ? extends Throwable> listener,
      ExecutorService executor) {
    listeners.retriesExceeded().add(Listeners.of(Listeners.of(listener), Assert.notNull(executor, "executor"), null));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution fails and the
   * {@link RetryPolicy#withMaxRetries(int) max retry attempts} or
   * {@link RetryPolicy#withMaxDuration(long, java.util.concurrent.TimeUnit) max duration} are exceeded.
   */
  public F onRetriesExceededAsync(CheckedConsumer<? extends Throwable> listener, ExecutorService executor) {
    listeners.retriesExceeded()
        .add(Listeners.of(Listeners.of(listener), Assert.notNull(executor, "executor"), null));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called before an execution is retried.
   */
  public F onRetry(CheckedBiConsumer<? extends R, ? extends Throwable> listener) {
    listeners.retry().add(Listeners.of(listener));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called before an execution is retried.
   */
  public F onRetry(CheckedConsumer<? extends Throwable> listener) {
    listeners.retry().add(Listeners.of(listener));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called before an execution is retried.
   */
  public F onRetry(ContextualResultListener<? extends R, ? extends Throwable> listener) {
    listeners.retry().add((ContextualResultListener<R, Throwable>) Assert.notNull(listener, "listener"));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} before an execution is retried.
   */
  public F onRetryAsync(CheckedBiConsumer<? extends R, ? extends Throwable> listener, ExecutorService executor) {
    listeners.retry().add(Listeners.of(Listeners.of(listener), Assert.notNull(executor, "executor"), null));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} before an execution is retried.
   */
  public F onRetryAsync(CheckedConsumer<? extends Throwable> listener, ExecutorService executor) {
    listeners.retry().add(Listeners.of(Listeners.of(listener), Assert.notNull(executor, "executor"), null));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} before an execution is retried.
   */
  public F onRetryAsync(ContextualResultListener<? extends R, ? extends Throwable> listener, ExecutorService executor) {
    listeners.retry().add(Listeners.of(listener, Assert.notNull(executor, "executor"), null));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution is successful. If multiple policies, are configured,
   * this handler is called when the outer-most policy succeeds.
   */
  public F onSuccess(CheckedBiConsumer<? extends R, ExecutionContext> listener) {
    listeners.success().add(Listeners.ofResult(listener));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution is successful. If multiple policies, are configured,
   * this handler is called when the outer-most policy succeeds.
   */
  public F onSuccess(CheckedConsumer<? extends R> listener) {
    listeners.success().add(Listeners.ofResult(listener));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution is successful.
   * If multiple policies, are configured, this handler is called when the outer-most policy succeeds.
   */
  public F onSuccessAsync(CheckedBiConsumer<? extends R, ExecutionContext> listener, ExecutorService executor) {
    listeners.success().add(Listeners.of(Listeners.ofResult(listener), Assert.notNull(executor, "executor"), null));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution is successful.
   * If multiple policies, are configured, this handler is called when the outer-most policy succeeds.
   */
  public F onSuccessAsync(CheckedConsumer<? extends R> listener, ExecutorService executor) {
    listeners.success().add(Listeners.of(Listeners.ofResult(listener), Assert.notNull(executor, "executor"), null));
    return (F) this;
  }

  /**
   * Configures the {@code circuitBreaker} to be used to control the rate of event execution.
   *
   * @throws NullPointerException if {@code circuitBreaker} is null
   * @throws IllegalStateException if a circuit breaker is already configured or if ordered policies have been configured
   */
  public F with(CircuitBreaker circuitBreaker) {
    Assert.state(this.circuitBreaker == null, "A circuit breaker has already been configured");
    Assert.state(policies == null || policies.isEmpty(), "Policies have already been configured");
    this.circuitBreaker = Assert.notNull(circuitBreaker, "circuitBreaker");
    return (F) this;
  }

  /**
   * Configures the {@code retryPolicy} to be used for retrying failed executions.
   *
   * @throws NullPointerException if {@code retryPolicy} is null
   * @throws IllegalStateException if a retry policy is already configured or if ordered policies have been configured
   */
  public F with(RetryPolicy retryPolicy) {
    Assert.state(this.retryPolicy == RetryPolicy.NEVER, "A retry policy has already been configurd");
    Assert.state(policies == null || policies.isEmpty(), "Policies have already been configured");
    this.retryPolicy = Assert.notNull(retryPolicy, "retryPolicy");
    return (F) this;
  }

  /**
   * Configures the {@code fallback} action to be executed if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   * @throws IllegalStateException if {@code withFallback} method has already been called or if ordered policies have
   * been configured
   */
  public F withFallback(Callable<? extends R> fallback) {
    return withFallback(Fallback.of(fallback));
  }

  /**
   * Configures the {@code fallback} action to be executed if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   * @throws IllegalStateException if {@code withFallback} method has already been called or if ordered policies have
   * been configured
   */
  public F withFallback(CheckedBiConsumer<? extends R, ? extends Throwable> fallback) {
    return withFallback(Fallback.of(fallback));
  }

  /**
   * Configures the {@code fallback} action to be executed if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   * @throws IllegalStateException if {@code withFallback} method has already been called or if ordered policies have
   * been configured
   */
  public F withFallback(CheckedBiFunction<? extends R, ? extends Throwable, ? extends R> fallback) {
    withFallback(Fallback.of(fallback));
    return (F) this;
  }

  /**
   * Configures the {@code fallback} action to be executed if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   * @throws IllegalStateException if {@code withFallback} method has already been called or if ordered policies have
   * been configured
   */
  public F withFallback(CheckedConsumer<? extends Throwable> fallback) {
    return withFallback(Fallback.of(fallback));
  }

  /**
   * Configures the {@code fallback} action to be executed if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   * @throws IllegalStateException if {@code withFallback} method has already been called or if ordered policies have
   * been configured
   */
  public F withFallback(CheckedFunction<? extends Throwable, ? extends R> fallback) {
    return withFallback(Fallback.of(fallback));
  }

  /**
   * Configures the {@code fallback} action to be executed if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   * @throws IllegalStateException if {@code withFallback} method has already been called or if ordered policies have
   * been configured
   */
  public F withFallback(CheckedRunnable fallback) {
    return withFallback(Fallback.of(fallback));
  }

  /**
   * Configures the {@code fallback} result to be returned if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   * @throws IllegalStateException if {@code withFallback} method has already been called or if ordered policies have
   * been configured
   */
  public F withFallback(R fallback) {
    return withFallback(Fallback.of(fallback));
  }

  /**
   * Configures the {@code fallback} result to be returned if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   * @throws IllegalStateException if {@code withFallback} method has already been called or if ordered policies have
   * been configured
   */
  public F withFallback(Fallback fallback) {
    Assert.state(this.fallback == null, "withFallback has already been called");
    Assert.state(policies == null || policies.isEmpty(), "Policies have already been configured");
    this.fallback = Assert.notNull(fallback, "fallback");
    return (F) this;
  }
}
