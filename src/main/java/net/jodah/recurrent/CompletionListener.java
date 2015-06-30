package net.jodah.recurrent;

/**
 * Listens for an asynchronous invocation to complete.
 * 
 * @author Jonathan Halterman
 * @param <T> result type
 */
public interface CompletionListener<T> {
  /**
   * Handles the completion of a call.
   * 
   * @param result null if the call failed
   * @param failure null if the call was successful
   */
  void onCompletion(T result, Throwable failure);
}
