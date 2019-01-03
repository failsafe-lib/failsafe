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
import net.jodah.failsafe.event.FailsafeEvent;
import net.jodah.failsafe.function.*;
import net.jodah.failsafe.internal.util.Assert;
import net.jodah.failsafe.internal.util.CommonPoolScheduler;
import net.jodah.failsafe.util.concurrent.Scheduler;
import net.jodah.failsafe.util.concurrent.Schedulers;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;

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
  public S onAbort(CheckedConsumer<FailsafeEvent<R>> listener) {
    listeners.abortListener = Assert.notNull(listener, "listener");
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution is completed.
   */
  public S onComplete(CheckedConsumer<FailsafeEvent<R>> listener) {
    listeners.completeListener = Assert.notNull(listener, "listener");
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution attempt fails.
   */
  public S onFailedAttempt(CheckedConsumer<FailsafeEvent<R>> listener) {
    listeners.failedAttemptListener = Assert.notNull(listener, "listener");
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution fails and cannot be retried. If multiple policies,
   * are configured, this handler is called when the outer-most policy fails.
   */
  public S onFailure(CheckedConsumer<FailsafeEvent<R>> listener) {
    listeners.failureListener = Assert.notNull(listener, "listener");
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution fails and the {@link RetryPolicy#withMaxRetries(int)
   * max retry attempts} or {@link RetryPolicy#withMaxDuration(long, java.util.concurrent.TimeUnit) max duration} are
   * exceeded.
   */
  public S onRetriesExceeded(CheckedConsumer<FailsafeEvent<R>> listener) {
    listeners.retriesExceededListener = Assert.notNull(listener, "listener");
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called before an execution is retried.
   */
  public S onRetry(CheckedConsumer<FailsafeEvent<R>> listener) {
    listeners.retryListener = Assert.notNull(listener, "listener");
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution is successful. If multiple policies, are configured,
   * this handler is called when the outer-most policy succeeds.
   */
  public S onSuccess(CheckedConsumer<FailsafeEvent<R>> listener) {
    listeners.successListener = Assert.notNull(listener, "listener");
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
