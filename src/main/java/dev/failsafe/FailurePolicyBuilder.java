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
package dev.failsafe;

import dev.failsafe.function.CheckedBiPredicate;
import dev.failsafe.function.CheckedPredicate;
import dev.failsafe.internal.util.Assert;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A Policy that allows configurable conditions to determine whether an execution is a failure.
 * <ul>
 *   <li>By default, any exception is considered a failure and will be handled by the policy. You can override this by
 *   specifying your own {@code handle} conditions. The default exception handling condition will only be overridden by
 *   another condition that handles failure exceptions such as {@link #handle(Class)} or {@link #handleIf(CheckedBiPredicate)}.
 *   Specifying a condition that only handles results, such as {@link #handleResult(Object)} or
 *   {@link #handleResultIf(CheckedPredicate)} will not replace the default exception handling condition.</li>
 *   <li>If multiple {@code handle} conditions are specified, any condition that matches an execution result or exception
 *   will trigger policy handling.</li>
 * </ul>
 *
 * @param <S> self type
 * @param <C> config type
 * @param <R> result type
 * @author Jonathan Halterman
 */
@SuppressWarnings("unchecked")
public abstract class FailurePolicyBuilder<S, C extends FailurePolicyConfig<R>, R> extends PolicyBuilder<S, C, R> {
  protected FailurePolicyBuilder(C config) {
    super(config);
  }

  /**
   * Specifies the exception to handle as a failure. Any exception that is assignable from the {@code exception} will be
   * handled.
   *
   * @throws NullPointerException if {@code exception} is null
   */
  public S handle(Class<? extends Throwable> exception) {
    Assert.notNull(exception, "exception");
    return handle(Arrays.asList(exception));
  }

  /**
   * Specifies the exceptions to handle as failures. Any exceptions that are assignable from the {@code exceptions} will
   * be handled.
   *
   * @throws NullPointerException if {@code exceptions} is null
   * @throws IllegalArgumentException if exceptions is empty
   */
  @SafeVarargs
  public final S handle(Class<? extends Throwable>... exceptions) {
    Assert.notNull(exceptions, "exceptions");
    Assert.isTrue(exceptions.length > 0, "exceptions cannot be empty");
    return handle(Arrays.asList(exceptions));
  }

  /**
   * Specifies the exceptions to handle as failures. Any exceptions that are assignable from the {@code exceptions} will
   * be handled.
   *
   * @throws NullPointerException if {@code exceptions} is null
   * @throws IllegalArgumentException if exceptions is null or empty
   */
  public S handle(List<Class<? extends Throwable>> exceptions) {
    Assert.notNull(exceptions, "exceptions");
    Assert.isTrue(!exceptions.isEmpty(), "exceptions cannot be empty");
    config.exceptionsChecked = true;
    config.failureConditions.add(failurePredicateFor(exceptions));
    return (S) this;
  }

  /**
   * Specifies that a failure has occurred if the {@code failurePredicate} matches the exception. Any exception thrown
   * from the {@code failurePredicate} is treated as a {@code false} result.
   *
   * @throws NullPointerException if {@code failurePredicate} is null
   */
  public S handleIf(CheckedPredicate<? extends Throwable> failurePredicate) {
    Assert.notNull(failurePredicate, "failurePredicate");
    config.exceptionsChecked = true;
    config.failureConditions.add(failurePredicateFor(failurePredicate));
    return (S) this;
  }

  /**
   * Specifies that a failure has occurred if the {@code resultPredicate} matches the execution result. Any exception
   * thrown from the {@code resultPredicate} is treated as a {@code false} result.
   *
   * @throws NullPointerException if {@code resultPredicate} is null
   */
  @SuppressWarnings("unchecked")
  public S handleIf(CheckedBiPredicate<R, ? extends Throwable> resultPredicate) {
    Assert.notNull(resultPredicate, "resultPredicate");
    config.exceptionsChecked = true;
    config.failureConditions.add((CheckedBiPredicate<R, Throwable>) resultPredicate);
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
   * exceptions with the same condition, use {@link #handleIf(CheckedBiPredicate)}. Any exception thrown from the {@code
   * resultPredicate} is treated as a {@code false} result.
   *
   * @throws NullPointerException if {@code resultPredicate} is null
   */
  public S handleResultIf(CheckedPredicate<R> resultPredicate) {
    Assert.notNull(resultPredicate, "resultPredicate");
    config.failureConditions.add(resultPredicateFor(resultPredicate));
    return (S) this;
  }

  /**
   * Returns a predicate that evaluates whether the {@code result} equals an execution result.
   */
  static <R> CheckedBiPredicate<R, Throwable> resultPredicateFor(R result) {
    return (t, u) -> result == null ? t == null && u == null : Objects.equals(result, t);
  }

  /**
   * Returns a predicate that evaluates the {@code failurePredicate} against a failure.
   */
  @SuppressWarnings("unchecked")
  static <R> CheckedBiPredicate<R, Throwable> failurePredicateFor(
    CheckedPredicate<? extends Throwable> failurePredicate) {
    return (t, u) -> u != null && ((CheckedPredicate<Throwable>) failurePredicate).test(u);
  }

  /**
   * Returns a predicate that evaluates the {@code resultPredicate} against a result, when present.
   * <p>
   * Short-circuits to false without invoking {@code resultPredicate}, when result is not present (i.e.
   * BiPredicate.test(null, Throwable)).
   */
  static <R> CheckedBiPredicate<R, Throwable> resultPredicateFor(CheckedPredicate<R> resultPredicate) {
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
  static <R> CheckedBiPredicate<R, Throwable> failurePredicateFor(List<Class<? extends Throwable>> failures) {
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
