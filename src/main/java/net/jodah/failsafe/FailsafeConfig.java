package net.jodah.failsafe;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

import net.jodah.failsafe.event.ContextualResultListener;
import net.jodah.failsafe.function.CheckedBiConsumer;
import net.jodah.failsafe.function.CheckedBiFunction;
import net.jodah.failsafe.function.CheckedRunnable;
import net.jodah.failsafe.function.CheckedConsumer;
import net.jodah.failsafe.function.CheckedFunction;
import net.jodah.failsafe.internal.util.Assert;

/**
 * Failsafe configuration.
 * 
 * @author Jonathan Halterman
 * @param <R> result type
 * @param <F> failsafe type - {@link SyncFailsafe} or {@link AsyncFailsafe}
 */
@SuppressWarnings("unchecked")
public class FailsafeConfig<R, F> {
  RetryPolicy retryPolicy = RetryPolicy.NEVER;
  CircuitBreaker circuitBreaker;
  CheckedBiFunction<R, Throwable, R> fallback;
  Listeners<R> listeners;
  ListenerRegistry<R> listenerRegistry;

  FailsafeConfig() {
  }

  FailsafeConfig(FailsafeConfig<R, ?> config) {
    retryPolicy = config.retryPolicy;
    circuitBreaker = config.circuitBreaker;
    fallback = config.fallback;
    listeners = config.listeners;
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
          : (abortListeners = new ArrayList<ContextualResultListener<T, Throwable>>(2));
    }

    List<ContextualResultListener<T, Throwable>> complete() {
      return completeListeners != null ? completeListeners
          : (completeListeners = new ArrayList<ContextualResultListener<T, Throwable>>(2));
    }

    List<ContextualResultListener<T, Throwable>> failedAttempt() {
      return failedAttemptListeners != null ? failedAttemptListeners
          : (failedAttemptListeners = new ArrayList<ContextualResultListener<T, Throwable>>(2));
    }

    List<ContextualResultListener<T, Throwable>> failure() {
      return failureListeners != null ? failureListeners
          : (failureListeners = new ArrayList<ContextualResultListener<T, Throwable>>(2));
    }

    List<ContextualResultListener<T, Throwable>> retriesExceeded() {
      return retriesExceededListeners != null ? retriesExceededListeners
          : (retriesExceededListeners = new ArrayList<ContextualResultListener<T, Throwable>>(2));
    }

    List<ContextualResultListener<T, Throwable>> retry() {
      return retryListeners != null ? retryListeners
          : (retryListeners = new ArrayList<ContextualResultListener<T, Throwable>>(2));
    }

    List<ContextualResultListener<T, Throwable>> success() {
      return successListeners != null ? successListeners
          : (successListeners = new ArrayList<ContextualResultListener<T, Throwable>>(2));
    }
  }

  static <T> void call(List<ContextualResultListener<T, Throwable>> listeners, T result, Throwable failure,
      ExecutionContext context) {
    for (ContextualResultListener<T, Throwable> listener : listeners) {
      try {
        listener.onResult(result, failure, context);
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
    registry().abort().add(Listeners.<R>of(listener));
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
    registry().failedAttempt().add(Listeners.<R>of(listener));
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
    registry().failedAttempt().add(Listeners.of(Listeners.<R>of(listener), Assert.notNull(executor, "executor"), null));
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
   * Registers the {@code listener} to be called when an execution fails and cannot be retried.
   */
  public F onFailure(CheckedBiConsumer<? extends R, ? extends Throwable> listener) {
    registry().failure().add(Listeners.of(listener));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution fails and cannot be retried.
   */
  public F onFailure(CheckedConsumer<? extends Throwable> listener) {
    registry().failure().add(Listeners.<R>of(listener));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution fails and cannot be retried.
   */
  public F onFailure(ContextualResultListener<? extends R, ? extends Throwable> listener) {
    registry().failure().add((ContextualResultListener<R, Throwable>) Assert.notNull(listener, "listener"));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution fails and
   * cannot be retried.
   */
  public F onFailureAsync(CheckedBiConsumer<? extends R, ? extends Throwable> listener, ExecutorService executor) {
    registry().failure().add(Listeners.of(Listeners.of(listener), Assert.notNull(executor, "executor"), null));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution fails and
   * cannot be retried.
   */
  public F onFailureAsync(CheckedConsumer<? extends Throwable> listener, ExecutorService executor) {
    registry().failure().add(Listeners.of(Listeners.<R>of(listener), Assert.notNull(executor, "executor"), null));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution fails and
   * cannot be retried.
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
    registry().retriesExceeded().add(Listeners.<R>of(listener));
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
        .add(Listeners.of(Listeners.<R>of(listener), Assert.notNull(executor, "executor"), null));
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
    registry().retry().add(Listeners.<R>of(listener));
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
    registry().retry().add(Listeners.of(Listeners.<R>of(listener), Assert.notNull(executor, "executor"), null));
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
   * Registers the {@code listener} to be called when an execution is successful.
   */
  public F onSuccess(CheckedBiConsumer<? extends R, ExecutionContext> listener) {
    registry().success().add(Listeners.ofResult(listener));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution is successful.
   */
  public F onSuccess(CheckedConsumer<? extends R> listener) {
    registry().success().add(Listeners.ofResult(listener));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution is successful.
   */
  public F onSuccessAsync(CheckedBiConsumer<? extends R, ExecutionContext> listener, ExecutorService executor) {
    registry().success().add(Listeners.of(Listeners.ofResult(listener), Assert.notNull(executor, "executor"), null));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution is successful.
   */
  public F onSuccessAsync(CheckedConsumer<? extends R> listener, ExecutorService executor) {
    registry().success().add(Listeners.of(Listeners.ofResult(listener), Assert.notNull(executor, "executor"), null));
    return (F) this;
  }

  /**
   * Configures the {@code circuitBreaker} to be used to control the rate of event execution.
   * 
   * @throws NullPointerException if {@code circuitBreaker} is null
   * @throws IllegalStateException if a circuit breaker is already configured
   */
  public F with(CircuitBreaker circuitBreaker) {
    Assert.state(this.circuitBreaker == null, "A circuit breaker has already been configured");
    this.circuitBreaker = Assert.notNull(circuitBreaker, "circuitBreaker");
    return (F) this;
  }

  /**
   * Configures the {@code listeners} to be called as execution events occur.
   * 
   * @throws NullPointerException if {@code listeners} is null
   */
  public <T> F with(Listeners<T> listeners) {
    this.listeners = (Listeners<R>) Assert.notNull(listeners, "listeners");
    return (F) this;
  }

  /**
   * Configures the {@code retryPolicy} to be used for retrying failed executions.
   * 
   * @throws NullPointerException if {@code retryPolicy} is null
   * @throws IllegalStateException if a retry policy is already configured
   */
  public F with(RetryPolicy retryPolicy) {
    Assert.state(this.retryPolicy == RetryPolicy.NEVER, "A retry policy has already been configurd");
    this.retryPolicy = Assert.notNull(retryPolicy, "retryPolicy");
    return (F) this;
  }

  /**
   * Configures the {@code fallback} action to be executed if execution fails.
   * 
   * @throws NullPointerException if {@code fallback} is null
   * @throws IllegalStateException if {@code withFallback} method has already been called
   */
  public F withFallback(Callable<? extends R> fallback) {
    return withFallback(Functions.fnOf((Callable<R>) Assert.notNull(fallback, "fallback")));
  }

  /**
   * Configures the {@code fallback} action to be executed if execution fails.
   * 
   * @throws NullPointerException if {@code fallback} is null
   * @throws IllegalStateException if {@code withFallback} method has already been called
   */
  public F withFallback(CheckedBiConsumer<? extends R, ? extends Throwable> fallback) {
    return withFallback(Functions.fnOf((CheckedBiConsumer<R, Throwable>) Assert.notNull(fallback, "fallback")));
  }

  /**
   * Configures the {@code fallback} action to be executed if execution fails.
   * 
   * @throws NullPointerException if {@code fallback} is null
   * @throws IllegalStateException if {@code withFallback} method has already been called
   */
  public F withFallback(CheckedBiFunction<? extends R, ? extends Throwable, ? extends R> fallback) {
    Assert.state(this.fallback == null, "withFallback has already been called");
    this.fallback = (CheckedBiFunction<R, Throwable, R>) Assert.notNull(fallback, "fallback");
    return (F) this;
  }

  /**
   * Configures the {@code fallback} action to be executed if execution fails.
   * 
   * @throws NullPointerException if {@code fallback} is null
   * @throws IllegalStateException if {@code withFallback} method has already been called
   */
  public F withFallback(CheckedConsumer<? extends Throwable> fallback) {
    return withFallback(Functions.fnOf((CheckedConsumer<Throwable>) Assert.notNull(fallback, "fallback")));
  }

  /**
   * Configures the {@code fallback} action to be executed if execution fails.
   * 
   * @throws NullPointerException if {@code fallback} is null
   * @throws IllegalStateException if {@code withFallback} method has already been called
   */
  public F withFallback(CheckedFunction<? extends Throwable, ? extends R> fallback) {
    return withFallback(Functions.fnOf((CheckedFunction<Throwable, R>) Assert.notNull(fallback, "fallback")));
  }

  /**
   * Configures the {@code fallback} action to be executed if execution fails.
   * 
   * @throws NullPointerException if {@code fallback} is null
   * @throws IllegalStateException if {@code withFallback} method has already been called
   */
  public F withFallback(CheckedRunnable fallback) {
    return withFallback(Functions.fnOf(Assert.notNull(fallback, "fallback")));
  }

  /**
   * Configures the {@code fallback} result to be returned if execution fails.
   * 
   * @throws NullPointerException if {@code fallback} is null
   * @throws IllegalStateException if {@code withFallback} method has already been called
   */
  public F withFallback(R fallback) {
    return withFallback(Functions.fnOf(Assert.notNull(fallback, "fallback")));
  }

  void handleAbort(R result, Throwable failure, ExecutionContext context) {
    if (listenerRegistry != null && listenerRegistry.abortListeners != null) {
      context = context.copy();
      call(listenerRegistry.abortListeners, result, failure, context);
    }

    if (listeners != null) {
      try {
        listeners.onAbort(result, failure);
      } catch (Exception ignore) {
      }
      try {
        listeners.onAbort(result, failure, context);
      } catch (Exception ignore) {
      }
    }
  }

  void handleComplete(R result, Throwable failure, ExecutionContext context, boolean success) {
    if (success)
      handleSuccess(result, context);
    else
      handleFailure(result, failure, context);
    handleComplete(result, failure, context);
  }

  void handleFailedAttempt(R result, Throwable failure, ExecutionContext context) {
    if (listenerRegistry != null && listenerRegistry.failedAttemptListeners != null) {
      context = context.copy();
      call(listenerRegistry.failedAttemptListeners, result, failure, context);
    }

    if (listeners != null) {
      try {
        listeners.onFailedAttempt(result, failure);
      } catch (Exception ignore) {
      }
      try {
        listeners.onFailedAttempt(result, failure, context);
      } catch (Exception ignore) {
      }
    }
  }

  void handleRetriesExceeded(R result, Throwable failure, ExecutionContext context) {
    if (listenerRegistry != null && listenerRegistry.retriesExceededListeners != null) {
      context = context.copy();
      call(listenerRegistry.retriesExceededListeners, result, failure, context);
    }

    if (listeners != null) {
      try {
        listeners.onRetriesExceeded(result, failure);
      } catch (Exception ignore) {
      }
    }
  }

  void handleRetry(R result, Throwable failure, ExecutionContext context) {
    if (listenerRegistry != null && listenerRegistry.retryListeners != null) {
      context = context.copy();
      call(listenerRegistry.retryListeners, result, failure, context);
    }

    if (listeners != null) {
      try {
        listeners.onRetry(result, failure);
      } catch (Exception ignore) {
      }
      try {
        listeners.onRetry(result, failure, context);
      } catch (Exception ignore) {
      }
    }
  }

  ListenerRegistry<R> registry() {
    return listenerRegistry != null ? listenerRegistry : (listenerRegistry = new ListenerRegistry<R>());
  }

  private void handleComplete(R result, Throwable failure, ExecutionContext context) {
    if (listenerRegistry != null && listenerRegistry.completeListeners != null) {
      context = context.copy();
      call(listenerRegistry.completeListeners, result, failure, context);
    }

    if (listeners != null) {
      try {
        listeners.onComplete(result, failure);
      } catch (Exception ignore) {
      }
      try {
        listeners.onComplete(result, failure, context);
      } catch (Exception ignore) {
      }
    }
  }

  private void handleFailure(R result, Throwable failure, ExecutionContext context) {
    if (listenerRegistry != null && listenerRegistry.failureListeners != null) {
      context = context.copy();
      call(listenerRegistry.failureListeners, result, failure, context);
    }

    if (listeners != null) {
      try {
        listeners.onFailure(result, failure);
      } catch (Exception ignore) {
      }
      try {
        listeners.onFailure(result, failure, context);
      } catch (Exception ignore) {
      }
    }
  }

  private void handleSuccess(R result, ExecutionContext context) {
    if (listenerRegistry != null && listenerRegistry.successListeners != null) {
      context = context.copy();
      call(listenerRegistry.successListeners, result, null, context);
    }

    if (listeners != null) {
      try {
        listeners.onSuccess(result);
      } catch (Exception ignore) {
      }
      try {
        listeners.onSuccess(result, context);
      } catch (Exception ignore) {
      }
    }
  }
}
