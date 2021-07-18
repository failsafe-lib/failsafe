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
import net.jodah.failsafe.util.concurrent.Scheduler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

/**
 * Common execution information.
 *
 * @author Jonathan Halterman
 */
@SuppressWarnings("WeakerAccess")
public abstract class AbstractExecution extends ExecutionContext {
  final Scheduler scheduler;
  final FailsafeExecutor<Object> executor;
  final List<PolicyExecutor<Policy<Object>>> policyExecutors;

  // Internally mutable state
  /* Whether a result has been post-executed */
  volatile boolean resultHandled;
  /* Whether the execution can be interrupted */
  volatile boolean canInterrupt;
  /* Whether the execution has been interrupted */
  volatile boolean interrupted;
  /* The wait time in nanoseconds. */
  volatile long waitNanos;
  /* Whether the execution has been completed */
  volatile boolean completed;

  /**
   * Creates a new AbstractExecution for the {@code executor}.
   */
  AbstractExecution(Scheduler scheduler, FailsafeExecutor<Object> executor) {
    this.scheduler = scheduler;
    this.executor = executor;
    policyExecutors = new ArrayList<>(executor.policies.size());
    ListIterator<Policy<Object>> policyIterator = executor.policies.listIterator(executor.policies.size());
    for (int i = 1; policyIterator.hasPrevious(); i++) {
      PolicyExecutor<Policy<Object>> policyExecutor = policyIterator.previous().toExecutor(this);
      policyExecutor.policyIndex = i;
      policyExecutors.add(policyExecutor);
    }
  }

  /**
   * Records an execution attempt so long as the execution has not already been completed or interrupted. In the case of
   * interruption, an execution will be recorded by the interrupting thread.
   *
   * @throws IllegalStateException if the execution is already complete
   */
  void record(ExecutionResult result) {
    Assert.state(!completed, "Execution has already been completed");
    if (!interrupted) {
      attempts.incrementAndGet();
      lastResult = result.getResult();
      lastFailure = result.getFailure();
    }
  }

  void preExecute() {
    attemptStartTime = Duration.ofNanos(System.nanoTime());
    if (startTime == Duration.ZERO)
      startTime = attemptStartTime;
    resultHandled = false;
    cancelledIndex = 0;
    canInterrupt = true;
    interrupted = false;
  }

  boolean isAsyncExecution() {
    return false;
  }

  /**
   * Performs post-execution handling of the {@code result}, completes the execution if all policies are complete for
   * the {@code result}, and returns the result from the policies.
   *
   * @throws IllegalStateException if the execution is already complete
   */
  synchronized ExecutionResult postExecute(ExecutionResult result) {
    record(result);
    boolean allComplete = true;
    for (PolicyExecutor<Policy<Object>> policyExecutor : policyExecutors) {
      result = policyExecutor.postExecute(result);
      allComplete = allComplete && result.isComplete();
    }

    waitNanos = result.getWaitNanos();
    completed = allComplete;
    return result;
  }

  /**
   * Returns the time to wait before the next execution attempt. Returns {@code 0} if an execution has not yet
   * occurred.
   */
  public Duration getWaitTime() {
    return Duration.ofNanos(waitNanos);
  }

  /**
   * Returns whether the execution is complete or if it can be retried. An execution is considered complete only when
   * all configured policies consider the execution complete.
   */
  public boolean isComplete() {
    return completed;
  }
}
