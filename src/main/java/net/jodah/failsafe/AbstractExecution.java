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

import net.jodah.failsafe.event.EventHandler;
import net.jodah.failsafe.internal.util.Assert;

import java.time.Duration;
import java.util.ListIterator;
import java.util.concurrent.Callable;

@SuppressWarnings("WeakerAccess")
public abstract class AbstractExecution extends ExecutionContext {
  final EventHandler eventHandler;
  Callable<Object> callable;

  // Internally mutable state
  volatile Object lastResult;
  volatile Throwable lastFailure;
  PolicyExecutor head;
  volatile PolicyExecutor lastExecuted;
  /** The wait time in nanoseconds. */
  private volatile long waitNanos;
  volatile boolean completed;

  /**
   * Creates a new AbstractExecution for the {@code callable} and {@code config}.
   */
  AbstractExecution(FailsafeConfig<Object, ?> config) {
    super(Duration.ofNanos(System.nanoTime()));
    eventHandler = config.listeners;

    PolicyExecutor next = null;
    if (config.policies == null || config.policies.isEmpty()) {
      // Add policies in logical order
      if (config.circuitBreaker != null)
        next = buildPolicyExecutor(config.circuitBreaker, next);
      if (config.retryPolicy != RetryPolicy.NEVER)
        next = buildPolicyExecutor(config.retryPolicy, next);
      if (config.fallback != null)
        next = buildPolicyExecutor(config.fallback, next);
    } else {
      // Add policies in user-defined order
      ListIterator<FailsafePolicy> policyIterator = config.policies.listIterator(config.policies.size());
      while (policyIterator.hasPrevious())
        next = buildPolicyExecutor(policyIterator.previous(), next);
    }

    head = next;
  }

  @SuppressWarnings("unchecked")
  void inject(Callable<?> callable) {
    this.callable = (Callable<Object>) callable;
  }

  private PolicyExecutor buildPolicyExecutor(FailsafePolicy policy, PolicyExecutor next) {
    PolicyExecutor policyExecutor = policy.toExecutor();
    policyExecutor.execution = this;
    policyExecutor.eventHandler = eventHandler;
    policyExecutor.next = next;
    return policyExecutor;
  }

  /**
   * Records an execution attempt.
   *
   * @throws IllegalStateException if the execution is already complete
   */
  void record(ExecutionResult result) {
    Assert.state(!completed, "Execution has already been completed");
    executions++;
    lastResult = result.result;
    lastFailure = result.failure;
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
    result = postExecute(result, head);
    waitNanos = result.waitNanos;
    completed = result.completed;
    return completed;
  }

  private ExecutionResult postExecute(ExecutionResult result, PolicyExecutor policyExecutor) {
    // Traverse to the last executor
    if (policyExecutor.next != null)
      postExecute(result, policyExecutor.next);

    return policyExecutor.postExecute(result);
  }

  /**
   * Performs a synchronous execution.
   */
  ExecutionResult executeSync() {
    ExecutionResult result = head.executeSync(null);
    completed = result.completed;
    eventHandler.handleComplete(result, this);
    return result;
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
