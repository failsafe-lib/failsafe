package net.jodah.failsafe.function;

public interface BiPredicate<T, U> {
  boolean test(T t, U u);
}
