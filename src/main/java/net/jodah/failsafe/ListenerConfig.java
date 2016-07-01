package net.jodah.failsafe;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import net.jodah.failsafe.event.ContextualResultListener;
import net.jodah.failsafe.event.ContextualSuccessListener;
import net.jodah.failsafe.event.FailureListener;
import net.jodah.failsafe.event.ResultListener;
import net.jodah.failsafe.event.SuccessListener;
import net.jodah.failsafe.internal.util.Assert;

/**
 * Listener configuration.
 * 
 * @author Jonathan Halterman
 * @param <S> source type
 * @param <R> result type
 */
@SuppressWarnings("unchecked")
public class ListenerConfig<S, R> {
  Listeners<R> listeners;
  ListenerRegistry<R> listenerRegistry;

  ListenerConfig() {
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
  public S onAbort(ContextualResultListener<? extends R, ? extends Throwable> listener) {
    registry().abort().add((ContextualResultListener<R, Throwable>) Assert.notNull(listener, "listener"));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution is aborted.
   */
  public S onAbort(FailureListener<? extends Throwable> listener) {
    registry().abort().add(Listeners.of(listener));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution is aborted.
   */
  public S onAbort(ResultListener<? extends R, ? extends Throwable> listener) {
    registry().abort().add(Listeners.of(listener));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution is aborted.
   */
  public S onAbortAsync(ContextualResultListener<? extends R, ? extends Throwable> listener, ExecutorService executor) {
    registry().abort().add(Listeners.of(listener, Assert.notNull(executor, "executor"), null));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution is aborted.
   */
  public S onAbortAsync(FailureListener<? extends Throwable> listener, ExecutorService executor) {
    registry().abort().add(Listeners.of(Listeners.of(listener), Assert.notNull(executor, "executor"), null));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution is aborted.
   */
  public S onAbortAsync(ResultListener<? extends R, ? extends Throwable> listener, ExecutorService executor) {
    registry().abort().add(Listeners.of(Listeners.of(listener), Assert.notNull(executor, "executor"), null));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution is completed.
   */
  public S onComplete(ContextualResultListener<? extends R, ? extends Throwable> listener) {
    registry().complete().add((ContextualResultListener<R, Throwable>) Assert.notNull(listener, "listener"));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution is completed.
   */
  public S onComplete(ResultListener<? extends R, ? extends Throwable> listener) {
    registry().complete().add(Listeners.of(listener));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution is completed.
   */
  public S onCompleteAsync(ContextualResultListener<? extends R, ? extends Throwable> listener,
      ExecutorService executor) {
    registry().complete().add(Listeners.of(listener, Assert.notNull(executor, "executor"), null));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution is completed.
   */
  public S onCompleteAsync(ResultListener<? extends R, ? extends Throwable> listener, ExecutorService executor) {
    registry().complete().add(Listeners.of(Listeners.of(listener), Assert.notNull(executor, "executor"), null));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution attempt fails.
   */
  public S onFailedAttempt(ContextualResultListener<? extends R, ? extends Throwable> listener) {
    registry().failedAttempt().add((ContextualResultListener<R, Throwable>) Assert.notNull(listener, "listener"));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution attempt fails.
   */
  public S onFailedAttempt(FailureListener<? extends Throwable> listener) {
    registry().failedAttempt().add(Listeners.of(listener));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution attempt fails.
   */
  public S onFailedAttempt(ResultListener<? extends R, ? extends Throwable> listener) {
    registry().failedAttempt().add(Listeners.of(listener));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution attempt fails.
   */
  public S onFailedAttemptAsync(ContextualResultListener<? extends R, ? extends Throwable> listener,
      ExecutorService executor) {
    registry().failedAttempt().add(Listeners.of(listener, Assert.notNull(executor, "executor"), null));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution attempt fails.
   */
  public S onFailedAttemptAsync(FailureListener<? extends Throwable> listener, ExecutorService executor) {
    registry().failedAttempt().add(Listeners.of(Listeners.of(listener), Assert.notNull(executor, "executor"), null));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution attempt fails.
   */
  public S onFailedAttemptAsync(ResultListener<? extends R, ? extends Throwable> listener, ExecutorService executor) {
    registry().failedAttempt().add(Listeners.of(Listeners.of(listener), Assert.notNull(executor, "executor"), null));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution fails and cannot be retried.
   */
  public S onFailure(ContextualResultListener<? extends R, ? extends Throwable> listener) {
    registry().failure().add((ContextualResultListener<R, Throwable>) Assert.notNull(listener, "listener"));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution fails and cannot be retried.
   */
  public S onFailure(FailureListener<? extends Throwable> listener) {
    registry().failure().add(Listeners.of(listener));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution fails and cannot be retried.
   */
  public S onFailure(ResultListener<? extends R, ? extends Throwable> listener) {
    registry().failure().add(Listeners.of(listener));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution fails and
   * cannot be retried.
   */
  public S onFailureAsync(ContextualResultListener<? extends R, ? extends Throwable> listener,
      ExecutorService executor) {
    registry().failure().add(Listeners.of(listener, Assert.notNull(executor, "executor"), null));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution fails and
   * cannot be retried.
   */
  public S onFailureAsync(FailureListener<? extends Throwable> listener, ExecutorService executor) {
    registry().failure().add(Listeners.of(Listeners.of(listener), Assert.notNull(executor, "executor"), null));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution fails and
   * cannot be retried.
   */
  public S onFailureAsync(ResultListener<? extends R, ? extends Throwable> listener, ExecutorService executor) {
    registry().failure().add(Listeners.of(Listeners.of(listener), Assert.notNull(executor, "executor"), null));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution fails and retries are exceeded.
   */
  public S onRetriesExceeded(FailureListener<? extends Throwable> listener) {
    registry().retriesExceeded().add(Listeners.of(listener));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution fails and retries are exceeded.
   */
  public S onRetriesExceeded(ResultListener<? extends R, ? extends Throwable> listener) {
    registry().retriesExceeded().add(Listeners.of(listener));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution fails and
   * retries are exceeded.
   */
  public S onRetriesExceededAsync(FailureListener<? extends Throwable> listener, ExecutorService executor) {
    registry().retriesExceeded().add(Listeners.of(Listeners.of(listener), Assert.notNull(executor, "executor"), null));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution fails and
   * retries are exceeded.
   */
  public S onRetriesExceededAsync(ResultListener<? extends R, ? extends Throwable> listener, ExecutorService executor) {
    registry().retriesExceeded().add(Listeners.of(Listeners.of(listener), Assert.notNull(executor, "executor"), null));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called before an execution is retried.
   */
  public S onRetry(ContextualResultListener<? extends R, ? extends Throwable> listener) {
    registry().retry().add((ContextualResultListener<R, Throwable>) Assert.notNull(listener, "listener"));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called before an execution is retried.
   */
  public S onRetry(FailureListener<? extends Throwable> listener) {
    registry().retry().add(Listeners.of(listener));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called before an execution is retried.
   */
  public S onRetry(ResultListener<? extends R, ? extends Throwable> listener) {
    registry().retry().add(Listeners.of(listener));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} before an execution is retried.
   */
  public S onRetryAsync(ContextualResultListener<? extends R, ? extends Throwable> listener, ExecutorService executor) {
    registry().retry().add(Listeners.of(listener, Assert.notNull(executor, "executor"), null));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} before an execution is retried.
   */
  public S onRetryAsync(FailureListener<? extends Throwable> listener, ExecutorService executor) {
    registry().retry().add(Listeners.of(Listeners.of(listener), Assert.notNull(executor, "executor"), null));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} before an execution is retried.
   */
  public S onRetryAsync(ResultListener<? extends R, ? extends Throwable> listener, ExecutorService executor) {
    registry().retry().add(Listeners.of(Listeners.of(listener), Assert.notNull(executor, "executor"), null));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution is successful.
   */
  public S onSuccess(ContextualSuccessListener<? extends R> listener) {
    registry().success().add(Listeners.of(listener));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution is successful.
   */
  public S onSuccess(SuccessListener<? extends R> listener) {
    registry().success().add(Listeners.of(listener));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution is successful.
   */
  public S onSuccessAsync(ContextualSuccessListener<? extends R> listener, ExecutorService executor) {
    registry().success().add(Listeners.of(Listeners.of(listener), Assert.notNull(executor, "executor"), null));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution is successful.
   */
  public S onSuccessAsync(SuccessListener<? extends R> listener, ExecutorService executor) {
    registry().success().add(Listeners.of(Listeners.of(listener), Assert.notNull(executor, "executor"), null));
    return (S) this;
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
