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
package net.jodah.failsafe.internal.executor;

import net.jodah.failsafe.ExecutionResult;
import net.jodah.failsafe.FailsafeFuture;
import net.jodah.failsafe.PolicyExecutor;
import net.jodah.failsafe.PolicyListeners.EventListener;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.RetryPolicy.DelayFunction;
import net.jodah.failsafe.util.concurrent.Scheduler;

import java.time.Duration;

import static net.jodah.failsafe.internal.util.RandomDelay.randomDelay;
import static net.jodah.failsafe.internal.util.RandomDelay.randomDelayInRange;

/**
 * A PolicyExecutor that handles failures according to a {@link RetryPolicy}.
 *
 * @author Jonathan Halterman
 */
public class RetryPolicyExecutor extends PolicyExecutor<RetryPolicy> {
  // Mutable state
  private volatile boolean retriesExceeded;
  /** The fixed, backoff, random or computed delay time in nanoseconds. */
  private volatile long delayNanos = -1;
  /** The wait time, which is the delay time adjusted for jitter and max duration, in nanoseconds. */
  private volatile long waitNanos;

  // Listeners
  private EventListener abortListener;
  private EventListener failedAttemptListener;
  private EventListener retriesExceededListener;
  private EventListener retryListener;

  public RetryPolicyExecutor(RetryPolicy retryPolicy, EventListener abortListener, EventListener failedAttemptListener,
      EventListener retriesExceededListener, EventListener retryListener) {
    super(retryPolicy);
    this.abortListener = abortListener;
    this.failedAttemptListener = failedAttemptListener;
    this.retriesExceededListener = retriesExceededListener;
    this.retryListener = retryListener;
  }

  @Override
  protected ExecutionResult preExecute(ExecutionResult result) {
    if (retryListener != null && result != null && execution.getExecutions() > 0)
      retryListener.handle(result, execution);
    return result;
  }

  @Override
  protected ExecutionResult executeSync(ExecutionResult result) {
    while (true) {
      result = super.executeSync(result);
      if (result.completed)
        return result;
    }
  }

  @Override
  protected ExecutionResult executeAsync(ExecutionResult result, boolean shouldExecute, Scheduler scheduler,
      FailsafeFuture<Object> future) {
    while (true) {
      result = super.executeAsync(result, shouldExecute, scheduler, future);
      if (result == null || result.completed)
        return result;

      // Move right again
      shouldExecute = true;
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  protected ExecutionResult onFailure(ExecutionResult result) {
    // Determine the computed delay
    long computedDelayNanos = -1;
    DelayFunction<Object, Throwable> delayFunction = (DelayFunction<Object, Throwable>) policy.getDelayFn();
    if (delayFunction != null && policy.canApplyDelayFn(result.result, result.failure)) {
      Duration computedDelay = delayFunction.computeDelay(result.result, result.failure, execution);
      if (computedDelay != null && computedDelay.toNanos() >= 0)
        computedDelayNanos = computedDelay.toNanos();
    }

    // Determine the non-computed delay
    if (computedDelayNanos == -1) {
      Duration delay = policy.getDelay();
      Duration delayMin = policy.getDelayMin();
      Duration delayMax = policy.getDelayMax();

      if (delayNanos == -1 && delay != null && !delay.equals(Duration.ZERO))
        delayNanos = delay.toNanos();
      else if (delayMin != null && delayMax != null)
        delayNanos = randomDelayInRange(delayMin.toNanos(), delayMin.toNanos(), Math.random());

      // Adjust for backoff
      if (execution.getExecutions() != 1 && policy.getMaxDelay() != null)
        delayNanos = (long) Math.min(delayNanos * policy.getDelayFactor(), policy.getMaxDelay().toNanos());
    }

    waitNanos = computedDelayNanos != -1 ? computedDelayNanos : delayNanos;

    // Adjust the wait time for jitter
    if (policy.getJitter() != null)
      waitNanos = randomDelay(waitNanos, policy.getJitter().toNanos(), Math.random());
    else if (policy.getJitterFactor() > 0.0)
      waitNanos = randomDelay(waitNanos, policy.getJitterFactor(), Math.random());

    // Adjust the wait time for max duration
    long elapsedNanos = execution.getElapsedTime().toNanos();
    if (policy.getMaxDuration() != null) {
      long maxRemainingWaitTime = policy.getMaxDuration().toNanos() - elapsedNanos;
      waitNanos = Math.min(waitNanos, maxRemainingWaitTime < 0 ? 0 : maxRemainingWaitTime);
      if (waitNanos < 0)
        waitNanos = 0;
    }

    // Calculate result
    boolean maxRetriesExceeded = policy.getMaxRetries() != -1 && execution.getExecutions() > policy.getMaxRetries();
    boolean maxDurationExceeded = policy.getMaxDuration() != null && elapsedNanos > policy.getMaxDuration().toNanos();
    retriesExceeded = maxRetriesExceeded || maxDurationExceeded;
    boolean isAbortable = policy.isAbortable(result.result, result.failure);
    boolean shouldRetry = !result.success && !isAbortable && !retriesExceeded && policy.allowsRetries();
    boolean completed = isAbortable || !shouldRetry;
    boolean success = completed && result.success && !isAbortable;

    // Call listeners
    if (failedAttemptListener != null && !success)
      failedAttemptListener.handle(result, execution);
    if (abortListener != null && isAbortable)
      abortListener.handle(result, execution);
    else if (retriesExceededListener != null && !success && retriesExceeded)
      retriesExceededListener.handle(result, execution);

    return result.with(waitNanos, completed, success);
  }
}
