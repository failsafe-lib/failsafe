package net.jodah.failsafe.function;

import net.jodah.failsafe.AsyncExecution;

/**
 * A runnable that manually triggers asynchronous retries or completion via an asynchronous execution.
 * 
 * @author Jonathan Halterman
 */
public interface AsyncRunnable {
  void run(AsyncExecution execution) throws Exception;
}
