/*
 * Copyright 2026 the original author or authors.
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

/**
 * Thrown when a Failsafe execution fails with a checked exception, wrapping the underlying exception. Use
 * {@link Throwable#getCause()} to learn the cause of the failure. Synchronous executions propagate runtime exceptions
 * and errors directly. Asynchronous executions complete their futures exceptionally with the original failure instead
 * of wrapping it in this exception.
 */
public class FailsafeExecutionException extends FailsafeException {
  private static final long serialVersionUID = 1L;

  public FailsafeExecutionException(Throwable cause) {
    super(cause);
  }
}
