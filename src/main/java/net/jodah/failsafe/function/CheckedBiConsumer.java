package net.jodah.failsafe.function;

public interface CheckedBiConsumer<T, U> {
  void accept(T t, U u) throws Exception;
}