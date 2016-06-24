package net.jodah.failsafe;

import java.util.concurrent.ScheduledExecutorService;

import net.jodah.failsafe.event.ContextualResultListener;
import net.jodah.failsafe.event.ContextualSuccessListener;
import net.jodah.failsafe.event.FailureListener;
import net.jodah.failsafe.event.ResultListener;
import net.jodah.failsafe.event.SuccessListener;
import net.jodah.failsafe.util.concurrent.Scheduler;

/**
 * Failsafe execution event listeners that are called asynchronously on the {@link Scheduler} or
 * {@link ScheduledExecutorService} associated with the Failsafe call.
 * 
 * @author Jonathan Halterman
 * @param <S> source type
 * @param <T> result type
 */
@SuppressWarnings("unchecked")
public class AsyncListenerBindings<S, T> extends ListenerBindings<S, T> {
  final Scheduler scheduler;

  AsyncListenerBindings(Scheduler scheduler) {
    this.scheduler = scheduler;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler when an
   * execution is aborted according to the retry policy.
   */
  public S onAbortAsync(ContextualResultListener<? extends T, ? extends Throwable> listener) {
    config().abort().add(listenerOf(listener, null, scheduler));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler when an
   * execution is aborted according to the retry policy.
   */
  public S onAbortAsync(FailureListener<? extends Throwable> listener) {
    config().abort().add(listenerOf(listenerOf(listener), null, scheduler));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler when an
   * execution is aborted according to the retry policy.
   */
  public S onAbortAsync(ResultListener<? extends T, ? extends Throwable> listener) {
    config().abort().add(listenerOf(listenerOf(listener), null, scheduler));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler when an
   * execution is completed.
   */
  public S onCompleteAsync(ContextualResultListener<? extends T, ? extends Throwable> listener) {
    config().complete().add(listenerOf(listener, null, scheduler));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler when an
   * execution is completed.
   */
  public S onCompleteAsync(ResultListener<? extends T, ? extends Throwable> listener) {
    config().complete().add(listenerOf(listenerOf(listener), null, scheduler));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler after a
   * failed execution attempt.
   */
  public S onFailedAttemptAsync(ContextualResultListener<? extends T, ? extends Throwable> listener) {
    config().failedAttempt().add(listenerOf(listener, null, scheduler));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler after a
   * failed execution attempt.
   */
  public S onFailedAttemptAsync(FailureListener<? extends Throwable> listener) {
    config().failedAttempt().add(listenerOf(listenerOf(listener), null, scheduler));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler after a
   * failed execution attempt.
   */
  public S onFailedAttemptAsync(ResultListener<? extends T, ? extends Throwable> listener) {
    config().failedAttempt().add(listenerOf(listenerOf(listener), null, scheduler));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler after a
   * failure occurs that cannot be retried.
   */
  public S onFailureAsync(ContextualResultListener<? extends T, ? extends Throwable> listener) {
    config().failure().add(listenerOf(listener, null, scheduler));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler after a
   * failure occurs that cannot be retried.
   */
  public S onFailureAsync(FailureListener<? extends Throwable> listener) {
    config().failure().add(listenerOf(listenerOf(listener), null, scheduler));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler after a
   * failure occurs that cannot be retried.
   */
  public S onFailureAsync(ResultListener<? extends T, ? extends Throwable> listener) {
    config().failure().add(listenerOf(listenerOf(listener), null, scheduler));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler when the
   * retry policy is exceeded and the result is a failure.
   */
  public S onRetriesExceededAsync(FailureListener<? extends Throwable> listener) {
    config().retriesExceeded().add(listenerOf(listenerOf(listener), null, scheduler));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler when the
   * retry policy is exceeded and the result is a failure.
   */
  public S onRetriesExceededAsync(ResultListener<? extends T, ? extends Throwable> listener) {
    config().retriesExceeded().add(listenerOf(listenerOf(listener), null, scheduler));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler before a
   * retry is attempted.
   */
  public S onRetryAsync(ContextualResultListener<? extends T, ? extends Throwable> listener) {
    config().retry().add(listenerOf(listener, null, scheduler));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler before a
   * retry is attempted.
   */
  public S onRetryAsync(FailureListener<? extends Throwable> listener) {
    config().retry().add(listenerOf(listenerOf(listener), null, scheduler));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler before a
   * retry is attempted.
   */
  public S onRetryAsync(ResultListener<? extends T, ? extends Throwable> listener) {
    config().retry().add(listenerOf(listenerOf(listener), null, scheduler));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler after a
   * successful execution.
   */
  public S onSuccessAsync(ContextualSuccessListener<? extends T> listener) {
    config().success().add(listenerOf(listenerOf(listener), null, scheduler));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler after a
   * successful execution.
   */
  public S onSuccessAsync(SuccessListener<? extends T> listener) {
    config().success().add(listenerOf(listenerOf(listener), null, scheduler));
    return (S) this;
  }
}
