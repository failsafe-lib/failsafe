package net.jodah.recurrent.util;

public interface BiPredicate<T, U> {
  boolean test(T t, U u);
}
