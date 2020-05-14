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
package net.jodah.failsafe.internal;

import org.testng.annotations.Test;

import java.util.function.Predicate;

@Test
public abstract class CircuitStatsTest {
  protected static <T extends CircuitStats> void recordSuccesses(T stats, int count) {
    for (int i = 0; i < count; i++)
      stats.recordSuccess();
  }

  protected static <T extends CircuitStats> void recordFailures(T stats, int count) {
    for (int i = 0; i < count; i++)
      stats.recordFailure();
  }

  protected static <T extends CircuitStats> void recordExecutions(T stats, int count,
    Predicate<Integer> successPredicate) {
    for (int i = 0; i < count; i++) {
      if (successPredicate.test(i))
        stats.recordSuccess();
      else
        stats.recordFailure();
    }
  }
}
