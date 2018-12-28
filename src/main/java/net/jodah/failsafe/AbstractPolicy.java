package net.jodah.failsafe;

import net.jodah.failsafe.function.Predicate;
import net.jodah.failsafe.internal.util.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * Base Policy implementation that captures conditions to determine whether an execution is a failure. If no failure
 * conditions are configured:
 * <ul>
 * <li>If no other policies are configured, the execution is considered a failure if an Exception was thrown.</li>
 * <li>If other policies were configured, the execution is considered a failure if the previous configured Policy's
 * handling of the execution failed.</li>
 * </ul>
 *
 * @param <R> result type
 */
@SuppressWarnings("unchecked")
public abstract class AbstractPolicy<R> implements Policy {
  /** Indicates whether failures are checked by a configured failure condition */
  protected boolean failuresChecked;
  /** Conditions that determine whether an execution is a failure */
  protected List<BiPredicate<Object, Throwable>> failureConditions;

  AbstractPolicy() {
    failureConditions = new ArrayList<>();
  }

  /**
   * Specifies the failure to retryOn. Any failure that is assignable from the {@code failure} will be handled.
   *
   * @throws NullPointerException if {@code failure} is null
   */
  @SuppressWarnings({ "rawtypes" })
  public R handle(Class<? extends Throwable> failure) {
    Assert.notNull(failure, "failure");
    return handle(Arrays.asList(failure));
  }

  /**
   * Specifies the failures to retryOn. Any failures that are assignable from the {@code failures} will be handled.
   *
   * @throws NullPointerException if {@code failures} is null
   * @throws IllegalArgumentException if failures is empty
   */
  @SuppressWarnings("unchecked")
  public R handle(Class<? extends Throwable>... failures) {
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
  public R handle(List<Class<? extends Throwable>> failures) {
    Assert.notNull(failures, "failures");
    Assert.isTrue(!failures.isEmpty(), "failures cannot be empty");
    failuresChecked = true;
    failureConditions.add(Predicates.failurePredicateFor(failures));
    return (R) this;
  }

  /**
   * Specifies that a failure has occurred if the {@code failurePredicate} matches the failure.
   *
   * @throws NullPointerException if {@code failurePredicate} is null
   */
  public R handleIf(Predicate<? extends Throwable> failurePredicate) {
    Assert.notNull(failurePredicate, "failurePredicate");
    failuresChecked = true;
    failureConditions.add(Predicates.failurePredicateFor(failurePredicate));
    return (R) this;
  }

  /**
   * Specifies that a failure has occurred if the {@code resultPredicate} matches the execution result.
   *
   * @throws NullPointerException if {@code resultPredicate} is null
   */
  @SuppressWarnings("unchecked")
  public <T> R handleIf(BiPredicate<T, ? extends Throwable> resultPredicate) {
    Assert.notNull(resultPredicate, "resultPredicate");
    failuresChecked = true;
    failureConditions.add((BiPredicate<Object, Throwable>) resultPredicate);
    return (R) this;
  }

  /**
   * Specifies that a failure has occurred if the {@code result} matches the execution result.
   */
  public R handleResult(Object result) {
    failureConditions.add(Predicates.resultPredicateFor(result));
    return (R) this;
  }

  /**
   * Specifies that a failure has occurred if the {@code resultPredicate} matches the execution result.
   *
   * @throws NullPointerException if {@code resultPredicate} is null
   */
  public <T> R handleResultIf(Predicate<T> resultPredicate) {
    Assert.notNull(resultPredicate, "resultPredicate");
    failureConditions.add(Predicates.resultPredicateFor(resultPredicate));
    return (R) this;
  }

  /**
   * Returns whether an execution result can be retried given the configured failure conditions.
   *
   * @see #handle(Class...)
   * @see #handle(List)
   * @see #handleIf(BiPredicate)
   * @see #handleIf(Predicate)
   * @see #handleResult(Object)
   * @see #handleResultIf(Predicate)
   */
  boolean isFailure(ExecutionResult result) {
    return failureConditions.isEmpty() ? !result.success : isFailure(result.result, result.failure);
  }

  /**
   * Returns whether an execution result can be retried given the configured failure conditions.
   *
   * @see #handle(Class...)
   * @see #handle(List)
   * @see #handleIf(BiPredicate)
   * @see #handleIf(Predicate)
   * @see #handleResult(Object)
   * @see #handleResultIf(Predicate)
   */
  public boolean isFailure(Object result, Throwable failure) {
    for (BiPredicate<Object, Throwable> predicate : failureConditions) {
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
}
