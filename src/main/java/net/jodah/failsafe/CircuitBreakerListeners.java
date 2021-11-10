package net.jodah.failsafe;

import net.jodah.failsafe.event.CircuitBreakerStateChangedEvent;
import net.jodah.failsafe.event.EventListener;

/**
 * Configures listeners for a {@link CircuitBreaker}.
 *
 * @param <S> self type
 * @param <R> result type
 * @author Jonathan Halterman
 */
public interface CircuitBreakerListeners<S, R> extends PolicyListeners<S, R> {
  /**
   * Calls the {@code listener} when the circuit is closed.
   * <p>Note: Any exceptions that are thrown from within the {@code listener} are ignored.</p>
   *
   * @throws NullPointerException if {@code listener} is null
   */
  S onClose(EventListener<CircuitBreakerStateChangedEvent> listener);

  /**
   * Calls the {@code listener} when the circuit is half-opened.
   * <p>Note: Any exceptions that are thrown within the {@code listener} are ignored.</p>
   *
   * @throws NullPointerException if {@code listener} is null
   */
  S onHalfOpen(EventListener<CircuitBreakerStateChangedEvent> listener);

  /**
   * Calls the {@code listener} when the circuit is opened.
   * <p>Note: Any exceptions that are thrown within the {@code listener} are ignored.</p>
   *
   * @throws NullPointerException if {@code listener} is null
   */
  S onOpen(EventListener<CircuitBreakerStateChangedEvent> listener);
}
