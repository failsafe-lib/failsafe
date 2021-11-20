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

import dev.failsafe.function.AsyncRunnable;
import dev.failsafe.internal.TimeoutImpl;

import java.time.Duration;

/**
 * Builds {@link Timeout} instances.
 * <p>
 * This class is threadsafe.
 * </p>
 *
 * @param <R> result type
 * @author Jonathan Halterman
 * @see TimeoutConfig
 * @see TimeoutExceededException
 */
public class TimeoutBuilder<R> extends PolicyBuilder<TimeoutBuilder<R>, TimeoutConfig<R>, R> {
  TimeoutBuilder(Duration timeout) {
    super(new TimeoutConfig<>(timeout, false));
  }

  TimeoutBuilder(TimeoutConfig<R> config) {
    super(new TimeoutConfig<>(config));
  }

  /**
   * Builds a new {@link Timeout} using the builder's configuration.
   */
  public Timeout<R> build() {
    return new TimeoutImpl<>(new TimeoutConfig<>(config));
  }

  /**
   * Configures the policy to interrupt an execution in addition to cancelling it when the timeout is exceeded. For
   * synchronous executions this is done by calling {@link Thread#interrupt()} on the execution's thread. For
   * asynchronous executions this is done by calling {@link java.util.concurrent.Future#cancel(boolean)
   * Future.cancel(true)}. Executions can internally cooperate with interruption by checking {@link
   * Thread#isInterrupted()} or by handling {@link InterruptedException} where available.
   * <p>
   * Note: Only configure interrupts if the code being executed is designed to be interrupted.
   * <p>
   * <p>Note: interruption will have no effect when performing an {@link
   * FailsafeExecutor#getAsyncExecution(AsyncRunnable) async execution} since the async thread is unkown to
   * Failsafe.</p>
   */
  public TimeoutBuilder<R> withInterrupt() {
    config.canInterrupt = true;
    return this;
  }
}
