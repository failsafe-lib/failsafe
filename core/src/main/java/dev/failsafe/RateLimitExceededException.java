/*
 * Copyright 2021 the original author or authors.
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
 * Thrown when an execution exceeds or would exceed a {@link RateLimiter}.
 *
 * @author Jonathan Halterman
 */
public class RateLimitExceededException extends FailsafeException {
  private static final long serialVersionUID = 1L;

  private final RateLimiter<?> rateLimiter;

  public RateLimitExceededException(RateLimiter<?> rateLimiter) {
    this.rateLimiter = rateLimiter;
  }

  /** Returns the {@link RateLimiter} that caused the exception. */
  public RateLimiter<?> getRateLimiter() {
    return rateLimiter;
  }
}
