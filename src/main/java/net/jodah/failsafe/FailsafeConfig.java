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
import net.jodah.failsafe.internal.util.CommonPoolScheduler;
import net.jodah.failsafe.util.concurrent.Scheduler;
import net.jodah.failsafe.util.concurrent.Schedulers;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

/**
 * Failsafe configuration.
 *
 * @param <S> self type
 * @param <R> result type
 * @author Jonathan Halterman
 */
@SuppressWarnings({ "WeakerAccess", "UnusedReturnValue", "unchecked" })
public class FailsafeConfig<S, R> {
  Scheduler scheduler = CommonPoolScheduler.INSTANCE;
  Supplier<Scheduler> schedulerSupplier = () -> scheduler;
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

  /**
   * Registers the {@code listener} to be called when an execution is aborted.
   */
  public S onAbort(CheckedBiConsumer<? extends R, ? extends Throwable> listener) {
    listeners.abort().add(Listeners.of(listener));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution is aborted.
   */
  public S onAbort(CheckedConsumer<? extends Throwable> listener) {
    listeners.abort().add(Listeners.of(listener));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution is aborted.
   */
  public S onAbort(ContextualResultListener<? extends R, ? extends Throwable> listener) {
    listeners.abort().add((ContextualResultListener<R, Throwable>) Assert.notNull(listener, "listener"));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured Scheduler when an execution is
   * aborted according to a retry policy.
   *
   * @throws IllegalStateException if a {@link RetryPolicy} is not configured
   */
  public S onAbortAsync(ContextualResultListener<? extends R, ? extends Throwable> listener) {
    listeners.abort().add(Listeners.of(listener, schedulerSupplier));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured Scheduler when an execution is
   * aborted according to a retry policy.
   *
   * @throws IllegalStateException if a {@link RetryPolicy} is not configured
   */
  public S onAbortAsync(CheckedConsumer<? extends Throwable> listener) {
    listeners.abort().add(Listeners.of(Listeners.of(listener), schedulerSupplier));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured Scheduler when an execution is
   * aborted according to a retry policy.
   *
   * @throws IllegalStateException if a {@link RetryPolicy} is not configured
   */
  public S onAbortAsync(CheckedBiConsumer<? extends R, ? extends Throwable> listener) {
    listeners.abort().add(Listeners.of(Listeners.of(listener), schedulerSupplier));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution is completed.
   */
  public S onComplete(CheckedBiConsumer<? extends R, ? extends Throwable> listener) {
    listeners.complete().add(Listeners.of(listener));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution is completed.
   */
  public S onComplete(ContextualResultListener<? extends R, ? extends Throwable> listener) {
    listeners.complete().add((ContextualResultListener<R, Throwable>) Assert.notNull(listener, "listener"));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler when an
   * execution is completed.
   */
  public S onCompleteAsync(ContextualResultListener<? extends R, ? extends Throwable> listener) {
    listeners.complete().add(Listeners.of(listener, schedulerSupplier));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler when an
   * execution is completed.
   */
  public S onCompleteAsync(CheckedBiConsumer<? extends R, ? extends Throwable> listener) {
    listeners.complete().add(Listeners.of(Listeners.of(listener), schedulerSupplier));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution attempt fails.
   */
  public S onFailedAttempt(CheckedBiConsumer<? extends R, ? extends Throwable> listener) {
    listeners.failedAttempt().add(Listeners.of(listener));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution attempt fails.
   */
  public S onFailedAttempt(CheckedConsumer<? extends Throwable> listener) {
    listeners.failedAttempt().add(Listeners.of(listener));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution attempt fails.
   */
  public S onFailedAttempt(ContextualResultListener<? extends R, ? extends Throwable> listener) {
    listeners.failedAttempt().add((ContextualResultListener<R, Throwable>) Assert.notNull(listener, "listener"));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler after a
   * failed execution attempt.
   */
  public S onFailedAttemptAsync(ContextualResultListener<? extends R, ? extends Throwable> listener) {
    listeners.failedAttempt().add(Listeners.of(listener, schedulerSupplier));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler after a
   * failed execution attempt.
   */
  public S onFailedAttemptAsync(CheckedConsumer<? extends Throwable> listener) {
    listeners.failedAttempt().add(Listeners.of(Listeners.of(listener), schedulerSupplier));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler after a
   * failed execution attempt.
   */
  public S onFailedAttemptAsync(CheckedBiConsumer<? extends R, ? extends Throwable> listener) {
    listeners.failedAttempt().add(Listeners.of(Listeners.of(listener), schedulerSupplier));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution fails and cannot be retried. If multiple policies,
   * are configured, this handler is called when the outer-most policy fails.
   */
  public S onFailure(CheckedBiConsumer<? extends R, ? extends Throwable> listener) {
    listeners.failure().add(Listeners.of(listener));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution fails and cannot be retried. If multiple policies,
   * are configured, this handler is called when the outer-most policy fails.
   */
  public S onFailure(CheckedConsumer<? extends Throwable> listener) {
    listeners.failure().add(Listeners.of(listener));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution fails and cannot be retried. If multiple policies,
   * are configured, this handler is called when the outer-most policy fails.
   */
  public S onFailure(ContextualResultListener<? extends R, ? extends Throwable> listener) {
    listeners.failure().add((ContextualResultListener<R, Throwable>) Assert.notNull(listener, "listener"));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured Scheduler when an execution
   * fails and cannot be retried. If multiple policies, are configured, this handler is called when the outer-most
   * policy fails.
   */
  public S onFailureAsync(ContextualResultListener<? extends R, ? extends Throwable> listener) {
    listeners.failure().add(Listeners.of(listener, schedulerSupplier));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured Scheduler when an execution
   * fails and cannot be retried. If multiple policies, are configured, this handler is called when the outer-most
   * policy fails.
   */
  public S onFailureAsync(CheckedConsumer<? extends Throwable> listener) {
    listeners.failure().add(Listeners.of(Listeners.of(listener), schedulerSupplier));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured Scheduler when an execution
   * fails and cannot be retried. If multiple policies, are configured, this handler is called when the outer-most
   * policy fails.
   */
  public S onFailureAsync(CheckedBiConsumer<? extends R, ? extends Throwable> listener) {
    listeners.failure().add(Listeners.of(Listeners.of(listener), schedulerSupplier));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution fails and the {@link RetryPolicy#withMaxRetries(int)
   * max retry attempts} or {@link RetryPolicy#withMaxDuration(long, java.util.concurrent.TimeUnit) max duration} are
   * exceeded.
   */
  public S onRetriesExceeded(CheckedBiConsumer<? extends R, ? extends Throwable> listener) {
    listeners.retriesExceeded().add(Listeners.of(listener));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution fails and the {@link RetryPolicy#withMaxRetries(int)
   * max retry attempts} or {@link RetryPolicy#withMaxDuration(long, java.util.concurrent.TimeUnit) max duration} are
   * exceeded.
   */
  public S onRetriesExceeded(CheckedConsumer<? extends Throwable> listener) {
    listeners.retriesExceeded().add(Listeners.of(listener));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously when an execution fails and the {@link
   * RetryPolicy#withMaxRetries(int) max retry attempts} or {@link RetryPolicy#withMaxDuration(long,
   * java.util.concurrent.TimeUnit) max duration} are exceeded.
   */
  public S onRetriesExceededAsync(CheckedConsumer<? extends Throwable> listener) {
    listeners.retriesExceeded().add(Listeners.of(Listeners.of(listener), schedulerSupplier));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously when an execution fails and the {@link
   * RetryPolicy#withMaxRetries(int) max retry attempts} or {@link RetryPolicy#withMaxDuration(long,
   * java.util.concurrent.TimeUnit) max duration} are exceeded.
   */
  public S onRetriesExceededAsync(CheckedBiConsumer<? extends R, ? extends Throwable> listener) {
    listeners.retriesExceeded().add(Listeners.of(Listeners.of(listener), schedulerSupplier));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called before an execution is retried.
   */
  public S onRetry(CheckedBiConsumer<? extends R, ? extends Throwable> listener) {
    listeners.retry().add(Listeners.of(listener));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called before an execution is retried.
   */
  public S onRetry(CheckedConsumer<? extends Throwable> listener) {
    listeners.retry().add(Listeners.of(listener));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called before an execution is retried.
   */
  public S onRetry(ContextualResultListener<? extends R, ? extends Throwable> listener) {
    listeners.retry().add((ContextualResultListener<R, Throwable>) Assert.notNull(listener, "listener"));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler before an
   * execution is retried.
   *
   * @throws IllegalStateException if a {@link RetryPolicy} is not configured
   */
  public S onRetryAsync(ContextualResultListener<? extends R, ? extends Throwable> listener) {
    listeners.retry().add(Listeners.of(listener, schedulerSupplier));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler before an
   * execution is retried.
   *
   * @throws IllegalStateException if a {@link RetryPolicy} is not configured
   */
  public S onRetryAsync(CheckedConsumer<? extends Throwable> listener) {
    listeners.retry().add(Listeners.of(Listeners.of(listener), schedulerSupplier));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler before an
   * execution is retried.
   *
   * @throws IllegalStateException if a {@link RetryPolicy} is not configured
   */
  public S onRetryAsync(CheckedBiConsumer<? extends R, ? extends Throwable> listener) {
    listeners.retry().add(Listeners.of(Listeners.of(listener), schedulerSupplier));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution is successful. If multiple policies, are configured,
   * this handler is called when the outer-most policy succeeds.
   */
  public S onSuccess(CheckedBiConsumer<? extends R, ExecutionContext> listener) {
    listeners.success().add(Listeners.ofResult(listener));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution is successful. If multiple policies, are configured,
   * this handler is called when the outer-most policy succeeds.
   */
  public S onSuccess(CheckedConsumer<? extends R> listener) {
    listeners.success().add(Listeners.ofResult(listener));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler when an
   * execution is successful. If multiple policies, are configured, this handler is called when the outer-most policy
   * succeeds.
   */
  public S onSuccessAsync(CheckedBiConsumer<? extends R, ExecutionContext> listener) {
    listeners.success().add(Listeners.of(Listeners.ofResult(listener), schedulerSupplier));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler when an
   * execution is successful. If multiple policies, are configured, this handler is called when the outer-most policy
   * succeeds.
   */
  public S onSuccessAsync(CheckedConsumer<? extends R> listener) {
    listeners.success().add(Listeners.of(Listeners.ofResult(listener), schedulerSupplier));
    return (S) this;
  }

  /**
   * Configures the {@code executor} to use for performing asynchronous executions and listener callbacks.
   *
   * @throws NullPointerException if {@code executor} is null
   */
  public S with(ScheduledExecutorService executor) {
    this.scheduler = Schedulers.of(executor);
    return (S) this;
  }

  /**
   * Configures the {@code scheduler} to use for performing asynchronous executions and listener callbacks.
   *
   * @throws NullPointerException if {@code scheduler} is null
   */
  public S with(Scheduler scheduler) {
    this.scheduler = Assert.notNull(scheduler, "scheduler");
    return (S) this;
  }

  /**
   * Configures the {@code circuitBreaker} to be used to control the rate of event execution.
   *
   * @throws NullPointerException if {@code circuitBreaker} is null
   * @throws IllegalStateException if a circuit breaker is already configured or if ordered policies have been
   * configured
   */
  public S with(CircuitBreaker circuitBreaker) {
    Assert.state(this.circuitBreaker == null, "A circuit breaker has already been configured");
    Assert.state(policies == null || policies.isEmpty(), "Policies have already been configured");
    this.circuitBreaker = Assert.notNull(circuitBreaker, "circuitBreaker");
    return (S) this;
  }

  /**
   * Configures the {@code retryPolicy} to be used for retrying failed executions.
   *
   * @throws NullPointerException if {@code retryPolicy} is null
   * @throws IllegalStateException if a retry policy is already configured or if ordered policies have been configured
   */
  public S with(RetryPolicy retryPolicy) {
    Assert.state(this.retryPolicy == RetryPolicy.NEVER, "A retry policy has already been configurd");
    Assert.state(policies == null || policies.isEmpty(), "Policies have already been configured");
    this.retryPolicy = Assert.notNull(retryPolicy, "retryPolicy");
    return (S) this;
  }

  /**
   * Configures the {@code fallback} action to be executed if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   * @throws IllegalStateException if {@code withFallback} method has already been called or if ordered policies have
   * been configured
   */
  public S withFallback(Callable<? extends R> fallback) {
    return withFallback(Fallback.of(fallback));
  }

  /**
   * Configures the {@code fallback} action to be executed if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   * @throws IllegalStateException if {@code withFallback} method has already been called or if ordered policies have
   * been configured
   */
  public S withFallback(CheckedBiConsumer<? extends R, ? extends Throwable> fallback) {
    return withFallback(Fallback.of(fallback));
  }

  /**
   * Configures the {@code fallback} action to be executed if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   * @throws IllegalStateException if {@code withFallback} method has already been called or if ordered policies have
   * been configured
   */
  public S withFallback(CheckedBiFunction<? extends R, ? extends Throwable, ? extends R> fallback) {
    withFallback(Fallback.of(fallback));
    return (S) this;
  }

  /**
   * Configures the {@code fallback} action to be executed if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   * @throws IllegalStateException if {@code withFallback} method has already been called or if ordered policies have
   * been configured
   */
  public S withFallback(CheckedConsumer<? extends Throwable> fallback) {
    return withFallback(Fallback.of(fallback));
  }

  /**
   * Configures the {@code fallback} action to be executed if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   * @throws IllegalStateException if {@code withFallback} method has already been called or if ordered policies have
   * been configured
   */
  public S withFallback(CheckedFunction<? extends Throwable, ? extends R> fallback) {
    return withFallback(Fallback.of(fallback));
  }

  /**
   * Configures the {@code fallback} action to be executed if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   * @throws IllegalStateException if {@code withFallback} method has already been called or if ordered policies have
   * been configured
   */
  public S withFallback(CheckedRunnable fallback) {
    return withFallback(Fallback.of(fallback));
  }

  /**
   * Configures the {@code fallback} result to be returned if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   * @throws IllegalStateException if {@code withFallback} method has already been called or if ordered policies have
   * been configured
   */
  public S withFallback(R fallback) {
    return withFallback(Fallback.of(fallback));
  }

  /**
   * Configures the {@code fallback} result to be returned if execution fails.
   *
   * @throws NullPointerException if {@code fallback} is null
   * @throws IllegalStateException if {@code withFallback} method has already been called or if ordered policies have
   * been configured
   */
  public S withFallback(Fallback fallback) {
    Assert.state(this.fallback == null, "withFallback has already been called");
    Assert.state(policies == null || policies.isEmpty(), "Policies have already been configured");
    this.fallback = Assert.notNull(fallback, "fallback");
    return (S) this;
  }
}
