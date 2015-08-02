package net.jodah.recurrent;

/**
 * A callable that can manually trigger retries or completion for an invocation.
 * 
 * @author Jonathan Halterman
 * @param <T> result type
 */
public interface ContextualCallable<T> {
  T call(Invocation invocation) throws Exception;
}
