package net.jodah.recurrent.event;

/**
 * Listens for an invocation success.
 * 
 * @author Jonathan Halterman
 * @param <R> result type
 */
public interface SuccessListener<R> {
  /**
   * Handles the successful completion of a call.
   */
  void onSuccess(R result);
}
