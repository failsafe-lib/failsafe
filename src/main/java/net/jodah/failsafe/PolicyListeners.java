package net.jodah.failsafe;

import net.jodah.failsafe.event.ExecutionCompletedEvent;
import net.jodah.failsafe.function.CheckedConsumer;
import net.jodah.failsafe.internal.EventListener;
import net.jodah.failsafe.internal.util.Assert;

/**
 * Policy listener configuration.
 *
 * @param <S> self type
 * @param <R> result type
 * @author Jonathan Halterman
 */
@SuppressWarnings("unchecked")
public class PolicyListeners<S, R> {
  EventListener failureListener;
  EventListener successListener;

  /**
   * Registers the {@code listener} to be called when an execution fails for a {@link Policy}.
   */
  public S onFailure(CheckedConsumer<? extends ExecutionCompletedEvent<R>> listener) {
    failureListener = EventListener.of(Assert.notNull(listener, "listener"));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution is successful for a {@link Policy}.
   */
  public S onSuccess(CheckedConsumer<? extends ExecutionCompletedEvent<R>> listener) {
    successListener = EventListener.of(Assert.notNull(listener, "listener"));
    return (S) this;
  }
}
