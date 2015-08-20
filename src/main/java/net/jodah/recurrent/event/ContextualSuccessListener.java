package net.jodah.recurrent.event;

import net.jodah.recurrent.InvocationStats;

/**
 * Listens for an invocation success, providing {@link InvocationStats} that describe invocations so far.
 * 
 * @author Jonathan Halterman
 * @param <R> result type
 */
public interface ContextualSuccessListener<R> {
  /**
   * Handles the successful completion of a call.
   */
  void onSuccess(R result, InvocationStats stats);
}
