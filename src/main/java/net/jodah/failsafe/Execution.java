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
import net.jodah.failsafe.internal.util.DelegatingScheduler;
import net.jodah.failsafe.internal.util.Lists;

import java.util.function.Function;

/**
 * Tracks synchronous executions and handles failures according to one or more {@link Policy policies}. Execution
 * results must be explicitly recorded via one of the {@code record} methods.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
@SuppressWarnings("WeakerAccess")
public class Execution<R> extends AbstractExecution<R> {
  // Cross-attempt state --
  private final FailsafeExecutor<R> executor;
  final InterruptState interruptState;

  static class InterruptState {
    // Whether the execution can be interrupted
    volatile boolean canInterrupt;
    // Whether the execution has been internally interrupted
    volatile boolean interrupted;
  }

  /**
   * Creates a new {@link Execution} that will use the {@code outerPolicy} and {@code innerPolicies} to handle failures.
   * Policies are applied in reverse order, with the last policy being applied first.
   *
   * @throws NullPointerException if {@code outerPolicy} is null
   */
  @SafeVarargs
  public Execution(Policy<R> outerPolicy, Policy<R>... policies) {
    super(Lists.of(Assert.notNull(outerPolicy, "outerPolicy"), policies), DelegatingScheduler.INSTANCE);
    executor = null;
    interruptState = new InterruptState();
    preExecute();
  }

  Execution(FailsafeExecutor<R> executor) {
    super(executor.policies, DelegatingScheduler.INSTANCE);
    this.executor = executor;
    interruptState = new InterruptState();
  }

  private Execution(Execution<R> execution) {
    super(execution);
    executor = execution.executor;
    interruptState = execution.interruptState;
  }

  @Override
  synchronized void preExecute() {
    if (isStandalone()) {
      attemptRecorded = false;
      cancelledIndex = Integer.MIN_VALUE;
      interruptState.interrupted = false;
    }
    super.preExecute();
    interruptState.canInterrupt = true;
  }

  /**
   * Records and completes the execution successfully.
   *
   * @throws IllegalStateException if the execution is already complete
   */
  public void complete() {
    postExecute(ExecutionResult.NONE);
  }

  /**
   * Records an execution {@code result} or {@code failure} which triggers failure handling, if needed, by the
   * configured policies. If policy handling is not possible or completed, the execution is completed.
   *
   * @throws IllegalStateException if the execution is already complete
   */
  public void record(R result, Throwable failure) {
    preExecute();
    postExecute(new ExecutionResult(result, failure));
  }

  /**
   * Records an execution {@code result} which triggers failure handling, if needed, by the configured policies. If
   * policy handling is not possible or completed, the execution is completed.
   *
   * @throws IllegalStateException if the execution is already complete
   */
  public void recordResult(R result) {
    preExecute();
    postExecute(new ExecutionResult(result, null));
  }

  /**
   * Records an execution {@code failure} which triggers failure handling, if needed, by the configured policies. If
   * policy handling is not possible or completed, the execution is completed.
   *
   * @throws IllegalStateException if the execution is already complete
   */
  public void recordFailure(Throwable failure) {
    preExecute();
    postExecute(new ExecutionResult(null, failure));
  }

  /**
   * Returns a new copy of the execution if it is not standalone, else returns {@code this} since standalone executions
   * are referenced externally and cannot be replaced.
   */
  Execution<R> copy() {
    return isStandalone() ? this : new Execution<>(this);
  }

  private boolean isStandalone() {
    return executor == null;
  }

  /**
   * Performs a synchronous execution.
   */
  ExecutionResult executeSync(Function<Execution<R>, ExecutionResult> innerFn) {
    for (PolicyExecutor<R, ? extends Policy<R>> policyExecutor : policyExecutors)
      innerFn = policyExecutor.apply(innerFn, scheduler);

    ExecutionResult result = innerFn.apply(this);
    completed = result.isComplete();
    if (executor != null)
      executor.handleComplete(result, this);
    return result;
  }
}
