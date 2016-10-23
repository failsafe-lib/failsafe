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
package net.jodah.failsafe.util;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * Duration, consisting of length of a time unit.
 */
public class Duration {
  public static final Duration NONE = new Duration(0, TimeUnit.MILLISECONDS);

  private final long length;
  private final TimeUnit timeUnit;

  public Duration(long length, TimeUnit timeUnit) {
    this.length = length;
    this.timeUnit = timeUnit;
  }

  @Override
  public boolean equals(Object o) {
    return this == o || Duration.class.isInstance(o) && toNanos() == Duration.class.cast(o).toNanos();
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(new Object[] { toNanos(), NANOSECONDS });
  }

  @Override
  public String toString() {
    return length + " " + timeUnit.toString().toLowerCase();
  }

  /**
   * Returns the Duration in nanoseconds.
   */
  public long toNanos() {
    return timeUnit.toNanos(length);
  }

  /**
   * Returns the Duration in milliseconds.
   */
  public long toMillis() {
    return timeUnit.toMillis(length);
  }

  /**
   * Returns the Duration in seconds.
   */
  public long toSeconds() {
    return timeUnit.toSeconds(length);
  }

  /**
   * Returns the Duration in minutes.
   */
  public long toMinutes() {
    return timeUnit.toMinutes(length);
  }

  /**
   * Returns the Duration in hours.
   */
  public long toHours() {
    return timeUnit.toHours(length);
  }

  /**
   * Returns the Duration in days.
   */
  public long toDays() {
    return timeUnit.toDays(length);
  }
}
