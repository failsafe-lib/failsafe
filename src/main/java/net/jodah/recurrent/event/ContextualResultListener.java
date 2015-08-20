package net.jodah.recurrent.event;

import net.jodah.recurrent.InvocationStats;

/**
 * Listens for an invocation result, providing {@link InvocationStats} that describe invocations so far.
 * 
 * @author Jonathan Halterman
 * @param <R> result type
 * @param <F> failure type
 */
public interface ContextualResultListener<R, F extends Throwable> {
  /**
   * Handles an invocation result.
   * 
   * @param result The invocation result, else {@code null} if the call failed
   * @param failure The invocation failure, else {@code null} if the call was successful
   */
  void onResult(R result, F failure, InvocationStats stats);
}
