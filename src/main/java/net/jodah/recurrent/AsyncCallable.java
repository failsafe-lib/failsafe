package net.jodah.recurrent;

/**
 * A callable that can manually trigger asynchronous retries or completion for an invocation.
 * 
 * @author Jonathan Halterman
 * @param <T> result type
 */
public interface AsyncCallable<T> {
  T call(AsyncInvocation invocation) throws Exception;
}
