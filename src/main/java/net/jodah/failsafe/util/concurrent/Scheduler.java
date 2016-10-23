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
package net.jodah.failsafe.util.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Schedules executions.
 * 
 * @author Jonathan Halterman
 * @see Schedulers
 * @see net.jodah.failsafe.util.concurrent.DefaultScheduledFuture
 */
public interface Scheduler {
  /**
   * Schedules the {@code callable} to be called after the {@code delay} for the {@code unit}.
   */
  ScheduledFuture<?> schedule(Callable<?> callable, long delay, TimeUnit unit);
}
