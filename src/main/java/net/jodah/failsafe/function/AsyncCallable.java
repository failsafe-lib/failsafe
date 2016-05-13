package net.jodah.failsafe.function;

import net.jodah.failsafe.AsyncExecution;

/**
 * A callable that manually triggers asynchronous retries or completion via an asynchronous execution.
 * 
 * @author Jonathan Halterman
 * @param <T> result type
 */
public interface AsyncCallable<T> {
  T call(AsyncExecution execution) throws Exception;
}
