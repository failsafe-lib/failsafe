package net.jodah.recurrent;

/**
 * A runnable that manually triggers asynchronous retries or completion.
 * 
 * @author Jonathan Halterman
 */
public interface AsyncRunnable {
  void run(AsyncExecution execution) throws Exception;
}
