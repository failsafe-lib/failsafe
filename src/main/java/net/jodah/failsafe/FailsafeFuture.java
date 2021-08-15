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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
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
  private AbstractExecution<R> execution;

  // Mutable state, guarded by "this"
  private Future<R> dependentStageFuture;
  private BiConsumer<Boolean, ExecutionResult> cancelFn;
  private List<Future<R>> timeoutFutures;
  private boolean cancelWithInterrupt;

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
   * Cancels this and the internal delegate.
   */
  @Override
  public synchronized boolean cancel(boolean mayInterruptIfRunning) {
    if (isDone())
      return false;

    this.cancelWithInterrupt = mayInterruptIfRunning;
    execution.cancelledIndex = Integer.MAX_VALUE;
    boolean cancelResult = super.cancel(mayInterruptIfRunning);
    cancelDependencies(mayInterruptIfRunning, null);
    ExecutionResult result = ExecutionResult.failure(new CancellationException());
    super.completeExceptionally(result.getFailure());
    executor.handleComplete(result, execution);
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
      executor.handleComplete(result, execution);
    return completed;
  }

  synchronized List<Future<R>> getTimeoutDelegates() {
    return timeoutFutures;
  }

  /**
   * Cancels the dependency passing in the {@code mayInterrupt} flag, applies the retry cancel fn, and cancels all
   * timeout dependencies.
   */
  synchronized void cancelDependencies(boolean mayInterrupt, ExecutionResult cancelResult) {
    execution.interrupted = mayInterrupt;
    if (dependentStageFuture != null)
      dependentStageFuture.cancel(mayInterrupt);
    if (timeoutFutures != null) {
      for (Future<R> timeoutDelegate : timeoutFutures)
        timeoutDelegate.cancel(false);
      timeoutFutures.clear();
    }
    if (cancelFn != null)
      cancelFn.accept(mayInterrupt, cancelResult);
  }

  void inject(AbstractExecution<R> execution) {
    this.execution = execution;
  }

  /**
   * Injects a {@code dependentStageFuture} to be cancelled when this future is cancelled.
   */
  synchronized void injectStage(Future<R> dependentStageFuture) {
    // If outer future has already been cancelled, propagate the cancellation
    if (isCancelled())
      dependentStageFuture.cancel(cancelWithInterrupt);
    else
      this.dependentStageFuture = dependentStageFuture;
  }

  /**
   * Injects a {@code cancelFn} to be called when this future is cancelled.
   */
  synchronized void injectCancelFn(BiConsumer<Boolean, ExecutionResult> cancelFn) {
    this.cancelFn = cancelFn;
  }

  /**
   * Injects a {@code scheduledTimeoutExec} to be cancelled when this future is cancelled.
   */
  synchronized void injectTimeout(Future<R> timeoutFuture) {
    if (timeoutFutures == null)
      timeoutFutures = new ArrayList<>(3);
    timeoutFutures.add(timeoutFuture);
  }
}
