/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package net.jodah.failsafe;

import net.jodah.failsafe.internal.util.Assert;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * A Policy that allows configurable conditions to determine whether an execution is a failure.
 * <ul>
 *   <li>By default, any exception is considered a failure and will be handled by the policy. You can override this by
 *   specifying your own {@code handle} conditions. The default exception handling condition will only be overridden by
 *   another condition that handles failure exceptions such as {@link #handle(Class)} or {@link #handleIf(BiPredicate)}.
 *   Specifying a condition that only handles results, such as {@link #handleResult(Object)} or
 *   {@link #handleResultIf(Predicate)} will not replace the default exception handling condition.</li>
 *   <li>If multiple {@code handle} conditions are specified, any condition that matches an execution result or failure
 *   will trigger policy handling.</li>
 * </ul>
 *
 * @param <S> self type
 * @param <C> config type
 * @param <R> result type
 * @author Jonathan Halterman
 */
@SuppressWarnings("unchecked")
public abstract class FailurePolicyBuilder<S, C extends FailurePolicyConfig<R>, R> {
  protected C config;

  protected FailurePolicyBuilder(C config) {
    this.config = config;
  }

  /**
   * Specifies the failure to handle. Any failure that is assignable from the {@code failure} will be handled.
   *
   * @throws NullPointerException if {@code failure} is null
   */
  public S handle(Class<? extends Throwable> failure) {
    Assert.notNull(failure, "failure");
    return handle(Arrays.asList(failure));
  }

  /**
   * Specifies the failures to handle. Any failures that are assignable from the {@code failures} will be handled.
   *
   * @throws NullPointerException if {@code failures} is null
   * @throws IllegalArgumentException if failures is empty
   */
  @SafeVarargs
  public final S handle(Class<? extends Throwable>... failures) {
    Assert.notNull(failures, "failures");
    Assert.isTrue(failures.length > 0, "Failures cannot be empty");
    return handle(Arrays.asList(failures));
  }

  /**
   * Specifies the failures to handle. Any failures that are assignable from the {@code failures} will be handled.
   *
   * @throws NullPointerException if {@code failures} is null
   * @throws IllegalArgumentException if failures is null or empty
   */
  public S handle(List<Class<? extends Throwable>> failures) {
    Assert.notNull(failures, "failures");
    Assert.isTrue(!failures.isEmpty(), "failures cannot be empty");
    config.failuresChecked = true;
    config.failureConditions.add(failurePredicateFor(failures));
    return (S) this;
  }

  /**
   * Specifies that a failure has occurred if the {@code failurePredicate} matches the failure.
   *
   * @throws NullPointerException if {@code failurePredicate} is null
   */
  public S handleIf(Predicate<? extends Throwable> failurePredicate) {
    Assert.notNull(failurePredicate, "failurePredicate");
    config.failuresChecked = true;
    config.failureConditions.add(failurePredicateFor(failurePredicate));
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
    config.failuresChecked = true;
    config.failureConditions.add((BiPredicate<R, Throwable>) resultPredicate);
    return (S) this;
  }

  /**
   * Specifies that a failure has occurred if the {@code result} matches the execution result. This method is only
   * considered when a result is returned from an execution, not when an exception is thrown.
   */
  public S handleResult(R result) {
    config.failureConditions.add(resultPredicateFor(result));
    return (S) this;
  }

  /**
   * Specifies that a failure has occurred if the {@code resultPredicate} matches the execution result. This method is
   * only considered when a result is returned from an execution, not when an exception is thrown. To handle results or
   * exceptions with the same condition, use {@link #handleIf(BiPredicate)}.
   *
   * @throws NullPointerException if {@code resultPredicate} is null
   */
  public S handleResultIf(Predicate<R> resultPredicate) {
    Assert.notNull(resultPredicate, "resultPredicate");
    config.failureConditions.add(resultPredicateFor(resultPredicate));
    return (S) this;
  }

  /**
   * Returns a predicate that evaluates whether the {@code result} equals an execution result.
   */
  static <R> BiPredicate<R, Throwable> resultPredicateFor(R result) {
    return (t, u) -> result == null ? t == null && u == null : Objects.equals(result, t);
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
   * <p>
   * Short-circuits to false without invoking {@code resultPredicate}, when result is not present (i.e.
   * BiPredicate.test(null, Throwable)).
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
