/*
 * Copyright 2021 the original author or authors.
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
package dev.failsafe;

import dev.failsafe.internal.BulkheadImpl;

import java.time.Duration;

/**
 * A bulkhead allows you to restrict concurrent executions as a way of preventing system overload.
 * <p>
 * This class is threadsafe.
 * </p>
 *
 * @param <R> result type
 * @author Jonathan Halterman
 * @see BulkheadConfig
 * @see BulkheadBuilder
 * @see BulkheadFullException
 */
public interface Bulkhead<R> extends Policy<R> {
  /**
   * Returns a Bulkhead for the {@code maxConcurrency} that has {@link BulkheadBuilder#withMaxWaitTime(Duration) zero
   * wait} and is {@link BulkheadBuilder#withFairness() not fair} by default.
   *
   * @param maxConcurrency controls the max concurrent executions that are permitted within the bulkhead
   */
  static <R> BulkheadBuilder<R> builder(int maxConcurrency) {
    return new BulkheadBuilder<>(maxConcurrency);
  }

  /**
   * Creates a new BulkheadBuilder that will be based on the {@code config}. The built bulkhead is {@link
   * BulkheadBuilder#withFairness() not fair} by default.
   */
  static <R> BulkheadBuilder<R> builder(BulkheadConfig<R> config) {
    return new BulkheadBuilder<>(config);
  }

  /**
   * Returns a Bulkhead for the {@code maxConcurrency} that has {@link BulkheadBuilder#withMaxWaitTime(Duration) zero
   * wait} and is {@link BulkheadBuilder#withFairness() not fair} by default. Alias for {@code
   * Bulkhead.builder(maxConcurrency).build()}. To configure additional options on a Bulkhead, use {@link #builder(int)}
   * instead.
   *
   * @param maxConcurrency controls the max concurrent executions that are permitted within the bulkhead
   * @see #builder(int)
   */
  static <R> Bulkhead<R> of(int maxConcurrency) {
    return new BulkheadImpl<>(new BulkheadConfig<>(maxConcurrency));
  }

  /**
   * Returns the {@link BulkheadConfig} that the Bulkhead was built with.
   */
  @Override
  BulkheadConfig<R> getConfig();

  /**
   * Attempts to acquire a permit to perform an execution against within the bulkhead, waiting until one is available or
   * the thread is interrupted. After execution is complete, the permit should be {@link #releasePermit() released} back
   * to the bulkhead.
   *
   * @throws InterruptedException if the current thread is interrupted while waiting to acquire a permit
   * @see #tryAcquirePermit()
   */
  void acquirePermit() throws InterruptedException;

  /**
   * Attempts to acquire a permit to perform an execution within the bulkhead, waiting up to the {@code maxWaitTime}
   * until one is available, else throwing {@link BulkheadFullException} if a permit will not be available in time.
   * After execution is complete, the permit should be {@link #releasePermit() released} back to the bulkhead.
   *
   * @throws NullPointerException if {@code maxWaitTime} is null
   * @throws BulkheadFullException if the bulkhead cannot acquire a permit within the {@code maxWaitTime}
   * @throws InterruptedException if the current thread is interrupted while waiting to acquire a permit
   * @see #tryAcquirePermit(Duration)
   */
  default void acquirePermit(Duration maxWaitTime) throws InterruptedException {
    if (!tryAcquirePermit(maxWaitTime))
      throw new BulkheadFullException(this);
  }

  /**
   * Tries to acquire a permit to perform an execution within the bulkhead, returning immediately without waiting. After
   * execution is complete, the permit should be {@link #releasePermit() released} back to the bulkhead.
   *
   * @return whether the requested {@code permits} are successfully acquired or not
   */
  boolean tryAcquirePermit();

  /**
   * Tries to acquire a permit to perform an execution within the bulkhead, waiting up to the {@code maxWaitTime} until
   * they are available. After execution is complete, the permit should be {@link #releasePermit() released} back to the
   * bulkhead.
   *
   * @return whether a permit is successfully acquired
   * @throws NullPointerException if {@code maxWaitTime} is null
   * @throws InterruptedException if the current thread is interrupted while waiting to acquire a permit
   */
  boolean tryAcquirePermit(Duration maxWaitTime) throws InterruptedException;

  /**
   * Releases a permit to execute.
   */
  void releasePermit();
}
