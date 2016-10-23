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
package net.jodah.failsafe.event;

import net.jodah.failsafe.ExecutionContext;

/**
 * Listens for an execution result, providing {@link ExecutionContext} that describe executions so far.
 * 
 * @author Jonathan Halterman
 * @param <R> result type
 * @param <F> failure type
 */
public interface ContextualResultListener<R, F extends Throwable> {
  /**
   * Handles an execution result.
   * 
   * @param result The execution result, else {@code null} if the call failed
   * @param failure The execution failure, else {@code null} if the call was successful
   */
  void onResult(R result, F failure, ExecutionContext context) throws Exception;
}
