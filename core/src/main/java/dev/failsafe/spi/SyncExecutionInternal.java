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
package dev.failsafe.spi;

import dev.failsafe.Execution;

/**
 * Internal execution APIs.
 *
 * @param <R> result type
 * @author Jonathan Halterman
 */
public interface SyncExecutionInternal<R> extends ExecutionInternal<R>, Execution<R> {
  /**
   * Returns whether the execution is currently interrupted.
   */
  boolean isInterrupted();

  /**
   * Sets whether the execution is currently {@code interruptable}.
   */
  void setInterruptable(boolean interruptable);

  /**
   * Interrupts the execution.
   */
  void interrupt();

  /**
   * Returns a new copy of the SyncExecutionInternal if it is not standalone, else returns {@code this} since standalone
   * executions are referenced externally and cannot be replaced.
   */
  SyncExecutionInternal<R> copy();
}
