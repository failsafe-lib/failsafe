package net.jodah.failsafe.function;

public interface BiFunction<T, U, R> {
  R apply(T t, U u);
}
