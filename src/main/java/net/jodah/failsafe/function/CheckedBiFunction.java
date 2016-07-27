package net.jodah.failsafe.function;

public interface CheckedBiFunction<T, U, R> {
  R apply(T t, U u) throws Exception;
}
