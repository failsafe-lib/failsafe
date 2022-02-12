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
package dev.failsafe.internal;

import dev.failsafe.CircuitBreaker.State;
import dev.failsafe.CircuitBreakerConfig;
import dev.failsafe.CircuitBreakerOpenException;
import dev.failsafe.ExecutionContext;

import java.time.Duration;

/**
 * The state of a circuit.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
abstract class CircuitState<R> {
  final CircuitBreakerImpl<R> breaker;
  final CircuitBreakerConfig<R> config;
  volatile CircuitStats stats;

  CircuitState(CircuitBreakerImpl<R> breaker, CircuitStats stats) {
    this.breaker = breaker;
    this.config = breaker.getConfig();
    this.stats = stats;
  }

  public Duration getRemainingDelay() {
    return Duration.ZERO;
  }

  public CircuitStats getStats() {
    return stats;
  }

  public abstract State getState();

  public synchronized void recordFailure(ExecutionContext<R> context) {
    stats.recordFailure();
    checkThreshold(context);
    releasePermit();
  }

  public synchronized void recordSuccess() {
    stats.recordSuccess();
    checkThreshold(null);
    releasePermit();
  }

  public void handleConfigChange() {
  }

  void checkThreshold(ExecutionContext<R> context) {
  }

  abstract boolean tryAcquirePermit();

  void releasePermit() {
  }
}