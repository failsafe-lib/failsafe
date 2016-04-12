package net.jodah.recurrent;

/**
 * A callable that manually triggers asynchronous retries or completion.
 * 
 * @author Jonathan Halterman
 * @param <T> result type
 */
public interface AsyncCallable<T> {
  T call(AsyncExecution execution) throws Exception;
}
