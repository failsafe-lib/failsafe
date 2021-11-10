/*
 * Copyright 2016 the original author or authors.
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

import dev.failsafe.internal.util.Assert;
import dev.failsafe.internal.util.Lists;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Simple, sophisticated failure handling.
 *
 * @author Jonathan Halterman
 */
public class Failsafe {
  /**
   * Creates and returns a new {@link FailsafeExecutor} instance that will handle failures according to the given
   * policies. The policies are composed around an execution and will handle execution results in reverse, with the last
   * policy being applied first. For example, consider:
   * <p>
   * <pre>
   *   Failsafe.with(fallback, retryPolicy, circuitBreaker).get(supplier);
   * </pre>
   * </p>
   * <p>
   * This is equivalent to composition using the the {@link FailsafeExecutor#compose(Policy) compose} method:
   * <pre>
   *   Failsafe.with(fallback).compose(retryPolicy).compose(circuitBreaker).get(supplier);
   * </pre>
   * </p>
   * These result in the following internal composition when executing a {@code runnable} or {@code supplier} and
   * handling its result:
   * <p>
   * <pre>
   *   Fallback(RetryPolicy(CircuitBreaker(Supplier)))
   * </pre>
   * </p>
   * This means the {@code CircuitBreaker} is first to evaluate the {@code Supplier}'s result, then the {@code
   * RetryPolicy}, then the {@code Fallback}. Each policy makes its own determination as to whether the result
   * represents a failure. This allows different policies to be used for handling different types of failures.
   *
   * @param <R> result type
   * @param <P> policy type
   * @throws NullPointerException if {@code outerPolicy} is null
   */
  @SafeVarargs
  public static <R, P extends Policy<R>> FailsafeExecutor<R> with(P outerPolicy, P... policies) {
    Assert.notNull(outerPolicy, "outerPolicy");
    return new FailsafeExecutor<>(Lists.of(outerPolicy, policies));
  }

  /**
   * Creates and returns a new {@link FailsafeExecutor} instance that will handle failures according to the given {@code
   * policies}. The {@code policies} are composed around an execution and will handle execution results in reverse, with
   * the last policy being applied first. For example, consider:
   * <p>
   * <pre>
   *   Failsafe.with(fallback, retryPolicy, circuitBreaker).get(supplier);
   * </pre>
   * </p>
   * This results in the following internal composition when executing the {@code supplier} and handling its result:
   * <p>
   * <pre>
   *   Fallback(RetryPolicy(CircuitBreaker(Supplier)))
   * </pre>
   * </p>
   * This means the {@code CircuitBreaker} is first to evaluate the {@code Supplier}'s result, then the {@code
   * RetryPolicy}, then the {@code Fallback}. Each policy makes its own determination as to whether the result
   * represents a failure. This allows different policies to be used for handling different types of failures.
   *
   * @param <R> result type
   * @param <P> policy type
   * @throws NullPointerException if {@code policies} is null
   * @throws IllegalArgumentException if {@code policies} is empty
   * @deprecated This will be removed in 3.0. Use {@link #with(Policy, Policy[])} instead
   */
  @Deprecated
  public static <R, P extends Policy<R>> FailsafeExecutor<R> with(P[] policies) {
    Assert.notNull(policies, "policies");
    Assert.isTrue(policies.length > 0, "At least one policy must be supplied");
    return new FailsafeExecutor<>(Arrays.asList(policies));
  }

  /**
   * Creates and returns a new {@link FailsafeExecutor} instance that will handle failures according to the given {@code
   * policies}. The {@code policies} are composed around an execution and will handle execution results in reverse, with
   * the last policy being applied first. For example, consider:
   * <p>
   * <pre>
   *   Failsafe.with(Arrays.asList(fallback, retryPolicy, circuitBreaker)).get(supplier);
   * </pre>
   * </p>
   * This results in the following internal composition when executing a {@code runnable} or {@code supplier} and
   * handling its result:
   * <p>
   * <pre>
   *   Fallback(RetryPolicy(CircuitBreaker(Supplier)))
   * </pre>
   * </p>
   * This means the {@code CircuitBreaker} is first to evaluate the {@code Supplier}'s result, then the {@code
   * RetryPolicy}, then the {@code Fallback}. Each policy makes its own determination as to whether the result
   * represents a failure. This allows different policies to be used for handling different types of failures.
   *
   * @param <R> result type
   * @throws NullPointerException if {@code policies} is null
   * @throws IllegalArgumentException if {@code policies} is empty
   */
  public static <R> FailsafeExecutor<R> with(List<? extends Policy<R>> policies) {
    Assert.notNull(policies, "policies");
    Assert.isTrue(!policies.isEmpty(), "At least one policy must be supplied");
    return new FailsafeExecutor<>(policies);
  }

  /**
   * Creates and returns a noop {@link FailsafeExecutor} instance that treats any exception as a failure for the
   * purposes of calling event listeners, and provides no additional failure handling.
   *
   * @param <R> result type
   * @throws NullPointerException if {@code policies} is null
   * @throws IllegalArgumentException if {@code policies} is empty
   */
  public static <R> FailsafeExecutor<R> none() {
    return new FailsafeExecutor<>(Collections.emptyList());
  }
}
