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
import net.jodah.failsafe.ExecutionResult;

public interface EventHandler {
  void handleAbort(ExecutionResult result, ExecutionContext context);

  void handleComplete(ExecutionResult result, ExecutionContext context);

  void handleFailedAttempt(ExecutionResult result, ExecutionContext context);

  void handleRetriesExceeded(ExecutionResult result, ExecutionContext context);

  void handleRetry(ExecutionResult result, ExecutionContext context);
}
