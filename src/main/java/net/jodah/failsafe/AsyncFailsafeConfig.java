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

import java.util.concurrent.ScheduledExecutorService;

import net.jodah.failsafe.event.ContextualResultListener;
import net.jodah.failsafe.function.CheckedBiConsumer;
import net.jodah.failsafe.function.CheckedConsumer;
import net.jodah.failsafe.util.concurrent.Scheduler;

/**
 * Async Failsafe configuration.
 * <p>
 * Async execution event listeners are called asynchronously on the {@link Scheduler} or
 * {@link ScheduledExecutorService} associated with the Failsafe call.
 * 
 * @author Jonathan Halterman
 * @param <R> result type
 * @param <F> failsafe type - {@link SyncFailsafe} or {@link AsyncFailsafe}
 */
@SuppressWarnings("unchecked")
public class AsyncFailsafeConfig<R, F> extends FailsafeConfig<R, F> {
  final Scheduler scheduler;

  AsyncFailsafeConfig(FailsafeConfig<R, ?> config, Scheduler scheduler) {
    super(config);
    this.scheduler = scheduler;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler when an
   * execution is aborted according to the retry policy.
   */
  public F onAbortAsync(ContextualResultListener<? extends R, ? extends Throwable> listener) {
    registry().abort().add(Listeners.of(listener, null, scheduler));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler when an
   * execution is aborted according to the retry policy.
   */
  public F onAbortAsync(CheckedConsumer<? extends Throwable> listener) {
    registry().abort().add(Listeners.of(Listeners.<R>of(listener), null, scheduler));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler when an
   * execution is aborted according to the retry policy.
   */
  public F onAbortAsync(CheckedBiConsumer<? extends R, ? extends Throwable> listener) {
    registry().abort().add(Listeners.of(Listeners.of(listener), null, scheduler));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler when an
   * execution is completed.
   */
  public F onCompleteAsync(ContextualResultListener<? extends R, ? extends Throwable> listener) {
    registry().complete().add(Listeners.of(listener, null, scheduler));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler when an
   * execution is completed.
   */
  public F onCompleteAsync(CheckedBiConsumer<? extends R, ? extends Throwable> listener) {
    registry().complete().add(Listeners.of(Listeners.of(listener), null, scheduler));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler after a
   * failed execution attempt.
   */
  public F onFailedAttemptAsync(ContextualResultListener<? extends R, ? extends Throwable> listener) {
    registry().failedAttempt().add(Listeners.of(listener, null, scheduler));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler after a
   * failed execution attempt.
   */
  public F onFailedAttemptAsync(CheckedConsumer<? extends Throwable> listener) {
    registry().failedAttempt().add(Listeners.of(Listeners.<R>of(listener), null, scheduler));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler after a
   * failed execution attempt.
   */
  public F onFailedAttemptAsync(CheckedBiConsumer<? extends R, ? extends Throwable> listener) {
    registry().failedAttempt().add(Listeners.of(Listeners.of(listener), null, scheduler));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler after a
   * failure occurs that cannot be retried.
   */
  public F onFailureAsync(ContextualResultListener<? extends R, ? extends Throwable> listener) {
    registry().failure().add(Listeners.of(listener, null, scheduler));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler after a
   * failure occurs that cannot be retried.
   */
  public F onFailureAsync(CheckedConsumer<? extends Throwable> listener) {
    registry().failure().add(Listeners.of(Listeners.<R>of(listener), null, scheduler));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler after a
   * failure occurs that cannot be retried.
   */
  public F onFailureAsync(CheckedBiConsumer<? extends R, ? extends Throwable> listener) {
    registry().failure().add(Listeners.of(Listeners.of(listener), null, scheduler));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler when an
   * execution fails and the max retry attempts or duration are exceeded.
   */
  public F onRetriesExceededAsync(CheckedConsumer<? extends Throwable> listener) {
    registry().retriesExceeded().add(Listeners.of(Listeners.<R>of(listener), null, scheduler));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler when an
   * execution fails and the max retry attempts or duration are exceeded.
   */
  public F onRetriesExceededAsync(CheckedBiConsumer<? extends R, ? extends Throwable> listener) {
    registry().retriesExceeded().add(Listeners.of(Listeners.of(listener), null, scheduler));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler before a
   * retry is attempted.
   */
  public F onRetryAsync(ContextualResultListener<? extends R, ? extends Throwable> listener) {
    registry().retry().add(Listeners.of(listener, null, scheduler));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler before a
   * retry is attempted.
   */
  public F onRetryAsync(CheckedConsumer<? extends Throwable> listener) {
    registry().retry().add(Listeners.of(Listeners.<R>of(listener), null, scheduler));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler before a
   * retry is attempted.
   */
  public F onRetryAsync(CheckedBiConsumer<? extends R, ? extends Throwable> listener) {
    registry().retry().add(Listeners.of(Listeners.of(listener), null, scheduler));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler after a
   * successful execution.
   */
  public F onSuccessAsync(CheckedBiConsumer<? extends R, ExecutionContext> listener) {
    registry().success().add(Listeners.of(Listeners.ofResult(listener), null, scheduler));
    return (F) this;
  }

  /**
   * Registers the {@code listener} to be called asynchronously on Failsafe's configured executor or Scheduler after a
   * successful execution.
   */
  public F onSuccessAsync(CheckedConsumer<? extends R> listener) {
    registry().success().add(Listeners.of(Listeners.ofResult(listener), null, scheduler));
    return (F) this;
  }
}
