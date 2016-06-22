package net.jodah.failsafe;

import java.util.List;

import net.jodah.failsafe.function.BiPredicate;
import net.jodah.failsafe.function.Predicate;

/**
 * Utility for creating predicates.
 * 
 * @author Jonathan Halterman
 */
final class Predicates {
  /**
   * Returns a predicate that evaluates whether the {@code result} equals an execution result.
   */
  static BiPredicate<Object, Throwable> resultPredicateFor(Object result) {
    return new BiPredicate<Object, Throwable>() {
      @Override
      public boolean test(Object t, Throwable u) {
        return result == null ? t == null : result.equals(t);
      }
    };
  }

  /**
   * Returns a predicate that evaluates the {@code failurePredicate} against a failure.
   */
  @SuppressWarnings("unchecked")
  static BiPredicate<Object, Throwable> failurePredicateFor(Predicate<? extends Throwable> failurePredicate) {
    return new BiPredicate<Object, Throwable>() {
      @Override
      public boolean test(Object t, Throwable u) {
        return u != null && ((Predicate<Throwable>) failurePredicate).test(u);
      }
    };
  }

  /**
   * Returns a predicate that evaluates the {@code resultPredicate} against a result.
   */
  @SuppressWarnings("unchecked")
  static <T> BiPredicate<Object, Throwable> resultPredicateFor(Predicate<T> resultPredicate) {
    return new BiPredicate<Object, Throwable>() {
      @Override
      public boolean test(Object t, Throwable u) {
        return ((Predicate<Object>) resultPredicate).test(t);
      }
    };
  }

  /**
   * Returns a predicate that returns whether any of the {@code failures} are assignable from an execution failure.
   */
  static BiPredicate<Object, Throwable> failurePredicateFor(List<Class<? extends Throwable>> failures) {
    return new BiPredicate<Object, Throwable>() {
      @Override
      public boolean test(Object t, Throwable u) {
        if (u == null)
          return false;
        for (Class<? extends Throwable> failureType : failures)
          if (failureType.isAssignableFrom(u.getClass()))
            return true;
        return false;
      }
    };
  }
}
