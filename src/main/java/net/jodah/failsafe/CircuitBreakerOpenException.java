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

/**
 * Thrown when an execution is attempted while a configured {@link CircuitBreaker} is open.
 *
 * @author Jonathan Halterman
 */
public class CircuitBreakerOpenException extends FailsafeException {
  private static final long serialVersionUID = 1L;

  private final CircuitBreaker circuitBreaker;

  public CircuitBreakerOpenException(CircuitBreaker circuitBreaker) {
    this.circuitBreaker = circuitBreaker;
  }

  /** Retruns the {@link CircuitBreaker} that caused the exception. */
  public CircuitBreaker getCircuitBreaker() {
    return circuitBreaker;
  }
}
