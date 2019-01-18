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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

@SuppressWarnings("WeakerAccess")
public abstract class AbstractExecution extends ExecutionContext {
  final FailsafeExecutor<Object> executor;
  final List<PolicyExecutor<Policy<Object>>> policyExecutors;

  // Internally mutable state
  volatile Object lastResult;
  volatile Throwable lastFailure;

  /** The wait time in nanoseconds. */
  private volatile long waitNanos;
  volatile boolean completed;

  /**
   * Creates a new AbstractExecution for the {@code executor}.
   */
  AbstractExecution(FailsafeExecutor<Object> executor) {
    super(Duration.ofNanos(System.nanoTime()));
    this.executor = executor;

    if (executor.policies == null || executor.policies.isEmpty()) {
      // Add policies in logical order
      policyExecutors = new ArrayList<>(5);
      if (executor.circuitBreaker != null)
        buildPolicyExecutor(executor.circuitBreaker);
      if (executor.retryPolicy != RetryPolicy.NEVER)
        buildPolicyExecutor(executor.retryPolicy);
      if (executor.fallback != null)
        buildPolicyExecutor(executor.fallback);
    } else {
      // Add policies in user-defined order
      policyExecutors = new ArrayList<>(executor.policies.size());
      ListIterator<Policy<Object>> policyIterator = executor.policies.listIterator(executor.policies.size());
      while (policyIterator.hasPrevious())
        buildPolicyExecutor(policyIterator.previous());
    }
  }

  @SuppressWarnings("unchecked")
  private void buildPolicyExecutor(Policy policy) {
    PolicyExecutor<Policy<Object>> policyExecutor = policy.toExecutor();
    policyExecutor.execution = this;
    policyExecutors.add(policyExecutor);
  }

  /**
   * Records an execution attempt.
   *
   * @throws IllegalStateException if the execution is already complete
   */
  void record(ExecutionResult result) {
    Assert.state(!completed, "Execution has already been completed");
    attempts++;
    lastResult = result.getResult();
    lastFailure = result.getFailure();
  }

  void preExecute() {
  }

  /**
   * Performs post-execution handling of the {@code result}, returning true if complete else false.
   *
   * @throws IllegalStateException if the execution is already complete
   */
  synchronized boolean postExecute(ExecutionResult result) {
    record(result);
    for (PolicyExecutor<Policy<Object>> policyExecutor : policyExecutors)
      result = policyExecutor.postExecute(result);

    waitNanos = result.getWaitNanos();
    completed = result.isComplete();
    return completed;
  }

  /**
   * Returns the last failure that was recorded.
   */
  @SuppressWarnings("unchecked")
  public <T extends Throwable> T getLastFailure() {
    return (T) lastFailure;
  }

  /**
   * Returns the last result that was recorded.
   */
  @SuppressWarnings("unchecked")
  public <T> T getLastResult() {
    return (T) lastResult;
  }

  /**
   * Returns the time to wait before the next execution attempt. Returns {@code 0} if an execution has not yet
   * occurred.
   */
  public Duration getWaitTime() {
    return Duration.ofNanos(waitNanos);
  }

  /**
   * Returns whether the execution is complete or if it can be retried.
   */
  public boolean isComplete() {
    return completed;
  }
}
