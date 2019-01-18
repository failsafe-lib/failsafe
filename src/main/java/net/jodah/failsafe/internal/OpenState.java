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

public class OpenState extends CircuitState {
  private final CircuitBreaker circuit;
  private final long startTime = System.nanoTime();

  public OpenState(CircuitBreaker circuit, CircuitState previousState) {
    this.circuit = circuit;
    this.bitSet = previousState.bitSet;
  }

  @Override
  public boolean allowsExecution(CircuitBreakerStats stats) {
    if (System.nanoTime() - startTime >= circuit.getDelay().toNanos()) {
      circuit.halfOpen();
      return true;
    }

    return false;
  }

  @Override
  public State getState() {
    return State.OPEN;
  }
}