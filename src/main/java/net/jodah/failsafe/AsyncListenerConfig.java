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
 * @param <R> result type
 */
@SuppressWarnings("unchecked")
public class AsyncListenerConfig<S, R> extends ListenerConfig<S, R> {
  final Scheduler scheduler;

  AsyncListenerConfig(Scheduler scheduler) {
    this.scheduler = scheduler;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler when an
   * execution is aborted according to the retry policy.
   */
  public S onAbortAsync(ContextualResultListener<? extends R, ? extends Throwable> listener) {
    registry().abort().add(Listeners.of(listener, null, scheduler));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler when an
   * execution is aborted according to the retry policy.
   */
  public S onAbortAsync(FailureListener<? extends Throwable> listener) {
    registry().abort().add(Listeners.of(Listeners.<R>of(listener), null, scheduler));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler when an
   * execution is aborted according to the retry policy.
   */
  public S onAbortAsync(ResultListener<? extends R, ? extends Throwable> listener) {
    registry().abort().add(Listeners.of(Listeners.of(listener), null, scheduler));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler when an
   * execution is completed.
   */
  public S onCompleteAsync(ContextualResultListener<? extends R, ? extends Throwable> listener) {
    registry().complete().add(Listeners.of(listener, null, scheduler));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler when an
   * execution is completed.
   */
  public S onCompleteAsync(ResultListener<? extends R, ? extends Throwable> listener) {
    registry().complete().add(Listeners.of(Listeners.of(listener), null, scheduler));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler after a
   * failed execution attempt.
   */
  public S onFailedAttemptAsync(ContextualResultListener<? extends R, ? extends Throwable> listener) {
    registry().failedAttempt().add(Listeners.of(listener, null, scheduler));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler after a
   * failed execution attempt.
   */
  public S onFailedAttemptAsync(FailureListener<? extends Throwable> listener) {
    registry().failedAttempt().add(Listeners.of(Listeners.<R>of(listener), null, scheduler));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler after a
   * failed execution attempt.
   */
  public S onFailedAttemptAsync(ResultListener<? extends R, ? extends Throwable> listener) {
    registry().failedAttempt().add(Listeners.of(Listeners.of(listener), null, scheduler));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler after a
   * failure occurs that cannot be retried.
   */
  public S onFailureAsync(ContextualResultListener<? extends R, ? extends Throwable> listener) {
    registry().failure().add(Listeners.of(listener, null, scheduler));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler after a
   * failure occurs that cannot be retried.
   */
  public S onFailureAsync(FailureListener<? extends Throwable> listener) {
    registry().failure().add(Listeners.of(Listeners.<R>of(listener), null, scheduler));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler after a
   * failure occurs that cannot be retried.
   */
  public S onFailureAsync(ResultListener<? extends R, ? extends Throwable> listener) {
    registry().failure().add(Listeners.of(Listeners.of(listener), null, scheduler));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler when an
   * execution fails and the max retry attempts or duration are exceeded.
   */
  public S onRetriesExceededAsync(FailureListener<? extends Throwable> listener) {
    registry().retriesExceeded().add(Listeners.of(Listeners.<R>of(listener), null, scheduler));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler when an
   * execution fails and the max retry attempts or duration are exceeded.
   */
  public S onRetriesExceededAsync(ResultListener<? extends R, ? extends Throwable> listener) {
    registry().retriesExceeded().add(Listeners.of(Listeners.of(listener), null, scheduler));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler before a
   * retry is attempted.
   */
  public S onRetryAsync(ContextualResultListener<? extends R, ? extends Throwable> listener) {
    registry().retry().add(Listeners.of(listener, null, scheduler));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler before a
   * retry is attempted.
   */
  public S onRetryAsync(FailureListener<? extends Throwable> listener) {
    registry().retry().add(Listeners.of(Listeners.<R>of(listener), null, scheduler));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler before a
   * retry is attempted.
   */
  public S onRetryAsync(ResultListener<? extends R, ? extends Throwable> listener) {
    registry().retry().add(Listeners.of(Listeners.of(listener), null, scheduler));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler after a
   * successful execution.
   */
  public S onSuccessAsync(ContextualSuccessListener<? extends R> listener) {
    registry().success().add(Listeners.of(Listeners.of(listener), null, scheduler));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler after a
   * successful execution.
   */
  public S onSuccessAsync(SuccessListener<? extends R> listener) {
    registry().success().add(Listeners.of(Listeners.of(listener), null, scheduler));
    return (S) this;
  }
}
