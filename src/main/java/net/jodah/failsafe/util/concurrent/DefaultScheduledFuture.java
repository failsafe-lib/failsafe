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

import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A default ScheduledFuture implementation.
 * 
 * @author Jonathan Halterman
 * @param <T> result type
 */
public class DefaultScheduledFuture<T> implements ScheduledFuture<T> {
  /**
   * @return {@code 0}
   */
  @Override
  public long getDelay(TimeUnit unit) {
    return 0;
  }

  /**
   * @return {@code 0}
   */
  @Override
  public int compareTo(Delayed o) {
    return 0;
  }

  /**
   * @return {@code false}
   */
  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    return false;
  }

  /**
   * @return {@code false}
   */
  @Override
  public boolean isCancelled() {
    return false;
  }

  /**
   * @return {@code false}
   */
  @Override
  public boolean isDone() {
    return false;
  }

  /**
   * @return {@code null}
   */
  @Override
  public T get() throws InterruptedException, ExecutionException {
    return null;
  }

  /**
   * @return {@code null}
   */
  @Override
  public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
    return null;
  }
}