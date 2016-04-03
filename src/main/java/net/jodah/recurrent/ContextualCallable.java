package net.jodah.recurrent;

/**
 * A callable that provides contextual invocation statistics.
 * 
 * @author Jonathan Halterman
 * @param <T> result type
 */
public interface ContextualCallable<T> {
  T call(InvocationStats stats) throws Exception;
}
