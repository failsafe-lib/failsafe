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
package net.jodah.failsafe.examples;

import net.jodah.failsafe.Execution;
import net.jodah.failsafe.RetryPolicy;

import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

@SuppressWarnings("unchecked")
public class RetryLoopExample {
  static List<Object> list;

  static {
    list = mock(List.class);
    when(list.size()).thenThrow(IllegalStateException.class, IllegalStateException.class).thenReturn(5);
  }

  public static void main(String... args) throws Throwable {
    RetryPolicy<Object> retryPolicy = RetryPolicy.builder()
      .handle(IllegalStateException.class)
      .withBackoff(10, 40, ChronoUnit.MILLIS)
      .build();
    Execution<Object> execution = Execution.of(retryPolicy);

    while (!execution.isComplete()) {
      try {
        execution.recordResult(list.size());
      } catch (IllegalStateException e) {
        execution.recordFailure(e);

        // Wait before retrying
        Thread.sleep(execution.getDelay().toMillis());
      }
    }

    assertEquals(execution.getLastResult(), 5);
    assertEquals(execution.getAttemptCount(), 3);
  }
}
