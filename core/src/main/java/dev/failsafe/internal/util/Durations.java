/*
 * Copyright 2011-2021 the original author or authors.
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
package dev.failsafe.internal.util;

import java.time.Duration;

/**
 * Duration and long utilities.
 */
public final class Durations {
  static long MAX_SECONDS_PER_LONG = Long.MAX_VALUE / 1000_000_000L;
  static Duration MAX_SAFE_NANOS_DURATION = Duration.ofSeconds(MAX_SECONDS_PER_LONG);

  private Durations() {
  }

  /**
   * Returns either the {@code duration} else a Duration containing the max seconds that can safely be converted to
   * nanos without overflowing.
   */
  public static Duration ofSafeNanos(Duration duration) {
    return duration.getSeconds() < MAX_SECONDS_PER_LONG ? duration : MAX_SAFE_NANOS_DURATION;
  }
}
