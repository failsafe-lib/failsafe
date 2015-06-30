package net.jodah.recurrent.event;

/**
 * Listens for an asynchronous invocation to succeed.
 * 
 * @author Jonathan Halterman
 * @param <T> result type
 */
public interface SuccessListener<T> {
  /**
   * Handles the successful completion of a call.
   */
  void onSuccess(T result);
}
