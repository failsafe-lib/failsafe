package net.jodah.failsafe.function;

import net.jodah.failsafe.ExecutionContext;

/**
 * A runnable that provides execution context.
 * 
 * @author Jonathan Halterman
 */
public interface ContextualRunnable {
  void run(ExecutionContext context) throws Exception;
}
