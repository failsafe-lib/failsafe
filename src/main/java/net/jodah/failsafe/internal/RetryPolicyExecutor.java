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
package net.jodah.failsafe.internal;

import net.jodah.failsafe.ExecutionContext;
import net.jodah.failsafe.FailsafeException;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.RetryPolicyConfig;
import net.jodah.failsafe.spi.EventHandler;
import net.jodah.failsafe.spi.*;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static net.jodah.failsafe.internal.util.RandomDelay.randomDelay;
import static net.jodah.failsafe.internal.util.RandomDelay.randomDelayInRange;

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
  /** The last fixed, backoff, random or computed delay time in nanoseconds. */
  private volatile long lastDelayNanos;

  // Handlers
  private final EventHandler<R> abortHandler;
  private final EventHandler<R> failedAttemptHandler;
  private final EventHandler<R> retriesExceededHandler;
  private final EventHandler<R> retryHandler;
  private final EventHandler<R> retryScheduledHandler;

  public RetryPolicyExecutor(RetryPolicyImpl<R> retryPolicy, int policyIndex, EventHandler<R> successHandler,
    EventHandler<R> failureHandler, EventHandler<R> abortHandler, EventHandler<R> failedAttemptHandler,
    EventHandler<R> retriesExceededHandler, EventHandler<R> retryHandler, EventHandler<R> retryScheduledHandler) {
    super(policyIndex, retryPolicy, successHandler, failureHandler);
    this.retryPolicy = retryPolicy;
    this.config = retryPolicy.getConfig();
    this.abortHandler = abortHandler;
    this.failedAttemptHandler = failedAttemptHandler;
    this.retriesExceededHandler = retriesExceededHandler;
    this.retryHandler = retryHandler;
    this.retryScheduledHandler = retryScheduledHandler;
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
          // Set interrupt flag if interrupt was not intended
          if (!execution.isInterrupted())
            Thread.currentThread().interrupt();
          return ExecutionResult.failure(new FailsafeException(e));
        } finally {
          execution.setInterruptable(false);
        }

        if (execution.isCancelled(this))
          return result;

        // Initialize next attempt
        execution = execution.copy();

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
              if (postResult.isComplete() || execution.isCancelled(this)) {
                promise.complete(postResult);
              } else {
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
    long delayNanos = lastDelayNanos;

    // Determine the computed delay
    Duration computedDelay = retryPolicy.computeDelay(context);
    if (computedDelay != null) {
      delayNanos = computedDelay.toNanos();
    } else {
      // Determine the fixed or random delay
      delayNanos = getFixedOrRandomDelayNanos(delayNanos);
      delayNanos = adjustForBackoff(context, delayNanos);
      lastDelayNanos = delayNanos;
    }

    delayNanos = adjustForJitter(delayNanos);
    long elapsedNanos = context.getElapsedTime().toNanos();
    delayNanos = adjustForMaxDuration(delayNanos, elapsedNanos);

    // Calculate result
    boolean maxRetriesExceeded = config.getMaxRetries() != -1 && failedAttempts > config.getMaxRetries();
    boolean maxDurationExceeded = config.getMaxDuration() != null && elapsedNanos > config.getMaxDuration().toNanos();
    retriesExceeded = maxRetriesExceeded || maxDurationExceeded;
    boolean isAbortable = retryPolicy.isAbortable(result.getResult(), result.getFailure());
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

  private long getFixedOrRandomDelayNanos(long delayNanos) {
    Duration delay = config.getDelay();
    Duration delayMin = config.getDelayMin();
    Duration delayMax = config.getDelayMax();

    if (delayNanos == 0 && delay != null && !delay.equals(Duration.ZERO))
      delayNanos = delay.toNanos();
    else if (delayMin != null && delayMax != null)
      delayNanos = randomDelayInRange(delayMin.toNanos(), delayMax.toNanos(), Math.random());
    return delayNanos;
  }

  private long adjustForBackoff(ExecutionContext<R> context, long delayNanos) {
    if (context.getAttemptCount() != 1 && config.getMaxDelay() != null)
      delayNanos = (long) Math.min(delayNanos * config.getDelayFactor(), config.getMaxDelay().toNanos());
    return delayNanos;
  }

  private long adjustForJitter(long delayNanos) {
    if (config.getJitter() != null)
      delayNanos = randomDelay(delayNanos, config.getJitter().toNanos(), Math.random());
    else if (config.getJitterFactor() > 0.0)
      delayNanos = randomDelay(delayNanos, config.getJitterFactor(), Math.random());
    return delayNanos;
  }

  private long adjustForMaxDuration(long delayNanos, long elapsedNanos) {
    if (config.getMaxDuration() != null) {
      long maxRemainingDelay = config.getMaxDuration().toNanos() - elapsedNanos;
      delayNanos = Math.min(delayNanos, maxRemainingDelay < 0 ? 0 : maxRemainingDelay);
      if (delayNanos < 0)
        delayNanos = 0;
    }
    return delayNanos;
  }
}
