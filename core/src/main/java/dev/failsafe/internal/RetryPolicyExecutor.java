/*
 * Copyright 2018 the original author or authors.
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
package dev.failsafe.internal;

import dev.failsafe.ExecutionContext;
import dev.failsafe.RetryPolicy;
import dev.failsafe.RetryPolicyConfig;
import dev.failsafe.spi.*;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * A PolicyExecutor that handles failures according to a {@link RetryPolicy}.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public class RetryPolicyExecutor<R> extends PolicyExecutor<R> {
  private final RetryPolicyImpl<R> retryPolicy;
  private final RetryPolicyConfig<R> config;

  // Mutable state
  private volatile int failedAttempts;
  private volatile boolean retriesExceeded;
  /** Extracted: owns the delay-computation pipeline and its state (see RetryDelayCalculator). */
  private final RetryDelayCalculator<R> delayCalculator;

  // Handlers
  private final EventHandler<R> abortHandler;
  private final EventHandler<R> failedAttemptHandler;
  private final EventHandler<R> retriesExceededHandler;
  private final EventHandler<R> retryHandler;
  private final EventHandler<R> retryScheduledHandler;

  public RetryPolicyExecutor(RetryPolicyImpl<R> retryPolicy, int policyIndex) {
    super(retryPolicy, policyIndex);
    this.retryPolicy = retryPolicy;
    this.config = retryPolicy.getConfig();
    this.abortHandler = EventHandler.ofExecutionCompleted(config.getAbortListener());
    this.failedAttemptHandler = EventHandler.ofExecutionAttempted(config.getFailedAttemptListener());
    this.retriesExceededHandler = EventHandler.ofExecutionCompleted(config.getRetriesExceededListener());
    this.retryHandler = EventHandler.ofExecutionAttempted(config.getRetryListener());
    this.retryScheduledHandler = EventHandler.ofExecutionScheduled(config.getRetryScheduledListener());
    this.delayCalculator = new RetryDelayCalculator<>(config);
  }

  @Override
  public Function<SyncExecutionInternal<R>, ExecutionResult<R>> apply(
    Function<SyncExecutionInternal<R>, ExecutionResult<R>> innerFn, Scheduler scheduler) {

    return execution -> {
      while (true) {
        ExecutionResult<R> result = innerFn.apply(execution);
        // Returns if retries exceeded or an outer policy cancelled the execution
        if (retriesExceeded || execution.isCancelled(this))
          return result;

        result = postExecute(execution, result);
        if (result.isComplete() || execution.isCancelled(this))
          return result;

        try {
          if (retryScheduledHandler != null)
            retryScheduledHandler.handle(result, execution);

          // Guard against race with Timeout so that sleep can either be skipped or interrupted
          execution.setInterruptable(true);
          Thread.sleep(TimeUnit.NANOSECONDS.toMillis(result.getDelay()));
        } catch (InterruptedException e) {
          // Set interrupt flag if interruption was not performed by Failsafe
          if (!execution.isInterrupted())
            Thread.currentThread().interrupt();
          return ExecutionResult.exception(e);
        } finally {
          execution.setInterruptable(false);
        }

        // Guard against race with Timeout cancelling the execution
        synchronized (execution.getLock()) {
          if (execution.isCancelled(this))
            return result;

          // Initialize next attempt
          execution = execution.copy();
        }

        // Call retry handler
        if (retryHandler != null)
          retryHandler.handle(result, execution);
      }
    };
  }

  @Override
  public Function<AsyncExecutionInternal<R>, CompletableFuture<ExecutionResult<R>>> applyAsync(
    Function<AsyncExecutionInternal<R>, CompletableFuture<ExecutionResult<R>>> innerFn, Scheduler scheduler,
    FailsafeFuture<R> future) {

    return initialRequest -> {
      CompletableFuture<ExecutionResult<R>> promise = new CompletableFuture<>();
      AtomicReference<ExecutionResult<R>> previousResultRef = new AtomicReference<>();

      try {
        handleAsync(initialRequest, innerFn, scheduler, future, promise, previousResultRef);
      } catch (Throwable t) {
        promise.completeExceptionally(t);
      }

      return promise;
    };
  }

  public Object handleAsync(AsyncExecutionInternal<R> execution,
    Function<AsyncExecutionInternal<R>, CompletableFuture<ExecutionResult<R>>> innerFn, Scheduler scheduler,
    FailsafeFuture<R> future, CompletableFuture<ExecutionResult<R>> promise,
    AtomicReference<ExecutionResult<R>> previousResultRef) {

    // Call retry handler
    ExecutionResult<R> previousResult = previousResultRef.get();
    if (retryHandler != null && !execution.isRecorded() && previousResult != null)
      retryHandler.handle(previousResult, execution);

    // Propagate execution and handle result
    innerFn.apply(execution).whenComplete((result, error) -> {
      if (isValidResult(result, error, promise)) {
        if (retriesExceeded || execution.isCancelled(this)) {
          promise.complete(result);
        } else {
          postExecuteAsync(execution, result, scheduler, future).whenComplete((postResult, postError) -> {
            if (isValidResult(postResult, postError, promise)) {
              // Guard against race with Timeout cancelling the execution
              synchronized (execution.getLock()) {
                if (postResult.isComplete() || execution.isCancelled(this)) {
                  promise.complete(postResult);
                  return;
                }

                // Guard against race with future.complete or future.cancel
                synchronized (future) {
                  if (!future.isDone()) {
                    try {
                      if (retryScheduledHandler != null)
                        retryScheduledHandler.handle(postResult, execution);

                      previousResultRef.set(postResult);

                      AsyncExecutionInternal<R> retryExecution = execution.copy();
                      future.setExecution(retryExecution);
                      Callable<Object> retryFn = () -> handleAsync(retryExecution, innerFn, scheduler, future, promise,
                        previousResultRef);
                      Future<?> scheduledRetry = scheduler.schedule(retryFn, postResult.getDelay(),
                        TimeUnit.NANOSECONDS);
                      // Cancel prior inner executions, such as pending timeouts
                      future.cancelDependencies(this, false, null);

                      // Propagate outer cancellations to the thread that the innerFn will run with
                      future.setCancelFn(-1, (mayInterrupt, cancelResult) -> {
                        scheduledRetry.cancel(mayInterrupt);
                      });

                      // Propagate outer cancellations to the retry future and its promise
                      future.setCancelFn(this, (mayInterrupt, cancelResult) -> {
                        promise.complete(cancelResult);
                      });
                    } catch (Throwable t) {
                      // Hard scheduling failure
                      promise.completeExceptionally(t);
                    }
                  }
                }
              }
            }
          });
        }
      }
    });

    return null;
  }

  /**
   * Completes the {@code promise} and returns {@code false} if the {@code result} or {@code error} are invalid, else
   * returns {@code true}.
   */
  boolean isValidResult(ExecutionResult<R> result, Throwable error, CompletableFuture<ExecutionResult<R>> promise) {
    if (error != null) {
      promise.completeExceptionally(error);
      return false;
    } else if (result == null) {
      promise.complete(null);
      return false;
    }
    return true;
  }

  @Override
  public ExecutionResult<R> onFailure(ExecutionContext<R> context, ExecutionResult<R> result) {
    if (failedAttemptHandler != null)
      failedAttemptHandler.handle(result, context);

    failedAttempts++;

    // Determine the delay to use before the next attempt
    Duration userComputedDelay = retryPolicy.computeDelay(context);
    long elapsedNanos = context.getElapsedTime().toNanos();
    long delayNanos = delayCalculator.computeDelayNanos(userComputedDelay, context, elapsedNanos);

    // Calculate result
    boolean maxRetriesExceeded = config.getMaxRetries() != -1 && failedAttempts > config.getMaxRetries();
    boolean maxDurationExceeded = config.getMaxDuration() != null && elapsedNanos > config.getMaxDuration().toNanos();
    retriesExceeded = maxRetriesExceeded || maxDurationExceeded;
    boolean isAbortable = retryPolicy.isAbortable(result.getResult(), result.getException());
    boolean shouldRetry = !result.isSuccess() && !isAbortable && !retriesExceeded && config.allowsRetries();
    boolean completed = isAbortable || !shouldRetry;
    boolean success = completed && result.isSuccess() && !isAbortable;

    // Call event handlers
    if (abortHandler != null && isAbortable)
      abortHandler.handle(result, context);
    else if (retriesExceededHandler != null && !success && retriesExceeded)
      retriesExceededHandler.handle(result, context);

    return result.with(delayNanos, completed, success);
  }

  /**
   * Defaults async executions to not be complete until {@link #onFailure(ExecutionContext, ExecutionResult) says they
   * are}.
   */
  @Override
  public CompletableFuture<ExecutionResult<R>> onFailureAsync(ExecutionContext<R> context, ExecutionResult<R> result,
    Scheduler scheduler, FailsafeFuture<R> future) {
    return super.onFailureAsync(context, result.withNotComplete(), scheduler, future);
  }
}