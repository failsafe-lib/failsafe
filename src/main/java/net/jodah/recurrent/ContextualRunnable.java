package net.jodah.recurrent;

/**
 * A runnable that provides contextual invocation statistics.
 * 
 * @author Jonathan Halterman
 */
public interface ContextualRunnable {
  void run(InvocationStats stats) throws Exception;
}
