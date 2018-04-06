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

import java.util.concurrent.TimeUnit;

import net.jodah.failsafe.RetryPolicy.DelayFunction;
import net.jodah.failsafe.internal.util.Assert;
import net.jodah.failsafe.util.Duration;

abstract class AbstractExecution extends ExecutionContext {
  final FailsafeConfig<Object, ?> config;
  final RetryPolicy retryPolicy;
  final CircuitBreaker circuitBreaker;

  // Mutable state
  long attemptStartTime;
  volatile Object lastResult;
  volatile Throwable lastFailure;
  volatile boolean completed;
  volatile boolean retriesExceeded;
  volatile boolean success;
  /** The fixed, backoff, random or computed delay time in nanoseconds. */
  private volatile long delayNanos = -1;
  /** The wait time, which is the delay time adjusted for jitter and max duration, in nanoseconds. */
  volatile long waitNanos;

  /**
   * Creates a new Execution for the {@code retryPolicy} and {@code circuitBreaker}.
   * 
   * @throws NullPointerException if {@code retryPolicy} is null
   */
  AbstractExecution(FailsafeConfig<Object, ?> config) {
    super(new Duration(System.nanoTime(), TimeUnit.NANOSECONDS));
    this.config = config;
    retryPolicy = config.retryPolicy;
    this.circuitBreaker = config.circuitBreaker;
  }

  /**
   * Returns the last failure that was recorded.
   */
  @SuppressWarnings("unchecked")
  public <T extends Throwable> T getLastFailure() {
    return (T) lastFailure;
  }

  /**
   * Returns the last result that was recorded.
   */
  @SuppressWarnings("unchecked")
  public <T> T getLastResult() {
    return (T) lastResult;
  }

  /**
   * Returns the time to wait before the next execution attempt. Returns {@code 0} if an execution has not yet occurred.
   */
  public Duration getWaitTime() {
    return new Duration(waitNanos, TimeUnit.NANOSECONDS);
  }

  /**
   * Returns whether the execution is complete.
   */
  public boolean isComplete() {
    return completed;
  }

  void before() {
    if (circuitBreaker != null)
      circuitBreaker.before();
    attemptStartTime = System.nanoTime();
  }

  /**
   * Records and attempts to complete the execution, returning true if complete else false.
   * 
   * @throws IllegalStateException if the execution is already complete
   */
  @SuppressWarnings("unchecked")
  boolean complete(Object result, Throwable failure, boolean checkArgs) {
    Assert.state(!completed, "Execution has already been completed");
    executions++;
    lastResult = result;
    lastFailure = failure;
    long elapsedNanos = getElapsedTime().toNanos();

    // Record the execution with the circuit breaker
    if (circuitBreaker != null) {
      Duration timeout = circuitBreaker.getTimeout();
      boolean timeoutExceeded = timeout != null && elapsedNanos >= timeout.toNanos();
      if (circuitBreaker.isFailure(result, failure) || timeoutExceeded)
        circuitBreaker.recordFailure();
      else
        circuitBreaker.recordSuccess();
    }

    // Determine the computed delay
    long computedDelayNanos = -1;
    DelayFunction<Object, Throwable> delayFunction = (DelayFunction<Object, Throwable>) retryPolicy.getDelayFn();
    if (delayFunction != null && retryPolicy.canApplyDelayFn(result, failure)) {
      Duration computedDelay = delayFunction.computeDelay(result, failure, this);
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
      if (executions != 1 && retryPolicy.getMaxDelay() != null)
        delayNanos = (long) Math.min(delayNanos * retryPolicy.getDelayFactor(), retryPolicy.getMaxDelay().toNanos());
    }

    waitNanos = computedDelayNanos != -1 ? computedDelayNanos : delayNanos;

    // Adjust the wait time for jitter
    if (retryPolicy.getJitter() != null)
      waitNanos = randomDelay(waitNanos, retryPolicy.getJitter().toNanos(), Math.random());
    else if (retryPolicy.getJitterFactor() > 0.0)
      waitNanos = randomDelay(waitNanos, retryPolicy.getJitterFactor(), Math.random());

    // Adjust the wait time for max duration
    if (retryPolicy.getMaxDuration() != null) {
      long maxRemainingWaitTime = retryPolicy.getMaxDuration().toNanos() - elapsedNanos;
      waitNanos = Math.min(waitNanos, maxRemainingWaitTime < 0 ? 0 : maxRemainingWaitTime);
      if (waitNanos < 0)
        waitNanos = 0;
    }

    boolean maxRetriesExceeded = retryPolicy.getMaxRetries() != -1 && executions > retryPolicy.getMaxRetries();
    boolean maxDurationExceeded = retryPolicy.getMaxDuration() != null
        && elapsedNanos > retryPolicy.getMaxDuration().toNanos();
    retriesExceeded = maxRetriesExceeded || maxDurationExceeded;
    boolean isAbortable = retryPolicy.canAbortFor(result, failure);
    boolean isRetryable = retryPolicy.canRetryFor(result, failure);
    boolean shouldRetry = !retriesExceeded && checkArgs && !isAbortable && retryPolicy.allowsRetries() && isRetryable;
    completed = isAbortable || !shouldRetry;
    success = completed && !isAbortable && !isRetryable && failure == null;

    // Call listeners
    if (!success)
      config.handleFailedAttempt(result, failure, this);
    if (isAbortable)
      config.handleAbort(result, failure, this);
    else {
      if (!success && retriesExceeded)
        config.handleRetriesExceeded(result, failure, this);
      if (completed)
        config.handleComplete(result, failure, this, success);
    }

    return completed;
  }

  static long randomDelayInRange(long delayMin, long delayMax, double random) {
    return (long) (random * (delayMax - delayMin)) + delayMin;
  }

  static long randomDelay(long delay, long jitter, double random) {
    double randomAddend = (1 - random * 2) * jitter;
    return (long) (delay + randomAddend);
  }

  static long randomDelay(long delay, double jitterFactor, double random) {
    double randomFactor = 1 + (1 - random * 2) * jitterFactor;
    return (long) (delay * randomFactor);
  }
}
