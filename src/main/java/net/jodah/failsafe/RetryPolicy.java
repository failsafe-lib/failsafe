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

import net.jodah.failsafe.event.ExecutionAttemptedEvent;
import net.jodah.failsafe.event.ExecutionCompletedEvent;
import net.jodah.failsafe.event.ExecutionScheduledEvent;
import net.jodah.failsafe.function.CheckedConsumer;

import java.time.Duration;

/**
 * A policy that defines when retries should be performed. See {@link RetryPolicyBuilder} for configuration options.
 * <p>
 * This class is threadsafe.
 * </p>
 *
 * @param <R> result type
 * @author Jonathan Halterman
 * @see RetryPolicyConfig
 * @see RetryPolicyBuilder
 */
public interface RetryPolicy<R> extends Policy<R>, ExecutionListeners<RetryPolicy<R>, R> {
  static <R> RetryPolicyBuilder<R> builder() {
    return new RetryPolicyBuilder<>();
  }

  static <R> RetryPolicy<R> ofDefaults() {
    return RetryPolicy.<R>builder().build();
  }

  /**
   * Returns the {@link RetryPolicyConfig} that the RetryPolicy was built with.
   */
  RetryPolicyConfig<R> getConfig();

  /**
   * Registers the {@code listener} to be called when an execution is aborted.
   * <p>Note: Any exceptions that are thrown from within the {@code listener} are ignored. To provide an alternative
   * result for a failed execution, use a {@link Fallback}.</p>
   */
  RetryPolicy<R> onAbort(CheckedConsumer<ExecutionCompletedEvent<R>> listener);

  /**
   * Registers the {@code listener} to be called when an execution attempt fails. You can also use {@link
   * #onFailure(CheckedConsumer) onFailure} to determine when the execution attempt fails <i>and</i> and all retries
   * have failed.
   * <p>Note: Any exceptions that are thrown from within the {@code listener} are ignored. To provide an alternative
   * result for a failed execution, use a {@link Fallback}.</p>
   */
  RetryPolicy<R> onFailedAttempt(CheckedConsumer<ExecutionAttemptedEvent<R>> listener);

  /**
   * Registers the {@code listener} to be called when an execution fails and the {@link
   * RetryPolicyBuilder#withMaxRetries(int) max retry attempts} or {@link RetryPolicyBuilder#withMaxDuration(Duration)
   * max duration} are exceeded.
   * <p>Note: Any exceptions that are thrown from within the {@code listener} are ignored. To provide an alternative
   * result for a failed execution, use a {@link Fallback}.</p>
   */
  RetryPolicy<R> onRetriesExceeded(CheckedConsumer<ExecutionCompletedEvent<R>> listener);

  /**
   * Registers the {@code listener} to be called when a retry is about to be attempted.
   * <p>Note: Any exceptions that are thrown from within the {@code listener} are ignored. To provide an alternative
   * result for a failed execution, use a {@link Fallback}.</p>
   *
   * @see #onRetryScheduled(CheckedConsumer)
   */
  RetryPolicy<R> onRetry(CheckedConsumer<ExecutionAttemptedEvent<R>> listener);

  /**
   * Registers the {@code listener} to be called when a retry for an async call is about to be scheduled. This method
   * differs from {@link #onRetry(CheckedConsumer)} since it is called when a retry is initially scheduled but before
   * any configured delay, whereas {@link #onRetry(CheckedConsumer) onRetry} is called after a delay, just before the
   * retry attempt takes place.
   * <p>
   * <p>Note: Any exceptions that are thrown from within the {@code listener} are ignored. To provide an alternative
   * result for a failed execution, use a {@link Fallback}.</p>
   *
   * @see #onRetry(CheckedConsumer)
   */
  RetryPolicy<R> onRetryScheduled(CheckedConsumer<ExecutionScheduledEvent<R>> listener);
}
