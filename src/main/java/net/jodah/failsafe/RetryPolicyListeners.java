package net.jodah.failsafe;

import net.jodah.failsafe.event.EventListener;
import net.jodah.failsafe.event.ExecutionAttemptedEvent;
import net.jodah.failsafe.event.ExecutionCompletedEvent;
import net.jodah.failsafe.event.ExecutionScheduledEvent;

/**
 * Configures listeners for a {@link RetryPolicy}.
 *
 * @param <S> self type
 * @param <R> result type
 * @author Jonathan Halterman
 */
public interface RetryPolicyListeners<S, R> extends PolicyListeners<S, R> {
  /**
   * Registers the {@code listener} to be called when an execution is aborted.
   * <p>Note: Any exceptions that are thrown from within the {@code listener} are ignored. To provide an alternative
   * result for a failed execution, use a {@link Fallback}.</p>
   */
  S onAbort(EventListener<ExecutionCompletedEvent<R>> listener);

  /**
   * Registers the {@code listener} to be called when an execution attempt fails. You can also use {@link
   * #onFailure(EventListener) onFailure} to determine when the execution attempt fails <i>and</i> and all retries
   * have failed.
   * <p>Note: Any exceptions that are thrown from within the {@code listener} are ignored. To provide an alternative
   * result for a failed execution, use a {@link Fallback}.</p>
   */
  S onFailedAttempt(EventListener<ExecutionAttemptedEvent<R>> listener);

  /**
   * Registers the {@code listener} to be called when an execution fails and the {@link
   * RetryPolicyConfig#getMaxRetries() max retry attempts} or {@link RetryPolicyConfig#getMaxDuration() max duration}
   * are exceeded.
   * <p>Note: Any exceptions that are thrown from within the {@code listener} are ignored. To provide an alternative
   * result for a failed execution, use a {@link Fallback}.</p>
   */
  S onRetriesExceeded(EventListener<ExecutionCompletedEvent<R>> listener);

  /**
   * Registers the {@code listener} to be called when a retry is about to be attempted.
   * <p>Note: Any exceptions that are thrown from within the {@code listener} are ignored. To provide an alternative
   * result for a failed execution, use a {@link Fallback}.</p>
   *
   * @see #onRetryScheduled(EventListener)
   */
  S onRetry(EventListener<ExecutionAttemptedEvent<R>> listener);

  /**
   * Registers the {@code listener} to be called when a retry for an async call is about to be scheduled. This method
   * differs from {@link #onRetry(EventListener)} since it is called when a retry is initially scheduled but before
   * any configured delay, whereas {@link #onRetry(EventListener) onRetry} is called after a delay, just before the
   * retry attempt takes place.
   * <p>
   * <p>Note: Any exceptions that are thrown from within the {@code listener} are ignored. To provide an alternative
   * result for a failed execution, use a {@link Fallback}.</p>
   *
   * @see #onRetry(EventListener)
   */
  S onRetryScheduled(EventListener<ExecutionScheduledEvent<R>> listener);
}
