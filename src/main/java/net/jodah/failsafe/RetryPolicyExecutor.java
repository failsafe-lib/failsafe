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
import java.util.concurrent.Future;
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
  private volatile long delayNanos = -1;

  // Listeners
  private EventListener abortListener;
  private EventListener failedAttemptListener;
  private EventListener retriesExceededListener;
  private EventListener retryListener;

  RetryPolicyExecutor(RetryPolicy retryPolicy, AbstractExecution execution, EventListener abortListener,
    EventListener failedAttemptListener, EventListener retriesExceededListener, EventListener retryListener) {
    super(retryPolicy, execution);
    this.abortListener = abortListener;
    this.failedAttemptListener = failedAttemptListener;
    this.retriesExceededListener = retriesExceededListener;
    this.retryListener = retryListener;
  }

  @Override
  protected Supplier<ExecutionResult> supply(Supplier<ExecutionResult> supplier, Scheduler scheduler) {
    return () -> {
      while (true) {
        ExecutionResult result = supplier.get();
        if (retriesExceeded)
          return result;

        result = postExecute(result);
        if (result.isComplete())
          return result;

        try {
          Thread.sleep(TimeUnit.NANOSECONDS.toMillis(result.getWaitNanos()));
        } catch (InterruptedException e) {
          if (!execution.interrupted)
            Thread.currentThread().interrupt();
          return ExecutionResult.failure(new FailsafeException(e));
        }

        // Call retry listener
        if (retryListener != null)
          retryListener.handle(result, execution);
      }
    };
  }

  @Override
  @SuppressWarnings("unchecked")
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
              if (retriesExceeded)
                promise.complete(result);
              else  {
                postExecuteAsync(result, scheduler, future).whenComplete((postResult, postError) -> {
                  if (postError != null)
                    promise.completeExceptionally(postError);
                  else if (postResult != null) {
                    if (retriesExceeded || postResult.isComplete())
                      promise.complete(postResult);
                    else {
                      // Guard against race with future.complete or future.cancel
                      synchronized (future) {
                        if (!future.isDone()) {
                          try {
                            previousResult = postResult;
                            future.inject(
                              (Future) scheduler.schedule(this, postResult.getWaitNanos(), TimeUnit.NANOSECONDS));
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

    // Call attempt listeners
    if (failedAttemptListener != null && !success)
      failedAttemptListener.handle(result, execution);

    // Call completion listeners
    if (abortListener != null && isAbortable)
      abortListener.handle(result, execution);
    else if (retriesExceededListener != null && !success && retriesExceeded)
      retriesExceededListener.handle(result, execution);

    return result.with(waitNanos, completed, success);
  }

  private long getFixedOrRandomDelayNanos(long waitNanos) {
    Duration delay = policy.getDelay();
    Duration delayMin = policy.getDelayMin();
    Duration delayMax = policy.getDelayMax();

    if (waitNanos == -1 && delay != null && !delay.equals(Duration.ZERO))
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
