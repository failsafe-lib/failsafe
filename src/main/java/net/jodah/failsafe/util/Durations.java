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

import net.jodah.failsafe.internal.util.Assert;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

/**
 * Utilities for working with durations.
 *
 * @author Jonathan Halterman
 */
public final class Durations {
  private Durations() {
  }

  /**
   * Converts the {@code unit} to a {@code ChronoUnit}.
   */
  public static Duration of(long duration, TimeUnit timeUnit) {
    ChronoUnit chronoUnit;
    switch (Assert.notNull(timeUnit, "timeUnit")) {
      case NANOSECONDS:
        chronoUnit = ChronoUnit.NANOS;
        break;
      case MICROSECONDS:
        chronoUnit = ChronoUnit.MICROS;
        break;
      case MILLISECONDS:
        chronoUnit = ChronoUnit.MILLIS;
        break;
      case SECONDS:
        chronoUnit = ChronoUnit.SECONDS;
        break;
      case MINUTES:
        chronoUnit = ChronoUnit.MINUTES;
        break;
      case HOURS:
        chronoUnit = ChronoUnit.HOURS;
        break;
      case DAYS:
        chronoUnit = ChronoUnit.DAYS;
        break;
      default:
        throw new IllegalArgumentException("Unknown TimeUnit");
    }
    return Duration.of(duration, chronoUnit);
  }
}
