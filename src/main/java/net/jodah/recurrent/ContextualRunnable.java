package net.jodah.recurrent;

/**
 * A runnable that can manually trigger retries or completion for an invocation.
 * 
 * @author Jonathan Halterman
 */
public interface ContextualRunnable {
  void run(Invocation invocation) throws Exception;
}
