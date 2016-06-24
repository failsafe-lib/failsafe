package net.jodah.failsafe;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import net.jodah.failsafe.event.ContextualResultListener;
import net.jodah.failsafe.event.ContextualSuccessListener;
import net.jodah.failsafe.event.FailureListener;
import net.jodah.failsafe.event.ResultListener;
import net.jodah.failsafe.event.SuccessListener;
import net.jodah.failsafe.internal.util.Assert;
import net.jodah.failsafe.util.concurrent.Scheduler;

/**
 * Listener configuration.
 * 
 * @author Jonathan Halterman
 * @param <S> source type
 * @param <T> result type
 */
@SuppressWarnings("unchecked")
public class ListenerBindings<S, T> {
  Listeners<T> listeners;
  ListenerConfig<T> listenerConfig;

  ListenerBindings() {
  }

  static class ListenerConfig<T> {
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

  static <T> ContextualResultListener<T, Throwable> listenerOf(
      ContextualResultListener<? extends T, ? extends Throwable> listener, ExecutorService executor,
      Scheduler scheduler) {
    return new ContextualResultListener<T, Throwable>() {
      @Override
      public void onResult(T result, Throwable failure, ExecutionContext context) {
        Callable<T> callable = new Callable<T>() {
          public T call() {
            ((ContextualResultListener<T, Throwable>) listener).onResult(result, failure, context);
            return null;
          }
        };

        try {
          if (executor != null)
            executor.submit(callable);
          else
            scheduler.schedule(callable, 0, TimeUnit.MILLISECONDS);
        } catch (Exception ignore) {
        }
      }
    };
  }

  static <T> ContextualResultListener<T, Throwable> listenerOf(ContextualSuccessListener<? extends T> listener) {
    Assert.notNull(listener, "listener");
    return new ContextualResultListener<T, Throwable>() {
      @Override
      public void onResult(T result, Throwable failure, ExecutionContext context) {
        ((ContextualSuccessListener<T>) listener).onSuccess(result, context);
      }
    };
  }

  static <T> ContextualResultListener<T, Throwable> listenerOf(FailureListener<? extends Throwable> listener) {
    Assert.notNull(listener, "listener");
    return new ContextualResultListener<T, Throwable>() {
      @Override
      public void onResult(T result, Throwable failure, ExecutionContext context) {
        ((FailureListener<Throwable>) listener).onFailure(failure);
      }
    };
  }

  static <T> ContextualResultListener<T, Throwable> listenerOf(
      ResultListener<? extends T, ? extends Throwable> listener) {
    Assert.notNull(listener, "listener");
    return new ContextualResultListener<T, Throwable>() {
      @Override
      public void onResult(T result, Throwable failure, ExecutionContext context) {
        ((ResultListener<T, Throwable>) listener).onResult(result, failure);
      }
    };
  }

  static <T> ContextualResultListener<T, Throwable> listenerOf(SuccessListener<? extends T> listener) {
    Assert.notNull(listener, "listener");
    return new ContextualResultListener<T, Throwable>() {
      @Override
      public void onResult(T result, Throwable failure, ExecutionContext context) {
        ((SuccessListener<T>) listener).onSuccess(result);
      }
    };
  }

  private static <T> void call(List<ContextualResultListener<T, Throwable>> listeners, T result, Throwable failure,
      ExecutionContext context) {
    for (ContextualResultListener<T, Throwable> listener : listeners) {
      try {
        listener.onResult(result, failure, context);
      } catch (Exception ignore) {
      }
    }
  }

  /**
   * Registers the {@code listener} to be called after a failed execution attempt.
   */
  public S onAbort(ContextualResultListener<? extends T, ? extends Throwable> listener) {
    config().abort().add((ContextualResultListener<T, Throwable>) Assert.notNull(listener, "listener"));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called when retries are aborted according to the retry policy.
   */
  public S onAbort(FailureListener<? extends Throwable> listener) {
    config().abort().add(listenerOf(listener));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called when retries are aborted according to the retry policy.
   */
  public S onAbort(ResultListener<? extends T, ? extends Throwable> listener) {
    config().abort().add(listenerOf(listener));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} after a failed execution
   * attempt.
   */
  public S onAbortAsync(ContextualResultListener<? extends T, ? extends Throwable> listener, ExecutorService executor) {
    config().abort().add(listenerOf(listener, Assert.notNull(executor, "executor"), null));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when retries are aborted
   * according to the retry policy.
   */
  public S onAbortAsync(FailureListener<? extends Throwable> listener, ExecutorService executor) {
    config().abort().add(listenerOf(listenerOf(listener), Assert.notNull(executor, "executor"), null));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when retries are aborted
   * according to the retry policy.
   */
  public S onAbortAsync(ResultListener<? extends T, ? extends Throwable> listener, ExecutorService executor) {
    config().abort().add(listenerOf(listenerOf(listener), Assert.notNull(executor, "executor"), null));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution is completed.
   */
  public S onComplete(ContextualResultListener<? extends T, ? extends Throwable> listener) {
    config().complete().add((ContextualResultListener<T, Throwable>) Assert.notNull(listener, "listener"));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution is completed.
   */
  public S onComplete(ResultListener<? extends T, ? extends Throwable> listener) {
    config().complete().add(listenerOf(listener));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution is completed.
   */
  public S onCompleteAsync(ContextualResultListener<? extends T, ? extends Throwable> listener,
      ExecutorService executor) {
    config().complete().add(listenerOf(listener, Assert.notNull(executor, "executor"), null));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when an execution is completed.
   */
  public S onCompleteAsync(ResultListener<? extends T, ? extends Throwable> listener, ExecutorService executor) {
    config().complete().add(listenerOf(listenerOf(listener), Assert.notNull(executor, "executor"), null));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called after a failed execution attempt.
   */
  public S onFailedAttempt(ContextualResultListener<? extends T, ? extends Throwable> listener) {
    config().failedAttempt().add((ContextualResultListener<T, Throwable>) Assert.notNull(listener, "listener"));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called after a failed execution attempt.
   */
  public S onFailedAttempt(FailureListener<? extends Throwable> listener) {
    config().failedAttempt().add(listenerOf(listener));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called after a failed execution attempt.
   */
  public S onFailedAttempt(ResultListener<? extends T, ? extends Throwable> listener) {
    config().failedAttempt().add(listenerOf(listener));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} after a failed execution
   * attempt.
   */
  public S onFailedAttemptAsync(ContextualResultListener<? extends T, ? extends Throwable> listener,
      ExecutorService executor) {
    config().failedAttempt().add(listenerOf(listener, Assert.notNull(executor, "executor"), null));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} after a failed execution
   * attempt.
   */
  public S onFailedAttemptAsync(FailureListener<? extends Throwable> listener, ExecutorService executor) {
    config().failedAttempt().add(listenerOf(listenerOf(listener), Assert.notNull(executor, "executor"), null));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} after a failed execution
   * attempt.
   */
  public S onFailedAttemptAsync(ResultListener<? extends T, ? extends Throwable> listener, ExecutorService executor) {
    config().failedAttempt().add(listenerOf(listenerOf(listener), Assert.notNull(executor, "executor"), null));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called after a failure occurs that cannot be retried.
   */
  public S onFailure(ContextualResultListener<? extends T, ? extends Throwable> listener) {
    config().failure().add((ContextualResultListener<T, Throwable>) Assert.notNull(listener, "listener"));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called after a failure occurs that cannot be retried.
   */
  public S onFailure(FailureListener<? extends Throwable> listener) {
    config().failure().add(listenerOf(listener));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called after a failure occurs that cannot be retried.
   */
  public S onFailure(ResultListener<? extends T, ? extends Throwable> listener) {
    config().failure().add(listenerOf(listener));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} after a failure occurs that
   * cannot be retried.
   */
  public S onFailureAsync(ContextualResultListener<? extends T, ? extends Throwable> listener,
      ExecutorService executor) {
    config().failure().add(listenerOf(listener, Assert.notNull(executor, "executor"), null));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} after a failure occurs that
   * cannot be retried.
   */
  public S onFailureAsync(FailureListener<? extends Throwable> listener, ExecutorService executor) {
    config().failure().add(listenerOf(listenerOf(listener), Assert.notNull(executor, "executor"), null));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} after a failure occurs that
   * cannot be retried.
   */
  public S onFailureAsync(ResultListener<? extends T, ? extends Throwable> listener, ExecutorService executor) {
    config().failure().add(listenerOf(listenerOf(listener), Assert.notNull(executor, "executor"), null));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called when the retry policy is exceeded and the result is a failure.
   */
  public S onRetriesExceeded(FailureListener<? extends Throwable> listener) {
    config().retriesExceeded().add(listenerOf(listener));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called when the retry policy is exceeded and the result is a failure.
   */
  public S onRetriesExceeded(ResultListener<? extends T, ? extends Throwable> listener) {
    config().retriesExceeded().add(listenerOf(listener));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when the retry policy is
   * exceeded and the result is a failure.
   */
  public S onRetriesExceededAsync(FailureListener<? extends Throwable> listener, ExecutorService executor) {
    config().retriesExceeded().add(listenerOf(listenerOf(listener), Assert.notNull(executor, "executor"), null));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} when the retry policy is
   * exceeded and the result is a failure.
   */
  public S onRetriesExceededAsync(ResultListener<? extends T, ? extends Throwable> listener, ExecutorService executor) {
    config().retriesExceeded().add(listenerOf(listenerOf(listener), Assert.notNull(executor, "executor"), null));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called before a retry is attempted.
   */
  public S onRetry(ContextualResultListener<? extends T, ? extends Throwable> listener) {
    config().retry().add((ContextualResultListener<T, Throwable>) Assert.notNull(listener, "listener"));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called before a retry is attempted.
   */
  public S onRetry(FailureListener<? extends Throwable> listener) {
    config().retry().add(listenerOf(listener));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called before a retry is attempted.
   */
  public S onRetry(ResultListener<? extends T, ? extends Throwable> listener) {
    config().retry().add(listenerOf(listener));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} before a retry is attempted.
   */
  public S onRetryAsync(ContextualResultListener<? extends T, ? extends Throwable> listener, ExecutorService executor) {
    config().retry().add(listenerOf(listener, Assert.notNull(executor, "executor"), null));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} before a retry is attempted.
   */
  public S onRetryAsync(FailureListener<? extends Throwable> listener, ExecutorService executor) {
    config().retry().add(listenerOf(listenerOf(listener), Assert.notNull(executor, "executor"), null));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} before a retry is attempted.
   */
  public S onRetryAsync(ResultListener<? extends T, ? extends Throwable> listener, ExecutorService executor) {
    config().retry().add(listenerOf(listenerOf(listener), Assert.notNull(executor, "executor"), null));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called after a successful execution.
   */
  public S onSuccess(ContextualSuccessListener<? extends T> listener) {
    config().success().add(listenerOf(listener));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called after a successful execution.
   */
  public S onSuccess(SuccessListener<? extends T> listener) {
    config().success().add(listenerOf(listener));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} after a successful execution.
   */
  public S onSuccessAsync(ContextualSuccessListener<? extends T> listener, ExecutorService executor) {
    config().success().add(listenerOf(listenerOf(listener), Assert.notNull(executor, "executor"), null));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on the {@code executor} after a successful execution.
   */
  public S onSuccessAsync(SuccessListener<? extends T> listener, ExecutorService executor) {
    config().success().add(listenerOf(listenerOf(listener), Assert.notNull(executor, "executor"), null));
    return (S) this;
  }

  void complete(T result, Throwable failure, ExecutionContext context, boolean success) {
    if (success)
      handleSuccess(result, context);
    else
      handleFailure(result, failure, context);
    handleComplete(result, failure, context);
  }

  ListenerConfig<T> config() {
    return listenerConfig != null ? listenerConfig : (listenerConfig = new ListenerConfig<T>());
  }

  void handleAbort(T result, Throwable failure, ExecutionContext context) {
    if (listenerConfig != null && listenerConfig.abortListeners != null) {
      context = context.copy();
      call(listenerConfig.abortListeners, result, failure, context);
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

  void handleComplete(T result, Throwable failure, ExecutionContext context) {
    if (listenerConfig != null && listenerConfig.completeListeners != null) {
      context = context.copy();
      call(listenerConfig.completeListeners, result, failure, context);
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

  void handleFailedAttempt(T result, Throwable failure, ExecutionContext context) {
    if (listenerConfig != null && listenerConfig.failedAttemptListeners != null) {
      context = context.copy();
      call(listenerConfig.failedAttemptListeners, result, failure, context);
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

  void handleFailure(T result, Throwable failure, ExecutionContext context) {
    if (listenerConfig != null && listenerConfig.failureListeners != null) {
      context = context.copy();
      call(listenerConfig.failureListeners, result, failure, context);
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

  void handleRetriesExceeded(T result, Throwable failure, ExecutionContext context) {
    if (listenerConfig != null && listenerConfig.retriesExceededListeners != null) {
      context = context.copy();
      call(listenerConfig.retriesExceededListeners, result, failure, context);
    }

    if (listeners != null) {
      try {
        listeners.onRetriesExceeded(result, failure);
      } catch (Exception ignore) {
      }
    }
  }

  void handleRetry(T result, Throwable failure, ExecutionContext context) {
    if (listenerConfig != null && listenerConfig.retryListeners != null) {
      context = context.copy();
      call(listenerConfig.retryListeners, result, failure, context);
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

  void handleSuccess(T result, ExecutionContext context) {
    if (listenerConfig != null && listenerConfig.successListeners != null) {
      context = context.copy();
      call(listenerConfig.successListeners, result, null, context);
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
