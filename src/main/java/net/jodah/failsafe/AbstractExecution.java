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
 * @param <R> result type
 * @author Jonathan Halterman
 */
public abstract class AbstractExecution<R> extends ExecutionContext<R> {
  // Cross-attempt state --
  final Scheduler scheduler;
  final List<PolicyExecutor<R, ? extends Policy<R>>> policyExecutors;

  // Per-attempt state --
  // Whether the execution has started
  volatile boolean attemptStarted;
  // Whether the execution attempt has been recorded
  volatile boolean attemptRecorded;
  // The wait time in nanoseconds
  volatile long waitNanos;
  // Whether the entire Failsafe execution has been completed
  volatile boolean completed;

  /**
   * Creates a new AbstractExecution for the {@code policies}.
   */
  AbstractExecution(List<? extends Policy<R>> policies, Scheduler scheduler) {
    this.scheduler = scheduler;
    policyExecutors = new ArrayList<>(policies.size());
    ListIterator<? extends Policy<R>> policyIterator = policies.listIterator(policies.size());
    for (int i = 0; policyIterator.hasPrevious(); i++) {
      Policy<R> policy = Assert.notNull(policyIterator.previous(), "policies");
      PolicyExecutor<R, ? extends Policy<R>> policyExecutor = policy.toExecutor(i);
      policyExecutors.add(policyExecutor);
    }
  }

  AbstractExecution(AbstractExecution<R> execution) {
    super(execution);
    scheduler = execution.scheduler;
    policyExecutors = execution.policyExecutors;
  }

  /**
   * Called when execution of the user's supplier is about to begin.
   */
  synchronized void preExecute() {
    if (!attemptRecorded) {
      attemptStartTime = Duration.ofNanos(System.nanoTime());
      if (startTime == Duration.ZERO)
        startTime = attemptStartTime;
      attemptStarted = true;
    }
  }

  /**
   * Records an execution attempt which may correspond with an execution result. Async executions will have results
   * recorded separately.
   */
  synchronized void recordAttempt() {
    if (!attemptRecorded) {
      attempts.incrementAndGet();
      attemptRecorded = true;
    }
  }

  /**
   * Records an execution attempt so long as the execution has not already been completed or interrupted. In the case of
   * interruption, an execution may have already been recorded, but the result will be re-recorded by the interrupting
   * thread.
   *
   * @throws IllegalStateException if the execution is already complete
   */
  synchronized void record(ExecutionResult result) {
    Assert.state(!completed, "Execution has already been completed");

    if (attemptStarted && !attemptRecorded) {
      recordAttempt();
      executions.incrementAndGet();
      this.result = result;
    }
  }

  /**
   * Returns whether the {@code policyExecutor} has been cancelled for the execution.
   */
  boolean isCancelled(PolicyExecutor<?, ?> policyExecutor) {
    return cancelledIndex > policyExecutor.policyIndex;
  }

  /**
   * Externally called. Records an execution and performs post-execution handling for the {@code result} against all
   * configured policy executors. Returns whether the result is complete for all policies.
   *
   * @throws IllegalStateException if the execution is already complete
   */
  synchronized ExecutionResult postExecute(ExecutionResult result) {
    record(result);
    boolean allComplete = true;
    for (PolicyExecutor<R, ? extends Policy<R>> policyExecutor : policyExecutors) {
      result = policyExecutor.postExecute(this, result);
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
