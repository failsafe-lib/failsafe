package net.jodah.failsafe.function;

public interface CheckedConsumer<T> {
  void accept(T t) throws Exception;
}