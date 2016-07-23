package net.jodah.failsafe.function;

public interface Function<T, R> {
  R apply(T t);
}
