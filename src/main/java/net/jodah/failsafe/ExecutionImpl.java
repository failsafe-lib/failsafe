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
import net.jodah.failsafe.spi.ExecutionInternal;
import net.jodah.failsafe.spi.ExecutionResult;
import net.jodah.failsafe.spi.Policy;
import net.jodah.failsafe.spi.PolicyExecutor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Execution and ExecutionInternal implementation.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
class ExecutionImpl<R> implements ExecutionInternal<R> {
  // -- Cross-attempt state --

  final List<PolicyExecutor<R, ? extends Policy<R>>> policyExecutors;
  // When the first execution attempt was started
  private volatile Duration startTime;
  // Number of execution attempts
  private AtomicInteger attempts;
  // Number of completed executions
  private AtomicInteger executions;

  // -- Per-attempt state --

  // The result of the previous execution attempt
  private final ExecutionResult<R> previousResult;
  // The result of the current execution attempt;
  volatile ExecutionResult<R> result;
  // When the most recent execution attempt was started
  volatile Duration attemptStartTime;
  // The index of a PolicyExecutor that cancelled the execution. Integer.MIN_VALUE represents non-cancelled.
  volatile int cancelledIndex = Integer.MIN_VALUE;
  // Whether the execution has pre-executed indicating it has started
  private volatile boolean preExecuted;
  // Whether the execution attempt has been recorded
  volatile boolean attemptRecorded;
  // Whether the execution has been completed for all polices
  volatile boolean completed;

  /**
   * Creates a new execution for the {@code policies}.
   */
  ExecutionImpl(List<? extends Policy<R>> policies) {
    policyExecutors = new ArrayList<>(policies.size());
    startTime = Duration.ZERO;
    attemptStartTime = Duration.ZERO;
    attempts = new AtomicInteger();
    executions = new AtomicInteger();
    previousResult = null;

    // Create policy executors
    ListIterator<? extends Policy<R>> policyIterator = policies.listIterator(policies.size());
    for (int i = 0; policyIterator.hasPrevious(); i++) {
      Policy<R> policy = Assert.notNull(policyIterator.previous(), "policies");
      PolicyExecutor<R, ? extends Policy<R>> policyExecutor = policy.toExecutor(i);
      policyExecutors.add(policyExecutor);
    }
  }

  /**
   * Create an execution for a new attempt.
   */
  ExecutionImpl(ExecutionImpl<R> execution) {
    policyExecutors = execution.policyExecutors;
    startTime = execution.startTime;
    attempts = execution.attempts;
    executions = execution.executions;
    previousResult = execution.result;
  }

  /** Used for testing purposes only */
  ExecutionImpl(ExecutionResult<R> previousResult) {
    policyExecutors = null;
    this.previousResult = previousResult;
  }

  @Override
  public ExecutionResult<R> getResult() {
    return result;
  }

  @Override
  public synchronized void preExecute() {
    if (!preExecuted) {
      attemptStartTime = Duration.ofNanos(System.nanoTime());
      if (startTime == Duration.ZERO)
        startTime = attemptStartTime;
      preExecuted = true;
    }
  }

  @Override
  public boolean isPreExecuted() {
    return preExecuted;
  }

  @Override
  public synchronized void recordAttempt() {
    if (!attemptRecorded) {
      attempts.incrementAndGet();
      attemptRecorded = true;
    }
  }

  @Override
  public synchronized void record(ExecutionResult<R> result) {
    if (preExecuted && !attemptRecorded) {
      recordAttempt();
      executions.incrementAndGet();
      this.result = result;
    }
  }

  /**
   * Externally called. Records an execution and performs post-execution handling for the {@code result} against all
   * configured policy executors. Returns whether the result is complete for all policies.
   *
   * @throws IllegalStateException if the execution is already complete
   */
  synchronized ExecutionResult<R> postExecute(ExecutionResult<R> result) {
    Assert.state(!completed, "Execution has already been completed");
    record(result);
    boolean allComplete = true;
    for (PolicyExecutor<R, ? extends Policy<R>> policyExecutor : policyExecutors) {
      result = policyExecutor.postExecute(this, result);
      allComplete = allComplete && result.isComplete();
    }

    completed = allComplete;
    return result;
  }

  @Override
  public void cancel() {
    cancelledIndex = Integer.MAX_VALUE;
  }

  @Override
  public void cancel(PolicyExecutor<R, ?> policyExecutor) {
    cancelledIndex = policyExecutor.getPolicyIndex();
  }

  @Override
  public boolean isCancelled() {
    return cancelledIndex > Integer.MIN_VALUE;
  }

  @Override
  public boolean isCancelled(PolicyExecutor<R, ?> policyExecutor) {
    return cancelledIndex > policyExecutor.getPolicyIndex();
  }

  @Override
  public Duration getElapsedTime() {
    return Duration.ofNanos(System.nanoTime() - startTime.toNanos());
  }

  @Override
  public Duration getElapsedAttemptTime() {
    return Duration.ofNanos(System.nanoTime() - attemptStartTime.toNanos());
  }

  @Override
  public int getAttemptCount() {
    return attempts.get();
  }

  @Override
  public int getExecutionCount() {
    return executions.get();
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T extends Throwable> T getLastFailure() {
    ExecutionResult<R> r = result != null ? result : previousResult;
    return r == null ? null : (T) r.getFailure();
  }

  @Override
  public R getLastResult() {
    ExecutionResult<R> r = result != null ? result : previousResult;
    return r == null ? null : r.getResult();
  }

  @Override
  public R getLastResult(R defaultValue) {
    ExecutionResult<R> r = result != null ? result : previousResult;
    return r == null ? defaultValue : r.getResult();
  }

  @Override
  public Duration getStartTime() {
    return startTime;
  }

  @Override
  public boolean isFirstAttempt() {
    return attempts.get() == 0;
  }

  @Override
  public boolean isRetry() {
    return attempts.get() > 0;
  }

  @Override
  public String toString() {
    return "[" + "attempts=" + attempts + ", executions=" + executions + ", lastResult=" + getLastResult()
      + ", lastFailure=" + getLastFailure() + ']';
  }
}
