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
import net.jodah.failsafe.util.concurrent.Scheduler;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static net.jodah.failsafe.internal.util.RandomDelay.randomDelay;
import static net.jodah.failsafe.internal.util.RandomDelay.randomDelayInRange;

/**
 * A PolicyExecutor that handles failures according to a {@link RetryPolicy}.
 *
 * @author Jonathan Halterman
 */
class RetryPolicyExecutor extends PolicyExecutor<RetryPolicy> {
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

  RetryPolicyExecutor(RetryPolicy retryPolicy, AbstractExecution execution, EventListener abortListener,
    EventListener failedAttemptListener, EventListener retriesExceededListener, EventListener retryListener,
    EventListener retryScheduledListener) {
    super(retryPolicy, execution);
    this.abortListener = abortListener;
    this.failedAttemptListener = failedAttemptListener;
    this.retriesExceededListener = retriesExceededListener;
    this.retryListener = retryListener;
    this.retryScheduledListener = retryScheduledListener;
  }

  @Override
  protected Supplier<ExecutionResult> supply(Supplier<ExecutionResult> supplier, Scheduler scheduler) {
    return () -> {
      while (true) {
        ExecutionResult result = supplier.get();
        // Returns if retries exceeded or an outer policy cancelled the execution
        if (retriesExceeded || executionCancelled())
          return result;

        result = postExecute(result);
        if (result.isComplete() || executionCancelled())
          return result;

        try {
          if (retryScheduledListener != null)
            retryScheduledListener.handle(result, execution);

          // Guard against race with Timeout so that sleep can either be skipped or interrupted
          execution.canInterrupt = true;
          Thread.sleep(TimeUnit.NANOSECONDS.toMillis(result.getWaitNanos()));
        } catch (InterruptedException e) {
          // Set interrupt flag if interrupt was not intended
          if (!execution.interrupted)
            Thread.currentThread().interrupt();
          return ExecutionResult.failure(new FailsafeException(e));
        } finally {
          execution.canInterrupt = false;
        }

        if (executionCancelled())
          return result;

        // Call retry listener
        if (retryListener != null)
          retryListener.handle(result, execution);
      }
    };
  }

  @Override
  protected Supplier<CompletableFuture<ExecutionResult>> supplyAsync(
    Supplier<CompletableFuture<ExecutionResult>> supplier, Scheduler scheduler, FailsafeFuture<Object> future) {
    return () -> {
      CompletableFuture<ExecutionResult> promise = new CompletableFuture<>();
      Callable<Object> callable = new Callable<Object>() {
        volatile ExecutionResult previousResult;

        @Override
        public Object call() {
          // Call retry listener
          if (retryListener != null && previousResult != null)
            retryListener.handle(previousResult, execution);

          // Propagate execution and handle result
          supplier.get().whenComplete((result, error) -> {
            if (error != null)
              promise.completeExceptionally(error);
            else if (result != null) {
              if (retriesExceeded || executionCancelled()) {
                promise.complete(result);
              } else {
                postExecuteAsync(result, scheduler, future).whenComplete((postResult, postError) -> {
                  if (postError != null)
                    promise.completeExceptionally(postError);
                  else if (postResult != null) {
                    if (postResult.isComplete() || executionCancelled()) {
                      promise.complete(postResult);
                    } else {
                      // Guard against race with future.complete or future.cancel
                      synchronized (future) {
                        if (!future.isDone()) {
                          try {
                            if (retryScheduledListener != null)
                              retryScheduledListener.handle(postResult, execution);

                            previousResult = postResult;
                            future.inject(scheduler.schedule(this, postResult.getWaitNanos(), TimeUnit.NANOSECONDS));
                            future.injectCancelFn(() -> {
                              // Ensure that the promise completes if a scheduled retry is cancelled
                              if (executionCancelled())
                                promise.complete(null);
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
      };

      try {
        callable.call();
      } catch (Throwable t) {
        promise.completeExceptionally(t);
      }

      return promise;
    };
  }

  @Override
  @SuppressWarnings("unchecked")
  protected ExecutionResult onFailure(ExecutionResult result) {
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
      waitNanos = adjustForBackoff(waitNanos);
      delayNanos = waitNanos;
    }

    waitNanos = adjustForJitter(waitNanos);
    long elapsedNanos = execution.getElapsedTime().toNanos();
    waitNanos = adjustForMaxDuration(waitNanos, elapsedNanos);

    // Calculate result
    boolean maxRetriesExceeded = policy.getMaxRetries() != -1 && failedAttempts > policy.getMaxRetries();
    boolean maxDurationExceeded = policy.getMaxDuration() != null && elapsedNanos > policy.getMaxDuration().toNanos();
    retriesExceeded = maxRetriesExceeded || maxDurationExceeded;
    boolean isAbortable = policy.isAbortable(result.getResult(), result.getFailure());
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
   * Defaults async executions to not be complete until {@link #onFailure(ExecutionResult) says they are}.
   */
  @Override
  protected CompletableFuture<ExecutionResult> onFailureAsync(ExecutionResult result, Scheduler scheduler,
    FailsafeFuture<Object> future) {
    return super.onFailureAsync(result.withNotComplete(), scheduler, future);
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

  private long adjustForBackoff(long waitNanos) {
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
