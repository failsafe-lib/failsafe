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
package dev.failsafe.internal;

import dev.failsafe.CircuitBreaker;
import dev.failsafe.internal.TimedCircuitStats.Clock;

/**
 * Stats for a circuit breaker.
 */
interface CircuitStats {
  static CircuitStats create(CircuitBreaker<?> breaker, int capacity, boolean supportsTimeBased,
    CircuitStats oldStats) {
    if (supportsTimeBased && breaker.getConfig().getFailureThresholdingPeriod() != null)
      return new TimedCircuitStats(TimedCircuitStats.DEFAULT_BUCKET_COUNT,
        breaker.getConfig().getFailureThresholdingPeriod(), new Clock(), oldStats);
    else if (capacity > 1) {
      return new CountingCircuitStats(capacity, oldStats);
    } else {
      return new DefaultCircuitStats();
    }
  }

  default void copyExecutions(CircuitStats oldStats) {
    for (int i = 0; i < oldStats.getSuccessCount(); i++)
      recordSuccess();
    for (int i = 0; i < oldStats.getFailureCount(); i++)
      recordFailure();
  }

  int getFailureCount();

  int getExecutionCount();

  int getSuccessCount();

  int getFailureRate();

  int getSuccessRate();

  void recordFailure();

  void recordSuccess();

  void reset();
}
