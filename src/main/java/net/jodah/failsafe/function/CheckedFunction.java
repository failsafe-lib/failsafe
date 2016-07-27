package net.jodah.failsafe.function;

public interface CheckedFunction<T, R> {
  R apply(T t) throws Exception;
}
