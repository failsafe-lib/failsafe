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
package net.jodah.failsafe.spi;

import net.jodah.failsafe.ExecutionListeners;
import net.jodah.failsafe.Policy;
import net.jodah.failsafe.event.ExecutionCompletedEvent;
import net.jodah.failsafe.function.CheckedConsumer;
import net.jodah.failsafe.internal.util.Assert;

/**
 * Abstract policy implementation that implements {@link ExecutionListeners}.
 *
 * @param <S> self type
 * @param <R> result type
 * @author Jonathan Halterman
 */
@SuppressWarnings("unchecked")
public abstract class AbstractPolicy<S extends Policy<R>, R> implements Policy<R>, ExecutionListeners<S, R> {
  protected volatile EventHandler<R> failureHandler;
  protected volatile EventHandler<R> successHandler;

  @Override
  public S onFailure(CheckedConsumer<ExecutionCompletedEvent<R>> listener) {
    failureHandler = EventHandler.ofCompleted(Assert.notNull(listener, "listener"));
    return (S) this;
  }

  @Override
  public S onSuccess(CheckedConsumer<ExecutionCompletedEvent<R>> listener) {
    successHandler = EventHandler.ofCompleted(Assert.notNull(listener, "listener"));
    return (S) this;
  }
}
