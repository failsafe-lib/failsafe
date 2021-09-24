package net.jodah.failsafe.spi;

import net.jodah.failsafe.ExecutionContext;

/**
 * Event handler for a completion listener.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public interface CompletionHandler<R> {
  void handleComplete(ExecutionResult<R> result, ExecutionContext<R> context);
}