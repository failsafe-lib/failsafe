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
import dev.failsafe.event.ExecutionCompletedEvent;

/**
 * Configuration for a {@link Policy}.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public abstract class PolicyConfig<R> {
  volatile EventListener<ExecutionCompletedEvent<R>> successListener;
  volatile EventListener<ExecutionCompletedEvent<R>> failureListener;

  protected PolicyConfig() {
  }

  protected PolicyConfig(PolicyConfig<R> config) {
    successListener = config.successListener;
    failureListener = config.failureListener;
  }

  /**
   * Returns the success listener.
   *
   * @see PolicyListeners#onSuccess(EventListener)
   */
  public EventListener<ExecutionCompletedEvent<R>> getSuccessListener() {
    return successListener;
  }

  /**
   * Returns the failure listener.
   *
   * @see PolicyListeners#onFailure(EventListener)
   */
  public EventListener<ExecutionCompletedEvent<R>> getFailureListener() {
    return failureListener;
  }
}
