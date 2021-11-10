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

import net.jodah.failsafe.spi.ExecutionResult;
import net.jodah.failsafe.spi.PolicyExecutor;
import net.jodah.failsafe.spi.SyncExecutionInternal;
import net.jodah.failsafe.spi.Scheduler;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

/**
 * SyncExecution and SyncExecutionInternal implementation.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
final class SyncExecutionImpl<R> extends ExecutionImpl<R> implements SyncExecutionInternal<R> {
  // -- Cross-attempt state --

  // An optional Failsafe executor
  private final FailsafeExecutor<R> executor;
  // The outer-most function that executions begin with
  private Function<SyncExecutionInternal<R>, ExecutionResult<R>> outerFn;
  // Whether the execution is currently interruptable
  private volatile boolean interruptable;
  // Whether the execution has been internally interrupted
  private volatile boolean interrupted;
  // The initial execution instance prior to copies being made
  private final SyncExecutionInternal<R> initial;

  // -- Per-attempt state --

  // The delay time in nanoseconds
  private volatile long delayNanos;

  /**
   * Create a standalone sync execution for the {@code policies}.
   */
  SyncExecutionImpl(List<? extends Policy<R>> policies) {
    super(policies);
    executor = null;
    initial = this;
    preExecute();
  }

  /**
   * Create a sync execution for the {@code executor}.
   */
  SyncExecutionImpl(FailsafeExecutor<R> executor, Scheduler scheduler,
    Function<SyncExecutionInternal<R>, ExecutionResult<R>> innerFn) {
    super(executor.policies);
    this.executor = executor;
    initial = this;

    outerFn = innerFn;
    for (PolicyExecutor<R> policyExecutor : policyExecutors)
      outerFn = policyExecutor.apply(outerFn, scheduler);
  }

  /**
   * Create a sync execution for a new attempt.
   */
  private SyncExecutionImpl(SyncExecutionImpl<R> execution) {
    super(execution);
    executor = execution.executor;
    interruptable = execution.interruptable;
    interrupted = execution.interrupted;
    initial = execution.initial;
  }

  @Override
  public void complete() {
    postExecute(ExecutionResult.none());
  }

  @Override
  public boolean isComplete() {
    return completed;
  }

  @Override
  public Duration getDelay() {
    return Duration.ofNanos(delayNanos);
  }

  @Override
  public void record(R result, Throwable failure) {
    preExecute();
    postExecute(new ExecutionResult<>(result, failure));
  }

  @Override
  public void recordResult(R result) {
    preExecute();
    postExecute(new ExecutionResult<>(result, null));
  }

  @Override
  public void recordFailure(Throwable failure) {
    preExecute();
    postExecute(new ExecutionResult<>(null, failure));
  }

  @Override
  public synchronized void preExecute() {
    if (isStandalone()) {
      attemptRecorded = false;
      cancelledIndex = Integer.MIN_VALUE;
      interrupted = false;
    }
    super.preExecute();
    interruptable = true;
  }

  @Override
  synchronized ExecutionResult<R> postExecute(ExecutionResult<R> result) {
    result = super.postExecute(result);
    delayNanos = result.getDelay();
    return result;
  }

  @Override
  public boolean isInterruptable() {
    return interruptable;
  }

  @Override
  public boolean isInterrupted() {
    return interrupted;
  }

  @Override
  public void setInterruptable(boolean interruptable) {
    this.interruptable = interruptable;
  }

  @Override
  public void setInterrupted(boolean interrupted) {
    this.interrupted = interrupted;
  }

  @Override
  public SyncExecutionInternal<R> getInitial() {
    return initial;
  }

  private boolean isStandalone() {
    return executor == null;
  }

  @Override
  public SyncExecutionImpl<R> copy() {
    return isStandalone() ? this : new SyncExecutionImpl<>(this);
  }

  /**
   * Performs a synchronous execution.
   */
  ExecutionResult<R> executeSync() {
    ExecutionResult<R> result = outerFn.apply(this);
    completed = result.isComplete();
    executor.completionHandler.accept(result, this);
    return result;
  }
}
