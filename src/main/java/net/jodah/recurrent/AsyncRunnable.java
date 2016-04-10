package net.jodah.recurrent;

/**
 * A runnable that can manually trigger asynchronous retries or completion for an invocation.
 * 
 * @author Jonathan Halterman
 */
public interface AsyncRunnable {
  void run(AsyncInvocation invocation) throws Exception;
}
