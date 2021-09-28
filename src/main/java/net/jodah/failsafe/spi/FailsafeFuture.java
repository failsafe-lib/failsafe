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
package net.jodah.failsafe.spi;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;

/**
 * A CompletableFuture implementation that propagates cancellations and calls completion handlers.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public class FailsafeFuture<R> extends CompletableFuture<R> {
  private final CompletionHandler<R> completionHandler;

  // Mutable state guarded by "this"

  // The most recent execution attempt
  private ExecutionInternal<R> newestExecution;
  // Functions to apply when this future is cancelled for each policy index, in descending order
  private Map<Integer, BiConsumer<Boolean, ExecutionResult<R>>> cancelFunctions;
  // Whether a cancel with interrupt has already occurred
  private boolean cancelledWithInterrupt;

  public FailsafeFuture(CompletionHandler<R> completionHandler) {
    this.completionHandler = completionHandler;
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
    newestExecution.cancel();
    boolean cancelResult = super.cancel(mayInterruptIfRunning);
    cancelDependencies(null, mayInterruptIfRunning, null);
    ExecutionResult<R> result = ExecutionResult.failure(new CancellationException());
    super.completeExceptionally(result.getFailure());
    completionHandler.handleComplete(result, newestExecution);
    return cancelResult;
  }

  /**
   * Completes the execution with the {@code result} and calls the completion handler.
   */
  public synchronized boolean completeResult(ExecutionResult<R> result) {
    if (isDone())
      return false;

    Throwable failure = result.getFailure();
    boolean completed;
    if (failure == null)
      completed = super.complete(result.getResult());
    else
      completed = super.completeExceptionally(failure);
    if (completed)
      completionHandler.handleComplete(result, newestExecution);
    return completed;
  }

  /**
   * Applies any {@link #setCancelFn(PolicyExecutor, BiConsumer) cancel functions} with the {@code cancelResult} for
   * PolicyExecutors whose policyIndex is < the policyIndex of the {@code cancellingPolicyExecutor}.
   *
   * @param cancellingPolicyExecutor the PolicyExecutor that is requesting the cancellation of inner policy executors
   */
  public synchronized void cancelDependencies(PolicyExecutor<R, ?> cancellingPolicyExecutor, boolean mayInterrupt,
    ExecutionResult<R> cancelResult) {
    if (cancelFunctions != null) {
      int cancellingPolicyIndex =
        cancellingPolicyExecutor == null ? Integer.MAX_VALUE : cancellingPolicyExecutor.getPolicyIndex();
      Iterator<Entry<Integer, BiConsumer<Boolean, ExecutionResult<R>>>> it = cancelFunctions.entrySet().iterator();

      /* This iteration occurs in descending order to ensure that the {@code cancelResult} can be supplied to outer
      cancel functions before the inner supplier is cancelled, which would cause PolicyExecutors to complete with
      CancellationException rather than the expected {@code cancelResult}. */
      while (it.hasNext()) {
        Map.Entry<Integer, BiConsumer<Boolean, ExecutionResult<R>>> entry = it.next();
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

  /**
   * Sets the {@code execution} representing the most recent attempt, which will be cancelled if this future is
   * cancelled.
   */
  public synchronized void setExecution(ExecutionInternal<R> execution) {
    this.newestExecution = execution;
  }

  /**
   * Sets a {@code cancelFn} to be called when a PolicyExecutor {@link #cancelDependencies(PolicyExecutor, boolean,
   * ExecutionResult) cancels dependencies} with a policyIndex > the given {@code policyIndex}, or when this future is
   * {@link #cancel(boolean) cancelled}.
   */
  public synchronized void setCancelFn(int policyIndex, BiConsumer<Boolean, ExecutionResult<R>> cancelFn) {
    if (cancelFunctions == null)
      cancelFunctions = new TreeMap<>(Collections.reverseOrder());
    cancelFunctions.put(policyIndex, cancelFn);
  }

  /**
   * Sets a {@code cancelFn} to be called when a PolicyExecutor {@link #cancelDependencies(PolicyExecutor, boolean,
   * ExecutionResult) cancels dependencies} with a policyIndex > the policyIndex of the given {@code policyExecutor}, or
   * when this future is {@link #cancel(boolean) cancelled}.
   */
  public synchronized void setCancelFn(PolicyExecutor<R, ?> policyExecutor,
    BiConsumer<Boolean, ExecutionResult<R>> cancelFn) {
    if (cancelFunctions == null)
      cancelFunctions = new TreeMap<>(Collections.reverseOrder());
    cancelFunctions.put(policyExecutor.getPolicyIndex(), cancelFn);
  }

  /**
   * Propogates any previous cancellation to the {@code future}, either by cancelling it immediately or setting a cancel
   * function to cancel it later.
   */
  public synchronized void propagateCancellation(Future<R> future) {
    if (isCancelled())
      future.cancel(cancelledWithInterrupt);
    else
      setCancelFn(-2, (mayInterrupt, cancelResult) -> future.cancel(mayInterrupt));
  }
}
