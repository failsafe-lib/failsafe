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

import net.jodah.failsafe.FailsafeException;
import net.jodah.failsafe.FailsafeFuture;
import net.jodah.failsafe.PolicyExecutor;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.RetryPolicy.DelayFunction;
import net.jodah.failsafe.util.Duration;
import net.jodah.failsafe.util.concurrent.Scheduler;

import java.util.concurrent.TimeUnit;

import static net.jodah.failsafe.internal.util.RandomDelay.randomDelay;
import static net.jodah.failsafe.internal.util.RandomDelay.randomDelayInRange;

/**
 * A PolicyExecutor that handles failures according to a {@link RetryPolicy}.
 *
 * @author Jonathan Halterman
 * @param <T> result type
 */
public class RetryPolicyExecutor extends PolicyExecutor {
  private final RetryPolicy retryPolicy;

  // Mutable state
  private volatile boolean retriesExceeded;
  /** The fixed, backoff, random or computed delay time in nanoseconds. */
  private volatile long delayNanos = -1;
  /** The wait time, which is the delay time adjusted for jitter and max duration, in nanoseconds. */
  private volatile long waitNanos;

  public RetryPolicyExecutor(RetryPolicy retryPolicy) {
    this.retryPolicy = retryPolicy;
  }

  @Override
  public PolicyResult preExecute(PolicyResult result) {
    if (result != null && execution.getExecutions() > 0)
      eventHandler.handleRetry(result.result, result.failure, execution);
    return result;
  }

  @Override
  public PolicyResult executeSync(PolicyResult result) {
    while (true) {
      result = super.executeSync(result);

      if (result.completed) {
        return result;
      } else {
        try {
          Thread.sleep(TimeUnit.NANOSECONDS.toMillis(waitNanos));
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return new PolicyResult(null, new FailsafeException(e), true, false);
        }
      }
    }
  }

  @Override
  public PolicyResult executeAsync(PolicyResult result, Scheduler scheduler, FailsafeFuture<Object> future, boolean shouldExecute) {
    while (true) {
      result = super.executeAsync(result, scheduler, future, shouldExecute);

      if (result == null || result.completed)
        return result;

      // Move right again
      shouldExecute = true;
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public PolicyResult postExecute(PolicyResult pr) {
    // Determine the computed delay
    long computedDelayNanos = -1;
    DelayFunction<Object, Throwable> delayFunction = (DelayFunction<Object, Throwable>) retryPolicy.getDelayFn();
    if (delayFunction != null && retryPolicy.canApplyDelayFn(pr.result, pr.failure)) {
      Duration computedDelay = delayFunction.computeDelay(pr.result, pr.failure, execution);
      if (computedDelay != null && computedDelay.toNanos() >= 0)
        computedDelayNanos = computedDelay.toNanos();
    }

    // Determine the non-computed delay
    if (computedDelayNanos == -1) {
      Duration delay = retryPolicy.getDelay();
      Duration delayMin = retryPolicy.getDelayMin();
      Duration delayMax = retryPolicy.getDelayMax();

      if (delayNanos == -1 && delay != null && !delay.equals(Duration.NONE))
        delayNanos = delay.toNanos();
      else if (delayMin != null && delayMax != null)
        delayNanos = randomDelayInRange(delayMin.toNanos(), delayMin.toNanos(), Math.random());

      // Adjust for backoff
      if (execution.getExecutions() != 1 && retryPolicy.getMaxDelay() != null)
        delayNanos = (long) Math.min(delayNanos * retryPolicy.getDelayFactor(), retryPolicy.getMaxDelay().toNanos());
    }

    waitNanos = computedDelayNanos != -1 ? computedDelayNanos : delayNanos;

    // Adjust the wait time for jitter
    if (retryPolicy.getJitter() != null)
      waitNanos = randomDelay(waitNanos, retryPolicy.getJitter().toNanos(), Math.random());
    else if (retryPolicy.getJitterFactor() > 0.0)
      waitNanos = randomDelay(waitNanos, retryPolicy.getJitterFactor(), Math.random());

    // Adjust the wait time for max duration
    long elapsedNanos = execution.getElapsedTime().toNanos();
    if (retryPolicy.getMaxDuration() != null) {
      long maxRemainingWaitTime = retryPolicy.getMaxDuration().toNanos() - elapsedNanos;
      waitNanos = Math.min(waitNanos, maxRemainingWaitTime < 0 ? 0 : maxRemainingWaitTime);
      if (waitNanos < 0)
        waitNanos = 0;
    }

    // Calculate result
    boolean maxRetriesExceeded = retryPolicy.getMaxRetries() != -1
        && execution.getExecutions() > retryPolicy.getMaxRetries();
    boolean maxDurationExceeded = retryPolicy.getMaxDuration() != null
        && elapsedNanos > retryPolicy.getMaxDuration().toNanos();
    retriesExceeded = maxRetriesExceeded || maxDurationExceeded;
    boolean isAbortable = retryPolicy.canAbortFor(pr.result, pr.failure);
    boolean isRetryable = retryPolicy.canRetryFor(pr.result, pr.failure);
    boolean shouldRetry = !retriesExceeded && !pr.noResult && !isAbortable && retryPolicy.allowsRetries()
        && isRetryable;
    boolean completed = isAbortable || !shouldRetry;
    boolean success = completed && !isAbortable && !isRetryable && pr.failure == null;

    // Call listeners
    if (!success)
      eventHandler.handleFailedAttempt(pr.result, pr.failure, execution);
    if (isAbortable)
      eventHandler.handleAbort(pr.result, pr.failure, execution);
    else if (!success && retriesExceeded)
      eventHandler.handleRetriesExceeded(pr.result, pr.failure, execution);

    return pr.with(waitNanos, completed, success);
  }
}
