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

import net.jodah.failsafe.event.EventListener;
import net.jodah.failsafe.event.ExecutionCompletedEvent;
import net.jodah.failsafe.internal.util.Assert;

/**
 * Builds policies.
 *
 * @param <S> self type
 * @param <C> config type
 * @param <R> result type
 * @author Jonathan Halterman
 */
@SuppressWarnings("unchecked")
public abstract class PolicyBuilder<S, C extends PolicyConfig<R>, R> implements PolicyListeners<S, R> {
  protected C config;

  protected PolicyBuilder(C config) {
    this.config = config;
  }

  public S onFailure(EventListener<ExecutionCompletedEvent<R>> listener) {
    config.failureListener = Assert.notNull(listener, "listener");
    return (S) this;
  }

  @Override
  public S onSuccess(EventListener<ExecutionCompletedEvent<R>> listener) {
    config.successListener = Assert.notNull(listener, "listener");
    return (S) this;
  }
}
