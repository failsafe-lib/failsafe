package net.jodah.failsafe.spi;

import net.jodah.failsafe.ExecutionContext;
import net.jodah.failsafe.PolicyListeners;

/**
 * Event handlers for {@link PolicyListeners}.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public interface PolicyHandlers<R> {
  void handleSuccess(ExecutionResult<R> result, ExecutionContext<R> context);

  void handleFailure(ExecutionResult<R> result, ExecutionContext<R> context);
}