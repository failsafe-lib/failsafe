package net.jodah.failsafe.event;

import net.jodah.failsafe.ExecutionContext;

/**
 * Listens for an execution success, providing {@link ExecutionContext} that describe executions so far.
 * 
 * @author Jonathan Halterman
 * @param <R> result type
 */
public interface ContextualSuccessListener<R> {
  /**
   * Handles the successful completion of a call.
   */
  void onSuccess(R result, ExecutionContext context);
}
