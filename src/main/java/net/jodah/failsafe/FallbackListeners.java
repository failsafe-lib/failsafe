package net.jodah.failsafe;

import net.jodah.failsafe.event.EventListener;
import net.jodah.failsafe.event.ExecutionAttemptedEvent;

/**
 * Configures listeners for a {@link Fallback}.
 *
 * @param <S> self type
 * @param <R> result type
 * @author Jonathan Halterman
 */
public interface FallbackListeners<S, R> extends PolicyListeners<S, R> {
  /**
   * Registers the {@code listener} to be called when the last execution attempt prior to the fallback failed. You can
   * also use {@link #onFailure(EventListener) onFailure} to determine when the fallback attempt also fails.
   * <p>Note: Any exceptions that are thrown from within the {@code listener} are ignored.</p>
   */
  S onFailedAttempt(EventListener<ExecutionAttemptedEvent<R>> listener);
}
