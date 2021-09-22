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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * A CompletableFuture implementation that propagates cancellations and calls completion handlers.
 * <p>
 * Part of the Failsafe SPI.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public class FailsafeFuture<R> extends CompletableFuture<R> {
  private final FailsafeExecutor<R> executor;

  // Mutable state guarded by "this" --

  private AbstractExecution<R> newestExecution;
  Map<Integer, BiConsumer<Boolean, ExecutionResult>> cancelFunctions;
  // Whether a cancel with interrupt has already occurred
  boolean cancelledWithInterrupt;

  FailsafeFuture(FailsafeExecutor<R> executor) {
    this.executor = executor;
  }

  /**
   * If not already completed, completes  the future with the {@code value}, calling the complete and success handlers.
   */
  @Override
  public synchronized boolean complete(R value) {
    return completeResult(ExecutionResult.success(value));
  }

  /**
   * If not already completed, completes the future with the {@code failure}, calling the complete and failure
   * handlers.
   */
  @Override
  public synchronized boolean completeExceptionally(Throwable failure) {
    return completeResult(ExecutionResult.failure(failure));
  }

  /**
   * Cancels the future along with any dependencies.
   */
  @Override
  public synchronized boolean cancel(boolean mayInterruptIfRunning) {
    if (isDone())
      return false;

    this.cancelledWithInterrupt = mayInterruptIfRunning;
    newestExecution.cancelledIndex = Integer.MAX_VALUE;
    boolean cancelResult = super.cancel(mayInterruptIfRunning);
    cancelDependencies(Integer.MAX_VALUE, mayInterruptIfRunning, null);
    ExecutionResult result = ExecutionResult.failure(new CancellationException());
    super.completeExceptionally(result.getFailure());
    executor.handleComplete(result, newestExecution);
    return cancelResult;
  }

  /**
   * Completes the execution with the {@code result} and calls completion listeners.
   */
  @SuppressWarnings("unchecked")
  synchronized boolean completeResult(ExecutionResult result) {
    if (isDone())
      return false;

    Throwable failure = result.getFailure();
    boolean completed;
    if (failure == null)
      completed = super.complete((R) result.getResult());
    else
      completed = super.completeExceptionally(failure);
    if (completed)
      executor.handleComplete(result, newestExecution);
    return completed;
  }

  /**
   * Cancels any {@link #injectCancelFn(int, BiConsumer) cancel functions} whose policy index is <= the given {@code
   * cancellingPolicyIndex}.
   *
   * @param cancellingPolicyIndex the policyIndex of the PolicyExecutor that the cancellation request is originating
   * from
   */
  synchronized void cancelDependencies(int cancellingPolicyIndex, boolean mayInterrupt, ExecutionResult cancelResult) {
    if (cancelFunctions != null) {
      Iterator<Entry<Integer, BiConsumer<Boolean, ExecutionResult>>> it = cancelFunctions.entrySet().iterator();
      while (it.hasNext()) {
        Map.Entry<Integer, BiConsumer<Boolean, ExecutionResult>> entry = it.next();
        if (cancellingPolicyIndex > entry.getKey()) {
          try {
            entry.getValue().accept(mayInterrupt, cancelResult);
          } catch (Exception ignore) {
          }
          it.remove();
        }
      }
    }
  }

  synchronized void inject(AbstractExecution<R> execution) {
    this.newestExecution = execution;
  }

  /**
   * Injects a {@code cancelFn} to be called when this future is cancelled with a policyIndex that is >= the given
   * {@code policyIndex}.
   */
  synchronized void injectCancelFn(int policyIndex, BiConsumer<Boolean, ExecutionResult> cancelFn) {
    if (cancelFunctions == null)
      cancelFunctions = new HashMap<>();
    cancelFunctions.put(policyIndex, cancelFn);
  }
}
