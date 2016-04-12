package net.jodah.recurrent;

/**
 * A runnable that provides contextual execution statistics.
 * 
 * @author Jonathan Halterman
 */
public interface ContextualRunnable {
  void run(ExecutionStats stats) throws Exception;
}
