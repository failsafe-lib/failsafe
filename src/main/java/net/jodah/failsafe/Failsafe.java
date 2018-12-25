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
package net.jodah.failsafe;

import net.jodah.failsafe.internal.util.Assert;

import java.util.Arrays;

/**
 * Simple, sophisticated failure handling.
 *
 * @author Jonathan Halterman
 */
public class Failsafe {
  /**
   * Creates and returns a new {@link SyncFailsafe} instance that will perform executions and retries synchronously
   * according to the {@code retryPolicy}.
   *
   * @param <T> result type
   * @throws NullPointerException if {@code retryPolicy} is null
   */
  public static <T> SyncFailsafe<T> with(RetryPolicy retryPolicy) {
    return new SyncFailsafe<>(Assert.notNull(retryPolicy, "retryPolicy"));
  }

  /**
   * Creates and returns a new {@link SyncFailsafe} instance that will perform executions and retries synchronously
   * according to the {@code circuitBreaker}.
   *
   * @param <T> result type
   * @throws NullPointerException if {@code circuitBreaker} is null
   */
  public static <T> SyncFailsafe<T> with(CircuitBreaker circuitBreaker) {
    return new SyncFailsafe<>(Assert.notNull(circuitBreaker, "circuitBreaker"));
  }

  /**
   * Creates and returns a new {@link SyncFailsafe} instance that will perform executions and retries synchronously
   * according to the {@code policies}. Policies are applied in reverse order, with the last policy being applied first.
   *
   * @param <T> result type
   * @throws NullPointerException if {@code policies} is null
   * @throws IllegalArgumentException if {@code policies} is empty
   */
  public static <T> SyncFailsafe<T> with(FailsafePolicy... policies) {
    Assert.notNull(policies, "policies");
    Assert.isTrue(policies.length > 0, "At least one policy must be supplied");
    return new SyncFailsafe<>(Arrays.asList(policies));
  }
}
