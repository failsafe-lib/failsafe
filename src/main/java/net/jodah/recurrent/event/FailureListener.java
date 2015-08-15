package net.jodah.recurrent.event;

/**
 * Listens for an asynchronous invocation to fail.
 * 
 * @author Jonathan Halterman
 */
public interface FailureListener {
  /**
   * Handles the failure of a call.
   */
  void onFailure(Throwable failure);
}
