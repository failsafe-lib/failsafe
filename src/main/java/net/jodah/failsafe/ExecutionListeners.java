package net.jodah.failsafe;

import net.jodah.failsafe.event.ExecutionCompletedEvent;
import net.jodah.failsafe.function.CheckedConsumer;

/**
 * Configures listeners for a Policy execution result.
 *
 * @param <S> self type
 * @param <R> result type
 * @author Jonathan Halterman
 */
public interface ExecutionListeners<S extends Policy<R>, R> {
  /**
   * Registers the {@code listener} to be called when the policy fails to handle an execution. This means that not only
   * was the supplied execution considered a failure by the policy, but that the policy was unable to produce a
   * successful result.
   * <p>Note: Any exceptions that are thrown from within the {@code listener} are ignored. To provide an alternative
   * result for a failed execution, use a {@link Fallback}.</p>
   */
  S onFailure(CheckedConsumer<ExecutionCompletedEvent<R>> listener);

  /**
   * Registers the {@code listener} to be called when the policy succeeds in handling an execution. This means that the
   * supplied execution either succeeded, or if it failed, the policy was able to produce a successful result.
   * <p>Note: Any exceptions that are thrown from within the {@code listener} are ignored.</p>
   */
  S onSuccess(CheckedConsumer<ExecutionCompletedEvent<R>> listener);
}
