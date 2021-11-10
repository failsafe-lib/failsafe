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
package dev.failsafe;

import dev.failsafe.event.EventListener;
import dev.failsafe.event.CircuitBreakerStateChangedEvent;

/**
 * Configures listeners for a {@link CircuitBreaker}.
 *
 * @param <S> self type
 * @param <R> result type
 * @author Jonathan Halterman
 */
public interface CircuitBreakerListeners<S, R> extends PolicyListeners<S, R> {
  /**
   * Calls the {@code listener} when the circuit is closed.
   * <p>Note: Any exceptions that are thrown from within the {@code listener} are ignored.</p>
   *
   * @throws NullPointerException if {@code listener} is null
   */
  S onClose(EventListener<CircuitBreakerStateChangedEvent> listener);

  /**
   * Calls the {@code listener} when the circuit is half-opened.
   * <p>Note: Any exceptions that are thrown within the {@code listener} are ignored.</p>
   *
   * @throws NullPointerException if {@code listener} is null
   */
  S onHalfOpen(EventListener<CircuitBreakerStateChangedEvent> listener);

  /**
   * Calls the {@code listener} when the circuit is opened.
   * <p>Note: Any exceptions that are thrown within the {@code listener} are ignored.</p>
   *
   * @throws NullPointerException if {@code listener} is null
   */
  S onOpen(EventListener<CircuitBreakerStateChangedEvent> listener);
}
