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

import java.util.Arrays;
import java.util.function.Supplier;

import net.jodah.failsafe.internal.util.Assert;
import net.jodah.failsafe.internal.util.DelegatingScheduler;

/**
 * Tracks executions and determines when an execution can be performed for a {@link RetryPolicy}.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
@SuppressWarnings("WeakerAccess")
public class Execution<R> extends AbstractExecution<R> {
  /**
   * Creates a new {@link Execution} that will use the {@code policies} to handle failures. Policies are applied in
   * reverse order, with the last policy being applied first.
   *
   * @throws NullPointerException if {@code policies} is null
   * @throws IllegalArgumentException if {@code policies} is empty
   */
  @SafeVarargs
  public Execution(Policy<R>... policies) {
    super(DelegatingScheduler.INSTANCE, new FailsafeExecutor<>(Arrays.asList(Assert.notNull(policies, "policies"))));
    preExecute();
  }

  Execution(FailsafeExecutor<R> executor) {
    super(DelegatingScheduler.INSTANCE, executor);
  }

  /**
   * Records an execution and returns true if a retry can be performed for the {@code result}, else returns false and
   * marks the execution as complete.
   *
   * @throws IllegalStateException if the execution is already complete
   */
  public boolean canRetryFor(R result) {
    preExecute();
    postExecute(new ExecutionResult(result, null));
    return !completed;
  }

  /**
   * Records an execution and returns true if a retry can be performed for the {@code result} or {@code failure}, else
   * returns false and marks the execution as complete.
   *
   * @throws IllegalStateException if the execution is already complete
   */
  public boolean canRetryFor(R result, Throwable failure) {
    preExecute();
    postExecute(new ExecutionResult(result, failure));
    return !completed;
  }

  /**
   * Records an execution and returns true if a retry can be performed for the {@code failure}, else returns false and
   * marks the execution as complete.
   *
   * @throws NullPointerException if {@code failure} is null
   * @throws IllegalStateException if the execution is already complete
   */
  public boolean canRetryOn(Throwable failure) {
    Assert.notNull(failure, "failure");
    preExecute();
    postExecute(new ExecutionResult(null, failure));
    return !completed;
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
   * Records and attempts to complete the execution with the {@code result}. Returns true on success, else false if
   * completion failed and execution should be retried.
   *
   * @throws IllegalStateException if the execution is already complete
   */
  public boolean complete(R result) {
    preExecute();
    postExecute(new ExecutionResult(result, null));
    return completed;
  }

  /**
   * Records a failed execution and returns true if a retry can be performed for the {@code failure}, else returns false
   * and completes the execution.
   * <p>
   * Alias of {@link #canRetryOn(Throwable)}
   *
   * @throws NullPointerException if {@code failure} is null
   * @throws IllegalStateException if the execution is already complete
   */
  public boolean recordFailure(Throwable failure) {
    return canRetryOn(failure);
  }

  /**
   * Performs a synchronous execution.
   */
  ExecutionResult executeSync(Supplier<ExecutionResult> supplier) {
    for (PolicyExecutor<R, Policy<R>> policyExecutor : policyExecutors)
      supplier = policyExecutor.supply(supplier, scheduler);

    ExecutionResult result = supplier.get();
    completed = result.isComplete();
    executor.handleComplete(result, this);
    return result;
  }
  
  <E extends Throwable> ExecutionResultWithException<E> executeSyncWithException(Supplier<ExecutionResultWithException<E>> supplier) {
    for (PolicyExecutor<Policy<Object>> policyExecutor : policyExecutors)
      supplier = policyExecutor.supplyWithException(supplier, scheduler);

    ExecutionResultWithException<E> result = supplier.get();
    completed = result.isComplete();
    executor.handleComplete(result, this);
    return result;
  }
}
