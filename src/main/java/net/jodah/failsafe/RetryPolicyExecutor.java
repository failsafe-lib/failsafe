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
package net.jodah.failsafe;

import net.jodah.failsafe.internal.EventListener;
import net.jodah.failsafe.internal.util.Assert;
import net.jodah.failsafe.util.concurrent.Scheduler;

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
class RetryPolicyExecutor<R> extends PolicyExecutor<R, RetryPolicy<R>> {
  // Mutable state
  private volatile int failedAttempts;
  private volatile boolean retriesExceeded;
  /** The fixed, backoff, random or computed delay time in nanoseconds. */
  private volatile long delayNanos;

  // Listeners
  private final EventListener abortListener;
  private final EventListener failedAttemptListener;
  private final EventListener retriesExceededListener;
  private final EventListener retryListener;
  private final EventListener retryScheduledListener;

  RetryPolicyExecutor(RetryPolicy<R> retryPolicy, int policyIndex, EventListener abortListener,
    EventListener failedAttemptListener, EventListener retriesExceededListener, EventListener retryListener,
    EventListener retryScheduledListener) {
    super(retryPolicy, policyIndex);
    this.abortListener = abortListener;
    this.failedAttemptListener = failedAttemptListener;
    this.retriesExceededListener = retriesExceededListener;
    this.retryListener = retryListener;
    this.retryScheduledListener = retryScheduledListener;
  }

  @Override
  protected Function<Execution<R>, ExecutionResult> apply(Function<Execution<R>, ExecutionResult> innerFn,
    Scheduler scheduler) {
    return execution -> {
      while (true) {
        ExecutionResult result = innerFn.apply(execution);
        // Returns if retries exceeded or an outer policy cancelled the execution
        if (retriesExceeded || execution.isCancelled(this))
          return result;

        result = postExecute(execution, result);
        if (result.isComplete() || execution.isCancelled(this))
          return result;

        try {
          if (retryScheduledListener != null)
            retryScheduledListener.handle(result, execution);

          // Guard against race with Timeout so that sleep can either be skipped or interrupted
          execution.interruptState.canInterrupt = true;
          Thread.sleep(TimeUnit.NANOSECONDS.toMillis(result.getWaitNanos()));
        } catch (InterruptedException e) {
          Assert.log(RetryPolicyExecutor.class, "Retry sleep interrupted exec=%s", execution.hashCode());

          // Set interrupt flag if interrupt was not intended
          if (!execution.interruptState.interrupted)
            Thread.currentThread().interrupt();
          return ExecutionResult.failure(new FailsafeException(e));
        } finally {
          execution.interruptState.canInterrupt = false;
        }

        if (execution.isCancelled(this))
          return result;

        // Initialize next attempt
        execution = execution.copy();

        Assert.log(RetryPolicyExecutor.class, "Retrying for new exec=%s", execution.hashCode());

        // Call retry listener
        if (retryListener != null)
          retryListener.handle(result, execution);
      }
    };
  }

  @Override
  protected Function<AsyncExecution<R>, CompletableFuture<ExecutionResult>> applyAsync(
    Function<AsyncExecution<R>, CompletableFuture<ExecutionResult>> innerFn, Scheduler scheduler,
    FailsafeFuture<R> future) {

    return initialRequest -> {
      CompletableFuture<ExecutionResult> promise = new CompletableFuture<>();
      AtomicReference<ExecutionResult> previousResultRef = new AtomicReference<>();

      try {
        handleAsync(initialRequest, innerFn, scheduler, future, promise, previousResultRef);
      } catch (Throwable t) {
        promise.completeExceptionally(t);
      }

      return promise;
    };
  }

  public Object handleAsync(AsyncExecution<R> execution,
    Function<AsyncExecution<R>, CompletableFuture<ExecutionResult>> innerFn, Scheduler scheduler,
    FailsafeFuture<R> future, CompletableFuture<ExecutionResult> promise,
    AtomicReference<ExecutionResult> previousResultRef) {

    // Call retry listener
    ExecutionResult previousResult = previousResultRef.get();
    if (!execution.recordCalled && retryListener != null && previousResult != null) {
      Assert.log(this, "Calling onRetry attempts=%s, exec=%s", execution.attempts.get(), execution.hashCode());
      retryListener.handle(previousResult, execution);
    }

    // Propagate execution and handle result
    Assert.log(this, "Applying innerFn exec=%s, promise=%s", execution.hashCode(), promise.hashCode());
    innerFn.apply(execution).whenComplete((result, error) -> {
      Assert.log(this, "Applied innerFn exec=%s, promise=%s, result=%s", execution.hashCode(), promise.hashCode(),
        result == null ? null : result.toSummary());
      if (isValidResult(result, error, promise)) {
        if (retriesExceeded || execution.isCancelled(this)) {
          promise.complete(result);
        } else {
          Assert.log(RetryPolicyExecutor.class, "Maybe post executing for exec=%s, promise=%s", execution.hashCode(),
            promise.hashCode());
          postExecuteAsync(execution, result, scheduler, future).whenComplete((postResult, postError) -> {
            if (isValidResult(postResult, postError, promise)) {
              if (postResult.isComplete() || execution.isCancelled(this)) {
                Assert.log(RetryPolicyExecutor.class, "Completing promise=%s", promise.hashCode());
                promise.complete(postResult);
              } else {
                // Guard against race with future.complete or future.cancel
                synchronized (future) {
                  if (!future.isDone()) {
                    try {
                      if (retryScheduledListener != null)
                        retryScheduledListener.handle(postResult, execution);

                      previousResultRef.set(postResult);
                      AsyncExecution<R> retryExecution = execution.copy();
                      future.inject(retryExecution);
                      Callable<Object> retryFn = () -> handleAsync(retryExecution, innerFn, scheduler, future, promise,
                        previousResultRef);
                      Future<?> scheduledRetry = scheduler.schedule(retryFn, postResult.getWaitNanos(),
                        TimeUnit.NANOSECONDS);
                      retryExecution.innerFuture = scheduledRetry;
                      Assert.log(RetryPolicyExecutor.class, "Scheduled retry for new exec=%s, future=%s",
                        retryExecution.hashCode(), scheduledRetry.hashCode());

                      // Propagate outer cancellations to the retry future and its promise
                      future.injectCancelFn(policyIndex, (mayInterrupt, cancelResult) -> {
                        Assert.log(Functions.class, "Cancelling retry future=%s", scheduledRetry.hashCode());
                        scheduledRetry.cancel(mayInterrupt);
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
  boolean isValidResult(ExecutionResult result, Throwable error, CompletableFuture<ExecutionResult> promise) {
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
  @SuppressWarnings("unchecked")
  protected ExecutionResult onFailure(AbstractExecution<R> execution, ExecutionResult result) {
    if (failedAttemptListener != null)
      failedAttemptListener.handle(result, execution);

    failedAttempts++;
    long waitNanos = delayNanos;

    // Determine the computed delay
    Duration computedDelay = policy.computeDelay(execution);
    if (computedDelay != null) {
      waitNanos = computedDelay.toNanos();
    } else {
      // Determine the fixed or random delay
      waitNanos = getFixedOrRandomDelayNanos(waitNanos);
      waitNanos = adjustForBackoff(execution, waitNanos);
      delayNanos = waitNanos;
    }

    waitNanos = adjustForJitter(waitNanos);
    long elapsedNanos = execution.getElapsedTime().toNanos();
    waitNanos = adjustForMaxDuration(waitNanos, elapsedNanos);

    // Calculate result
    boolean maxRetriesExceeded = policy.getMaxRetries() != -1 && failedAttempts > policy.getMaxRetries();
    boolean maxDurationExceeded = policy.getMaxDuration() != null && elapsedNanos > policy.getMaxDuration().toNanos();
    retriesExceeded = maxRetriesExceeded || maxDurationExceeded;
    boolean isAbortable = policy.isAbortable((R) result.getResult(), result.getFailure());
    boolean shouldRetry = !result.isSuccess() && !isAbortable && !retriesExceeded && policy.allowsRetries();
    boolean completed = isAbortable || !shouldRetry;
    boolean success = completed && result.isSuccess() && !isAbortable;

    // Call completion listeners
    if (abortListener != null && isAbortable)
      abortListener.handle(result, execution);
    else if (retriesExceededListener != null && !success && retriesExceeded)
      retriesExceededListener.handle(result, execution);

    return result.with(waitNanos, completed, success);
  }

  /**
   * Defaults async executions to not be complete until {@link #onFailure(AbstractExecution, ExecutionResult) says they
   * are}.
   */
  @Override
  protected CompletableFuture<ExecutionResult> onFailureAsync(AbstractExecution<R> execution, ExecutionResult result,
    Scheduler scheduler, FailsafeFuture<R> future) {
    return super.onFailureAsync(execution, result.withNotComplete(), scheduler, future);
  }

  private long getFixedOrRandomDelayNanos(long waitNanos) {
    Duration delay = policy.getDelay();
    Duration delayMin = policy.getDelayMin();
    Duration delayMax = policy.getDelayMax();

    if (waitNanos == 0 && delay != null && !delay.equals(Duration.ZERO))
      waitNanos = delay.toNanos();
    else if (delayMin != null && delayMax != null)
      waitNanos = randomDelayInRange(delayMin.toNanos(), delayMax.toNanos(), Math.random());
    return waitNanos;
  }

  private long adjustForBackoff(AbstractExecution<R> execution, long waitNanos) {
    if (execution.getAttemptCount() != 1 && policy.getMaxDelay() != null)
      waitNanos = (long) Math.min(waitNanos * policy.getDelayFactor(), policy.getMaxDelay().toNanos());
    return waitNanos;
  }

  private long adjustForJitter(long waitNanos) {
    if (policy.getJitter() != null)
      waitNanos = randomDelay(waitNanos, policy.getJitter().toNanos(), Math.random());
    else if (policy.getJitterFactor() > 0.0)
      waitNanos = randomDelay(waitNanos, policy.getJitterFactor(), Math.random());
    return waitNanos;
  }

  private long adjustForMaxDuration(long waitNanos, long elapsedNanos) {
    if (policy.getMaxDuration() != null) {
      long maxRemainingWaitTime = policy.getMaxDuration().toNanos() - elapsedNanos;
      waitNanos = Math.min(waitNanos, maxRemainingWaitTime < 0 ? 0 : maxRemainingWaitTime);
      if (waitNanos < 0)
        waitNanos = 0;
    }
    return waitNanos;
  }
}
