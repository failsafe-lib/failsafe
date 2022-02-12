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
package dev.failsafe;

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
public interface RetryPolicy<R> extends Policy<R> {
  /**
   * Creates a RetryPolicyBuilder that by default will build a RetryPolicy that allows 3 execution attempts max with no
   * delay, unless configured otherwise.
   *
   * @see #ofDefaults()
   */
  static <R> RetryPolicyBuilder<R> builder() {
    return new RetryPolicyBuilder<>();
  }

  /**
   * Creates a new RetryPolicyBuilder that will be based on the {@code config}.
   */
  static <R> RetryPolicyBuilder<R> builder(RetryPolicyConfig<R> config) {
    return new RetryPolicyBuilder<>(config);
  }

  /**
   * Creates a RetryPolicy that allows 3 execution attempts max with no delay. To configure additional options on a
   * RetryPolicy, use {@link #builder()} instead.
   *
   * @see #builder()
   */
  static <R> RetryPolicy<R> ofDefaults() {
    return RetryPolicy.<R>builder().build();
  }

  /**
   * Returns the {@link RetryPolicyConfig} that the RetryPolicy was built with.
   */
  @Override
  RetryPolicyConfig<R> getConfig();
}
