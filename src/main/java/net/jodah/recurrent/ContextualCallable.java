package net.jodah.recurrent;

public interface ContextualCallable<T> {
  T call(Invocation invocation) throws Exception;
}
