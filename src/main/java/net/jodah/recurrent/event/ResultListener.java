package net.jodah.recurrent.event;

/**
 * Listens for an execution result.
 * 
 * @author Jonathan Halterman
 * @param <R> result type
 * @param <F> failure type
 */
public interface ResultListener<R, F extends Throwable> {
  /**
   * Handles an execution result.
   * 
   * @param result The execution result, else {@code null} if the call failed
   * @param failure The execution failure, else {@code null} if the call was successful
   */
  void onResult(R result, F failure);
}
