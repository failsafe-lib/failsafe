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
import dev.failsafe.internal.util.Assert;

import java.time.Duration;

/**
 * Builds {@link Bulkhead} instances.
 * <p>
 * This class is <i>not</i> threadsafe.
 * </p>
 *
 * @param <R> result type
 * @author Jonathan Halterman
 * @see BulkheadConfig
 * @see BulkheadFullException
 */
public class BulkheadBuilder<R> extends PolicyBuilder<BulkheadBuilder<R>, BulkheadConfig<R>, R> {
  BulkheadBuilder(int maxConcurrency) {
    super(new BulkheadConfig<>(maxConcurrency));
  }

  BulkheadBuilder(BulkheadConfig<R> config) {
    super(new BulkheadConfig<>(config));
  }

  /**
   * Builds a new {@link Bulkhead} using the builder's configuration.
   */
  public Bulkhead<R> build() {
    return new BulkheadImpl<>(new BulkheadConfig<>(config));
  }

  /**
   * Configures the {@code maxWaitTime} to wait for permits to be available. If permits cannot be acquired before the
   * {@code maxWaitTime} is exceeded, then the bulkhead will throw {@link BulkheadFullException}.
   *
   * @throws NullPointerException if {@code maxWaitTime} is null
   */
  public BulkheadBuilder<R> withMaxWaitTime(Duration maxWaitTime) {
    config.maxWaitTime = Assert.notNull(maxWaitTime, "maxWaitTime");
    return this;
  }
}
