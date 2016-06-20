package net.jodah.failsafe.event;

/**
 * Listens for an execution failure.
 * 
 * @author Jonathan Halterman
 * @param <F> failure type
 */
public interface FailureListener<F extends Throwable> {
  /**
   * Handles an execution failure.
   * 
   * @param failure The execution failure
   */
  void onFailure(F failure);
}
