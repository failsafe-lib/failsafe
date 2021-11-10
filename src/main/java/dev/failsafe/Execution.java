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

import dev.failsafe.internal.util.Assert;
import dev.failsafe.internal.util.Lists;

import java.time.Duration;

/**
 * Tracks synchronous executions and handles failures according to one or more {@link Policy policies}. Execution
 * results must be explicitly recorded via one of the {@code record} methods.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public interface Execution<R> extends ExecutionContext<R> {
  /**
   * Creates a new {@link SyncExecutionImpl} that will use the {@code outerPolicy} and {@code innerPolicies} to handle
   * failures. Policies are applied in reverse order, with the last policy being applied first.
   *
   * @throws NullPointerException if {@code outerPolicy} is null
   */
  @SafeVarargs
  static <R> Execution<R> of(Policy<R> outerPolicy, Policy<R>... policies) {
    return new SyncExecutionImpl<>(Lists.of(Assert.notNull(outerPolicy, "outerPolicy"), policies));
  }

  /**
   * Records and completes the execution successfully.
   *
   * @throws IllegalStateException if the execution is already complete
   */
  void complete();

  /**
   * Returns whether the execution is complete or if it can be retried. An execution is considered complete only when
   * all configured policies consider the execution complete.
   */
  boolean isComplete();

  /**
   * Returns the time to delay before the next execution attempt. Returns {@code 0} if an execution has not yet
   * occurred.
   */
  Duration getDelay();

  /**
   * Records an execution {@code result} or {@code failure} which triggers failure handling, if needed, by the
   * configured policies. If policy handling is not possible or completed, the execution is completed.
   *
   * @throws IllegalStateException if the execution is already complete
   */
  void record(R result, Throwable failure);

  /**
   * Records an execution {@code result} which triggers failure handling, if needed, by the configured policies. If
   * policy handling is not possible or completed, the execution is completed.
   *
   * @throws IllegalStateException if the execution is already complete
   */
  void recordResult(R result);

  /**
   * Records an execution {@code failure} which triggers failure handling, if needed, by the configured policies. If
   * policy handling is not possible or completed, the execution is completed.
   *
   * @throws IllegalStateException if the execution is already complete
   */
  void recordFailure(Throwable failure);
}
