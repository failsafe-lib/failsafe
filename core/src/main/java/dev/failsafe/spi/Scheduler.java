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
package dev.failsafe.spi;

import dev.failsafe.internal.util.DelegatingScheduler;

import java.util.concurrent.*;

/**
 * Schedules executions.
 *
 * @author Jonathan Halterman
 * @see DefaultScheduledFuture
 */
public interface Scheduler {
  /**
   * The default scheduler used by Failsafe if no other scheduler or {@link ScheduledExecutorService} is configured for
   * an execution.
   */
  Scheduler DEFAULT = DelegatingScheduler.INSTANCE;

  /**
   * Schedules the {@code callable} to be called after the {@code delay} for the {@code unit}.
   */
  ScheduledFuture<?> schedule(Callable<?> callable, long delay, TimeUnit unit);

  /**
   * Returns a Scheduler adapted from the {@code scheduledExecutorService}.
   */
  static Scheduler of(ScheduledExecutorService scheduledExecutorService) {
    return scheduledExecutorService::schedule;
  }

  /**
   * Returns a Scheduler adapted from the {@code executorService}.
   */
  static Scheduler of(ExecutorService executorService) {
    return executorService instanceof ScheduledExecutorService ?
      of((ScheduledExecutorService) executorService) :
      new DelegatingScheduler(executorService);
  }
}
