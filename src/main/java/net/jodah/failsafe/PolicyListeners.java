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
   * Registers the {@code listener} to be called when a {@link Policy} fails to handle an execution. This means that not
   * only was the supplied execution considered a failure by the policy, but that the policy was unable to produce a
   * successful result.
   * <p>Note: Any exceptions that are thrown from within the {@code listener} are ignored. To provide an alternative
   * result for a failed execution, use a {@link Fallback}.</p>
   */
  public S onFailure(CheckedConsumer<? extends ExecutionCompletedEvent<R>> listener) {
    failureListener = EventListener.of(Assert.notNull(listener, "listener"));
    return (S) this;
  }

  /**
   * Registers the {@code listener} to be called when a {@link Policy} succeeds in handling an execution. This means
   * that the supplied execution either succeeded, or if it failed, the policy was able to produce a successful result.
   * <p>Note: Any exceptions that are thrown from within the {@code listener} are ignored.</p>
   */
  public S onSuccess(CheckedConsumer<? extends ExecutionCompletedEvent<R>> listener) {
    successListener = EventListener.of(Assert.notNull(listener, "listener"));
    return (S) this;
  }
}
