package net.jodah.recurrent;

/**
 * A callable that provides contextual execution statistics.
 * 
 * @author Jonathan Halterman
 * @param <T> result type
 */
public interface ContextualCallable<T> {
  T call(ExecutionStats stats) throws Exception;
}
