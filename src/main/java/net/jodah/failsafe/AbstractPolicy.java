package net.jodah.failsafe;

import net.jodah.failsafe.internal.util.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Base Policy implementation that captures conditions to determine whether an execution is a failure. If no failure
 * conditions are configured:
 * <ul>
 * <li>If no other policies are configured, the execution is considered a failure if an Exception was thrown.</li>
 * <li>If other policies were configured, the execution is considered a failure if the previous configured Policy's
 * handling of the execution failed.</li>
 * </ul>
 *
 * @param <S> self type
 * @param <R> result type
 */
@SuppressWarnings("unchecked")
public abstract class AbstractPolicy<S, R> extends PolicyListeners<S, R> implements Policy<R> {
  /** Indicates whether failures are checked by a configured failure condition */
  protected boolean failuresChecked;
  /** Conditions that determine whether an execution is a failure */
  protected List<BiPredicate<R, Throwable>> failureConditions;

  AbstractPolicy() {
    failureConditions = new ArrayList<>();
  }

  /**
   * Specifies the failure to retryOn. Any failure that is assignable from the {@code failure} will be handled.
   *
   * @throws NullPointerException if {@code failure} is null
   */
  public S handle(Class<? extends Throwable> failure) {
    Assert.notNull(failure, "failure");
    return handle(Arrays.asList(failure));
  }

  /**
   * Specifies the failures to retryOn. Any failures that are assignable from the {@code failures} will be handled.
   *
   * @throws NullPointerException if {@code failures} is null
   * @throws IllegalArgumentException if failures is empty
   */
  public S handle(Class<? extends Throwable>... failures) {
    Assert.notNull(failures, "failures");
    Assert.isTrue(failures.length > 0, "Failures cannot be empty");
    return handle(Arrays.asList(failures));
  }

  /**
   * Specifies the failures to retryOn. Any failures that are assignable from the {@code failures} will be handled.
   *
   * @throws NullPointerException if {@code failures} is null
   * @throws IllegalArgumentException if failures is null or empty
   */
  public S handle(List<Class<? extends Throwable>> failures) {
    Assert.notNull(failures, "failures");
    Assert.isTrue(!failures.isEmpty(), "failures cannot be empty");
    failuresChecked = true;
    failureConditions.add(failurePredicateFor(failures));
    return (S) this;
  }

  /**
   * Specifies that a failure has occurred if the {@code failurePredicate} matches the failure.
   *
   * @throws NullPointerException if {@code failurePredicate} is null
   */
  public S handleIf(Predicate<? extends Throwable> failurePredicate) {
    Assert.notNull(failurePredicate, "failurePredicate");
    failuresChecked = true;
    failureConditions.add(failurePredicateFor(failurePredicate));
    return (S) this;
  }

  /**
   * Specifies that a failure has occurred if the {@code resultPredicate} matches the execution result.
   *
   * @throws NullPointerException if {@code resultPredicate} is null
   */
  @SuppressWarnings("unchecked")
  public S handleIf(BiPredicate<R, ? extends Throwable> resultPredicate) {
    Assert.notNull(resultPredicate, "resultPredicate");
    failuresChecked = true;
    failureConditions.add((BiPredicate<R, Throwable>) resultPredicate);
    return (S) this;
  }

  /**
   * Specifies that a failure has occurred if the {@code result} matches the execution result.
   */
  public S handleResult(R result) {
    failureConditions.add(resultPredicateFor(result));
    return (S) this;
  }

  /**
   * Specifies that a failure has occurred if the {@code resultPredicate} matches the execution result.
   *
   * @throws NullPointerException if {@code resultPredicate} is null
   */
  public S handleResultIf(Predicate<R> resultPredicate) {
    Assert.notNull(resultPredicate, "resultPredicate");
    failureConditions.add(resultPredicateFor(resultPredicate));
    return (S) this;
  }

  /**
   * Returns whether an execution result can be retried given the configured failure conditions.
   *
   * @see #handle(Class...)
   * @see #handle(List)
   * @see #handleIf(BiPredicate)
   * @see #handleIf(Predicate)
   * @see #handleResult(R)
   * @see #handleResultIf(Predicate)
   */
  boolean isFailure(ExecutionResult result) {
    return failureConditions.isEmpty() ? result.failure != null : isFailure((R) result.result, result.failure);
  }

  /**
   * Returns whether an execution result can be retried given the configured failure conditions.
   *
   * @see #handle(Class...)
   * @see #handle(List)
   * @see #handleIf(BiPredicate)
   * @see #handleIf(Predicate)
   * @see #handleResult(R)
   * @see #handleResultIf(Predicate)
   */
  public boolean isFailure(R result, Throwable failure) {
    for (BiPredicate<R, Throwable> predicate : failureConditions) {
      try {
        if (predicate.test(result, failure))
          return true;
      } catch (Exception ignored) {
        // Ignore confused user-supplied predicates.
        // They should not be allowed to halt execution of the operation.
      }
    }

    // Fail by default if a failure is not checked by a condition
    return failure != null && !failuresChecked;
  }

  /**
   * Returns a predicate that evaluates whether the {@code result} equals an execution result.
   */
  static <R> BiPredicate<R, Throwable> resultPredicateFor(R result) {
    return (t, u) -> Objects.equals(result, t);
  }

  /**
   * Returns a predicate that evaluates the {@code failurePredicate} against a failure.
   */
  @SuppressWarnings("unchecked")
  static <R> BiPredicate<R, Throwable> failurePredicateFor(Predicate<? extends Throwable> failurePredicate) {
    return (t, u) -> u != null && ((Predicate<Throwable>) failurePredicate).test(u);
  }

  /**
   * Returns a predicate that evaluates the {@code resultPredicate} against a result, when present.
   *
   * Short-circuts to false without invoking {@code resultPredicate},
   * when result is not present (i.e. BiPredicate.test(null, Throwable)).
   */
  static <R> BiPredicate<R, Throwable> resultPredicateFor(Predicate<R> resultPredicate) {
    return (t, u) -> {
      if (u == null) {
        return resultPredicate.test(t);
      } else {
        // resultPredicate is only defined over the success type.
        // It doesn't know how to handle a failure of type Throwable,
        // so we return false here.
        return false;
      }
    };
  }

  /**
   * Returns a predicate that returns whether any of the {@code failures} are assignable from an execution failure.
   */
  static <R> BiPredicate<R, Throwable> failurePredicateFor(List<Class<? extends Throwable>> failures) {
    return (t, u) -> {
      if (u == null)
        return false;
      for (Class<? extends Throwable> failureType : failures)
        if (failureType.isAssignableFrom(u.getClass()))
          return true;
      return false;
    };
  }
}
