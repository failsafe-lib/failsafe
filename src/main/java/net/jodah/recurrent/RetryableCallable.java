package net.jodah.recurrent;

public interface RetryableCallable<T> {
  T call(Invocation invocation) throws Exception;
}
