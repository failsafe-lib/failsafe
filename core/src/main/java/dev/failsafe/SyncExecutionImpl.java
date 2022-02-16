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

import dev.failsafe.spi.ExecutionResult;
import dev.failsafe.spi.PolicyExecutor;
import dev.failsafe.spi.Scheduler;
import dev.failsafe.spi.SyncExecutionInternal;

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
  // An optional Failsafe call
  private final CallImpl<R> call;
  // The outer-most function that executions begin with
  private Function<SyncExecutionInternal<R>, ExecutionResult<R>> outerFn;
  // The interruptable execution thread
  private final Thread executionThread;
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
    call = null;
    initial = this;
    executionThread = Thread.currentThread();
    preExecute();
  }

  /**
   * Create a sync execution for the {@code executor}.
   */
  SyncExecutionImpl(FailsafeExecutor<R> executor, Scheduler scheduler, CallImpl<R> call,
    Function<SyncExecutionInternal<R>, ExecutionResult<R>> innerFn) {
    super(executor.policies);
    this.executor = executor;
    this.call = call;
    initial = this;
    executionThread = Thread.currentThread();
    if (call != null)
      call.setExecution(this);

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
    call = execution.call;
    interruptable = execution.interruptable;
    interrupted = execution.interrupted;
    initial = execution.initial;
    executionThread = execution.executionThread;
    if (call != null)
      call.setExecution(this);
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
  public void record(R result, Throwable exception) {
    preExecute();
    postExecute(new ExecutionResult<>(result, exception));
  }

  @Override
  public void recordResult(R result) {
    preExecute();
    postExecute(new ExecutionResult<>(result, null));
  }

  @Override
  public void recordException(Throwable exception) {
    preExecute();
    postExecute(new ExecutionResult<>(null, exception));
  }

  @Override
  @Deprecated
  public void recordFailure(Throwable failure) {
    recordException(failure);
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
  public boolean isInterrupted() {
    return interrupted;
  }

  @Override
  public void setInterruptable(boolean interruptable) {
    this.interruptable = interruptable;
  }

  @Override
  public void interrupt() {
    // Guard against race with the execution completing
    synchronized (initial) {
      if (interruptable) {
        interrupted = true;
        executionThread.interrupt();
      }
    }
  }

  /**
   * Returns the initial execution for a series of execution attempts, prior to {@link #copy() copies} being made.
   * Useful for locking to perform atomic operations on the same execution reference.
   */
  SyncExecutionInternal<R> getInitial() {
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
  R executeSync() {
    ExecutionResult<R> result = outerFn.apply(this);
    completed = result.isComplete();
    executor.completionHandler.accept(result, this);
    Throwable exception = result.getException();
    if (exception != null) {
      if (exception instanceof RuntimeException)
        throw (RuntimeException) exception;
      if (exception instanceof Error)
        throw (Error) exception;
      throw new FailsafeException(exception);
    }
    return result.getResult();
  }
}
