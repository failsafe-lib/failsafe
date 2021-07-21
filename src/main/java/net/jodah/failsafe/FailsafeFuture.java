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

/**
 * A CompletableFuture implementation that propagates cancellations and calls completion handlers.
 * <p>
 * Part of the Failsafe SPI.
 *
 * @param <T> result type
 * @author Jonathan Halterman
 */
public class FailsafeFuture<T> extends CompletableFuture<T> {
  private final FailsafeExecutor<T> executor;
  private AbstractExecution execution;

  // Mutable state, guarded by "this"
  private Future<T> dependency;
  private Runnable cancelFn;
  private List<Future<T>> timeoutDependencies;

  FailsafeFuture(FailsafeExecutor<T> executor) {
    this.executor = executor;
  }

  /**
   * If not already completed, completes  the future with the {@code value}, calling the complete and success handlers.
   */
  @Override
  public synchronized boolean complete(T value) {
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

    execution.cancelledIndex = Integer.MAX_VALUE;
    boolean cancelResult = super.cancel(mayInterruptIfRunning);
    cancelResult = cancelDependencies(mayInterruptIfRunning, cancelResult);
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
      completed = super.complete((T) result.getResult());
    else
      completed = super.completeExceptionally(failure);
    if (completed)
      executor.handleComplete(result, execution);
    return completed;
  }

  synchronized Future<T> getDependency() {
    return dependency;
  }

  synchronized List<Future<T>> getTimeoutDelegates() {
    return timeoutDependencies;
  }

  /**
   * Cancels the dependency passing in the {@code interruptDelegate} flag, applies the retry cancel fn, and cancels all
   * timeout dependencies.
   */
  synchronized boolean cancelDependencies(boolean interruptDelegate, boolean result) {
    execution.interrupted = interruptDelegate;
    if (dependency != null)
      result = dependency.cancel(interruptDelegate);
    if (cancelFn != null)
      cancelFn.run();
    if (timeoutDependencies != null) {
      for (Future<T> timeoutDelegate : timeoutDependencies)
        timeoutDelegate.cancel(false);
      timeoutDependencies.clear();
    }
    return result;
  }

  void inject(AbstractExecution execution) {
    this.execution = execution;
  }

  /**
   * Injects a {@code dependency} to be cancelled when this future is cancelled.
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  synchronized void inject(Future<?> dependency) {
    this.dependency = (Future) dependency;
  }

  /**
   * Injects a {@code cancelFn} to be called when this future is cancelled.
   */
  synchronized void injectCancelFn(Runnable cancelFn) {
    this.cancelFn = cancelFn;
  }

  /**
   * Injects a {@code timeoutDependency} to be cancelled when this future is cancelled.
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  synchronized void injectTimeout(Future<?> timeoutDependency) {
    if (timeoutDependencies == null)
      timeoutDependencies = new ArrayList<>(3);
    timeoutDependencies.add((Future) timeoutDependency);
  }
}
