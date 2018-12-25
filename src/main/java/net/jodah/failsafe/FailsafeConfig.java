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

import net.jodah.failsafe.event.ContextualResultListener;
import net.jodah.failsafe.event.EventHandler;
import net.jodah.failsafe.function.*;
import net.jodah.failsafe.internal.util.Assert;

import java.util.ArrayList;
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
  List<FailsafePolicy> policies;
  ListenerRegistry<R> listenerRegistry;

  FailsafeConfig() {
  }

  FailsafeConfig(List<FailsafePolicy> policies) {
    Assert.isTrue(policies.size() > 0, "At least one policy must be supplied");
    this.policies = policies;
  }

  FailsafeConfig(FailsafeConfig<R, ?> config) {
    retryPolicy = config.retryPolicy;
    circuitBreaker = config.circuitBreaker;
    fallback = config.fallback;
    policies = config.policies;
    listenerRegistry = config.listenerRegistry;
  }

  static class ListenerRegistry<T> {
    private List<ContextualResultListener<T, Throwable>> abortListeners;
    private List<ContextualResultListener<T, Throwable>> completeListeners;
    private List<ContextualResultListener<T, Throwable>> failedAttemptListeners;
    private List<ContextualResultListener<T, Throwable>> failureListeners;
    private List<ContextualResultListener<T, Throwable>> retriesExceededListeners;
    private List<ContextualResultListener<T, Throwable>> retryListeners;
    private List<ContextualResultListener<T, Throwable>> successListeners;

    List<ContextualResultListener<T, Throwable>> abort() {
      return abortListeners != null ? abortListeners
          : (abortListeners = new ArrayList<>(2));
    }

    List<ContextualResultListener<T, Throwable>> complete() {
      return completeListeners != null ? completeListeners
          : (completeListeners = new ArrayList<>(2));
    }

    List<ContextualResultListener<T, Throwable>> failedAttempt() {
      return failedAttemptListeners != null ? failedAttemptListeners
          : (failedAttemptListeners = new ArrayList<>(2));
    }

    List<ContextualResultListener<T, Throwable>> failure() {
      return failureListeners != null ? failureListeners
          : (failureListeners = new ArrayList<>(2));
    }

    List<ContextualResultListener<T, Throwable>> retriesExceeded() {
      return retriesExceededListeners != null ? retriesExceededListeners
          : (retriesExceededListeners = new ArrayList<>(2));
    }

    List<ContextualResultListener<T, Throwable>> retry() {
      return retryListeners != null ? retryListeners
          : (retryListeners = new ArrayList<>(2));
    }

    List<ContextualResultListener<T, Throwable>> success() {
      return successListeners != null ? successListeners
          : (successListeners = new ArrayList<>(2));
    }
  }

  static <T> void call(List<ContextualResultListener<T, Throwable>> listeners, ExecutionResult result,
      ExecutionContext context) {
    for (ContextualResultListener<T, Throwable> listener : listeners) {
      try {
        listener.onResult((T) result.result, result.failure, context);
      } catch (Exception ignore) {
      }
    }
  }

  /**
   * Registers the {@code listener} to be called when an execution is aborted.
   */
  public F onAbort(CheckedBiConsumer<? extends R, ? extends Throwable> listener) {
    registry().abort().add(Listeners.of(listener));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution is aborted.
   */
  public F onAbort(CheckedConsumer<? extends Throwable> listener) {
    registry().abort().add(Listeners.of(listener));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution is aborted.
   */
  public F onAbort(ContextualResultListener<? extends R, ? extends Throwable> listener) {
    registry().abort().add((ContextualResultListener<R, Throwable>) Assert.notNull(listener, "listener"));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution is aborted.
   */
  public F onAbortAsync(CheckedBiConsumer<? extends R, ? extends Throwable> listener, ExecutorService executor) {
    registry().abort().add(Listeners.of(Listeners.of(listener), Assert.notNull(executor, "executor"), null));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution is aborted.
   */
  public F onAbortAsync(CheckedConsumer<? extends Throwable> listener, ExecutorService executor) {
    registry().abort().add(Listeners.of(Listeners.of(listener), Assert.notNull(executor, "executor"), null));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution is aborted.
   */
  public F onAbortAsync(ContextualResultListener<? extends R, ? extends Throwable> listener, ExecutorService executor) {
    registry().abort().add(Listeners.of(listener, Assert.notNull(executor, "executor"), null));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution is completed.
   */
  public F onComplete(CheckedBiConsumer<? extends R, ? extends Throwable> listener) {
    registry().complete().add(Listeners.of(listener));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution is completed.
   */
  public F onComplete(ContextualResultListener<? extends R, ? extends Throwable> listener) {
    registry().complete().add((ContextualResultListener<R, Throwable>) Assert.notNull(listener, "listener"));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution is completed.
   */
  public F onCompleteAsync(CheckedBiConsumer<? extends R, ? extends Throwable> listener, ExecutorService executor) {
    registry().complete().add(Listeners.of(Listeners.of(listener), Assert.notNull(executor, "executor"), null));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution is completed.
   */
  public F onCompleteAsync(ContextualResultListener<? extends R, ? extends Throwable> listener,
      ExecutorService executor) {
    registry().complete().add(Listeners.of(listener, Assert.notNull(executor, "executor"), null));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution attempt fails.
   */
  public F onFailedAttempt(CheckedBiConsumer<? extends R, ? extends Throwable> listener) {
    registry().failedAttempt().add(Listeners.of(listener));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution attempt fails.
   */
  public F onFailedAttempt(CheckedConsumer<? extends Throwable> listener) {
    registry().failedAttempt().add(Listeners.of(listener));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution attempt fails.
   */
  public F onFailedAttempt(ContextualResultListener<? extends R, ? extends Throwable> listener) {
    registry().failedAttempt().add((ContextualResultListener<R, Throwable>) Assert.notNull(listener, "listener"));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution attempt fails.
   */
  public F onFailedAttemptAsync(CheckedBiConsumer<? extends R, ? extends Throwable> listener,
      ExecutorService executor) {
    registry().failedAttempt().add(Listeners.of(Listeners.of(listener), Assert.notNull(executor, "executor"), null));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution attempt fails.
   */
  public F onFailedAttemptAsync(CheckedConsumer<? extends Throwable> listener, ExecutorService executor) {
    registry().failedAttempt().add(Listeners.of(Listeners.of(listener), Assert.notNull(executor, "executor"), null));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution attempt fails.
   */
  public F onFailedAttemptAsync(ContextualResultListener<? extends R, ? extends Throwable> listener,
      ExecutorService executor) {
    registry().failedAttempt().add(Listeners.of(listener, Assert.notNull(executor, "executor"), null));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution fails and cannot be retried. If multiple policies,
   * are configured, this handler is called when the outer-most policy fails.
   */
  public F onFailure(CheckedBiConsumer<? extends R, ? extends Throwable> listener) {
    registry().failure().add(Listeners.of(listener));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution fails and cannot be retried. If multiple policies,
   * are configured, this handler is called when the outer-most policy fails.
   */
  public F onFailure(CheckedConsumer<? extends Throwable> listener) {
    registry().failure().add(Listeners.of(listener));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution fails and cannot be retried. If multiple policies,
   * are configured, this handler is called when the outer-most policy fails.
   */
  public F onFailure(ContextualResultListener<? extends R, ? extends Throwable> listener) {
    registry().failure().add((ContextualResultListener<R, Throwable>) Assert.notNull(listener, "listener"));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution fails and
   * cannot be retried. If multiple policies, are configured, this handler is called when the outer-most policy fails.
   */
  public F onFailureAsync(CheckedBiConsumer<? extends R, ? extends Throwable> listener, ExecutorService executor) {
    registry().failure().add(Listeners.of(Listeners.of(listener), Assert.notNull(executor, "executor"), null));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution fails and
   * cannot be retried. If multiple policies, are configured, this handler is called when the outer-most policy fails.
   */
  public F onFailureAsync(CheckedConsumer<? extends Throwable> listener, ExecutorService executor) {
    registry().failure().add(Listeners.of(Listeners.of(listener), Assert.notNull(executor, "executor"), null));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution fails and
   * cannot be retried. If multiple policies, are configured, this handler is called when the outer-most policy fails.
   */
  public F onFailureAsync(ContextualResultListener<? extends R, ? extends Throwable> listener,
      ExecutorService executor) {
    registry().failure().add(Listeners.of(listener, Assert.notNull(executor, "executor"), null));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution fails and the {@link RetryPolicy#withMaxRetries(int)
   * max retry attempts} or {@link RetryPolicy#withMaxDuration(long, java.util.concurrent.TimeUnit) max duration} are
   * exceeded.
   */
  public F onRetriesExceeded(CheckedBiConsumer<? extends R, ? extends Throwable> listener) {
    registry().retriesExceeded().add(Listeners.of(listener));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution fails and the {@link RetryPolicy#withMaxRetries(int)
   * max retry attempts} or {@link RetryPolicy#withMaxDuration(long, java.util.concurrent.TimeUnit) max duration} are
   * exceeded.
   */
  public F onRetriesExceeded(CheckedConsumer<? extends Throwable> listener) {
    registry().retriesExceeded().add(Listeners.of(listener));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution fails and the
   * {@link RetryPolicy#withMaxRetries(int) max retry attempts} or
   * {@link RetryPolicy#withMaxDuration(long, java.util.concurrent.TimeUnit) max duration} are exceeded.
   */
  public F onRetriesExceededAsync(CheckedBiConsumer<? extends R, ? extends Throwable> listener,
      ExecutorService executor) {
    registry().retriesExceeded().add(Listeners.of(Listeners.of(listener), Assert.notNull(executor, "executor"), null));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution fails and the
   * {@link RetryPolicy#withMaxRetries(int) max retry attempts} or
   * {@link RetryPolicy#withMaxDuration(long, java.util.concurrent.TimeUnit) max duration} are exceeded.
   */
  public F onRetriesExceededAsync(CheckedConsumer<? extends Throwable> listener, ExecutorService executor) {
    registry().retriesExceeded()
        .add(Listeners.of(Listeners.of(listener), Assert.notNull(executor, "executor"), null));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called before an execution is retried.
   */
  public F onRetry(CheckedBiConsumer<? extends R, ? extends Throwable> listener) {
    registry().retry().add(Listeners.of(listener));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called before an execution is retried.
   */
  public F onRetry(CheckedConsumer<? extends Throwable> listener) {
    registry().retry().add(Listeners.of(listener));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called before an execution is retried.
   */
  public F onRetry(ContextualResultListener<? extends R, ? extends Throwable> listener) {
    registry().retry().add((ContextualResultListener<R, Throwable>) Assert.notNull(listener, "listener"));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} before an execution is retried.
   */
  public F onRetryAsync(CheckedBiConsumer<? extends R, ? extends Throwable> listener, ExecutorService executor) {
    registry().retry().add(Listeners.of(Listeners.of(listener), Assert.notNull(executor, "executor"), null));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} before an execution is retried.
   */
  public F onRetryAsync(CheckedConsumer<? extends Throwable> listener, ExecutorService executor) {
    registry().retry().add(Listeners.of(Listeners.of(listener), Assert.notNull(executor, "executor"), null));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} before an execution is retried.
   */
  public F onRetryAsync(ContextualResultListener<? extends R, ? extends Throwable> listener, ExecutorService executor) {
    registry().retry().add(Listeners.of(listener, Assert.notNull(executor, "executor"), null));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution is successful. If multiple policies, are configured,
   * this handler is called when the outer-most policy succeeds.
   */
  public F onSuccess(CheckedBiConsumer<? extends R, ExecutionContext> listener) {
    registry().success().add(Listeners.ofResult(listener));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution is successful. If multiple policies, are configured,
   * this handler is called when the outer-most policy succeeds.
   */
  public F onSuccess(CheckedConsumer<? extends R> listener) {
    registry().success().add(Listeners.ofResult(listener));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution is successful.
   * If multiple policies, are configured, this handler is called when the outer-most policy succeeds.
   */
  public F onSuccessAsync(CheckedBiConsumer<? extends R, ExecutionContext> listener, ExecutorService executor) {
    registry().success().add(Listeners.of(Listeners.ofResult(listener), Assert.notNull(executor, "executor"), null));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution is successful.
   * If multiple policies, are configured, this handler is called when the outer-most policy succeeds.
   */
  public F onSuccessAsync(CheckedConsumer<? extends R> listener, ExecutorService executor) {
    registry().success().add(Listeners.of(Listeners.ofResult(listener), Assert.notNull(executor, "executor"), null));
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

  ListenerRegistry<R> registry() {
    return listenerRegistry != null ? listenerRegistry : (listenerRegistry = new ListenerRegistry<>());
  }

  EventHandler eventHandler = new EventHandler() {
    @Override
    public void handleAbort(ExecutionResult result, ExecutionContext context) {
      if (listenerRegistry != null && listenerRegistry.abortListeners != null)
        call(listenerRegistry.abortListeners, result, context.copy());
    }

    @Override
    public void handleComplete(ExecutionResult result, ExecutionContext context) {
      if (result.success)
        handleSuccess(result, context);
      else
        handleFailure(result, context);

      if (listenerRegistry != null && listenerRegistry.completeListeners != null)
        call(listenerRegistry.completeListeners, result, context.copy());
    }

    @Override
    public void handleFailedAttempt(ExecutionResult result, ExecutionContext context) {
      if (listenerRegistry != null && listenerRegistry.failedAttemptListeners != null)
        call(listenerRegistry.failedAttemptListeners, result, context.copy());
    }

    @Override
    public void handleRetriesExceeded(ExecutionResult result, ExecutionContext context) {
      if (listenerRegistry != null && listenerRegistry.retriesExceededListeners != null)
        call(listenerRegistry.retriesExceededListeners, result, context.copy());
    }

    @Override
    public void handleRetry(ExecutionResult result, ExecutionContext context) {
      if (listenerRegistry != null && listenerRegistry.retryListeners != null)
        call(listenerRegistry.retryListeners, result, context.copy());
    }

    private void handleFailure(ExecutionResult result, ExecutionContext context) {
      if (listenerRegistry != null && listenerRegistry.failureListeners != null)
        call(listenerRegistry.failureListeners, result, context.copy());
    }

    private void handleSuccess(ExecutionResult result, ExecutionContext context) {
      if (listenerRegistry != null && listenerRegistry.successListeners != null)
        call(listenerRegistry.successListeners, result, context.copy());
    }
  };
}
