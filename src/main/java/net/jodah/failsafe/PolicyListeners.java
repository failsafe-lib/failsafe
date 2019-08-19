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
package net.jodah.failsafe;

import net.jodah.failsafe.event.ExecutionCompletedEvent;
import net.jodah.failsafe.function.CheckedConsumer;
import net.jodah.failsafe.internal.EventListener;
import net.jodah.failsafe.internal.util.Assert;

/**
 * Policy listener configuration.
 *
 * @param <S> self type
 * @param <R> result type
 * @author Jonathan Halterman
 */
@SuppressWarnings("unchecked")
public class PolicyListeners<S, R> {
  EventListener failureListener;
  EventListener successListener;

  /**
   * Registers the {@code listener} to be called when an execution fails for a {@link Policy}.
   */
  public S onFailure(CheckedConsumer<? extends ExecutionCompletedEvent<R>> listener) {
    failureListener = EventListener.of(Assert.notNull(listener, "listener"));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called when an execution is successful for a {@link Policy}.
   */
  public S onSuccess(CheckedConsumer<? extends ExecutionCompletedEvent<R>> listener) {
    successListener = EventListener.of(Assert.notNull(listener, "listener"));
    return (S) this;
  }
}
