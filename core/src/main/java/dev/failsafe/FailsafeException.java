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
package dev.failsafe;

/**
 * Thrown when a synchronous Failsafe execution fails with an {@link Exception}, wrapping the underlying exception. Use
 * {@link Throwable#getCause()} to learn the cause of the failure.
 *
 * @author Jonathan Halterman
 */
public class FailsafeException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public FailsafeException() {
  }

  public FailsafeException(Throwable t) {
    super(t);
  }
}
