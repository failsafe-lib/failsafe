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
package net.jodah.failsafe.internal;

import net.jodah.failsafe.CircuitBreaker;
import net.jodah.failsafe.CircuitBreaker.State;

import java.time.Duration;

public class OpenState<R> extends CircuitState<R> {
  private final long startTime = System.nanoTime();
  private final long delayNanos;

  public OpenState(CircuitBreaker<R> breaker, CircuitState<R> previousState, Duration delay) {
    super(breaker, previousState.stats);
    this.delayNanos = delay.toNanos();
  }

  @Override
  public boolean allowsExecution() {
    if (System.nanoTime() - startTime >= delayNanos) {
      breaker.halfOpen();
      return true;
    }

    return false;
  }

  @Override
  public Duration getRemainingDelay() {
    long elapsedTime = System.nanoTime() - startTime;
    long remainingDelay = delayNanos - elapsedTime;
    return Duration.ofNanos(Math.max(remainingDelay, 0));
  }

  @Override
  public State getState() {
    return State.OPEN;
  }
}