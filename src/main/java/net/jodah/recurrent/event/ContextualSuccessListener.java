package net.jodah.recurrent.event;

import net.jodah.recurrent.ExecutionStats;

/**
 * Listens for an execution success, providing {@link ExecutionStats} that describe executions so far.
 * 
 * @author Jonathan Halterman
 * @param <R> result type
 */
public interface ContextualSuccessListener<R> {
  /**
   * Handles the successful completion of a call.
   */
  void onSuccess(R result, ExecutionStats stats);
}
