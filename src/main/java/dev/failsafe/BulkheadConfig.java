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

import java.time.Duration;

/**
 * Configuration for a {@link Bulkhead}.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public class BulkheadConfig<R> extends PolicyConfig<R> {
  int maxConcurrency;
  Duration maxWaitTime;
  boolean fair;

  BulkheadConfig(int maxConcurrency) {
    this.maxConcurrency = maxConcurrency;
  }

  BulkheadConfig(BulkheadConfig<R> config) {
    super(config);
    maxConcurrency = config.maxConcurrency;
    maxWaitTime = config.maxWaitTime;
    fair = config.fair;
  }

  /**
   * Returns that max concurrent executions that are permitted within the bulkhead.
   *
   * @see Bulkhead#builder(int)
   */
  public int getMaxConcurrency() {
    return maxConcurrency;
  }

  /**
   * Returns the max time to wait for permits to be available. If permits cannot be acquired before the max wait time is
   * exceeded, then the bulkhead will throw {@link BulkheadFullException}.
   *
   * @see BulkheadBuilder#withMaxWaitTime(Duration)
   */
  public Duration getMaxWaitTime() {
    return maxWaitTime;
  }

  /**
   * Returns whether the Bulkhead is fair in permitting waiting executions in order.
   *
   * @see BulkheadBuilder#withFairness()
   */
  public boolean isFair() {
    return fair;
  }
}
