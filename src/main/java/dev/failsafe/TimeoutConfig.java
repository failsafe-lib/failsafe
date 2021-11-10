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

import java.time.Duration;

/**
 * Configuration for a {@link Timeout}.
 * <p>
 * This class is threadsafe.
 * </p>
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public class TimeoutConfig<R> extends PolicyConfig<R> {
  Duration timeout;
  boolean canInterrupt;

  TimeoutConfig(Duration timeout, boolean canInterrupt) {
    this.timeout = timeout;
    this.canInterrupt = canInterrupt;
  }

  TimeoutConfig(TimeoutConfig<R> config) {
    super(config);
    timeout = config.timeout;
    canInterrupt = config.canInterrupt;
  }

  /**
   * Returns the timeout duration.
   */
  public Duration getTimeout() {
    return timeout;
  }

  /**
   * Returns whether the policy can interrupt an execution if the timeout is exceeded.
   *
   * @see TimeoutBuilder#withInterrupt()
   */
  public boolean canInterrupt() {
    return canInterrupt;
  }
}
