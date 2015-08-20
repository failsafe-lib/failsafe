package net.jodah.recurrent.event;

/**
 * Listens for an invocation result.
 * 
 * @author Jonathan Halterman
 * @param <R> result type
 * @param <F> failure type
 */
public interface ResultListener<R, F extends Throwable> {
  /**
   * Handles an invocation result.
   * 
   * @param result The invocation result, else {@code null} if the call failed
   * @param failure The invocation failure, else {@code null} if the call was successful
   */
  void onResult(R result, F failure);
}
