package net.jodah.failsafe.function;

import net.jodah.failsafe.ExecutionContext;

/**
 * A callable that provides execution context.
 * 
 * @author Jonathan Halterman
 * @param <T> result type
 */
public interface ContextualCallable<T> {
  T call(ExecutionContext context) throws Exception;
}
