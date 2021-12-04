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
package dev.failsafe;

import org.testng.annotations.Test;

import java.time.Duration;

import static dev.failsafe.testing.Asserts.assertThrows;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

@Test
public class CircuitBreakerTest {
  public void shouldDefaultDelay() throws Throwable {
    CircuitBreaker<Object> breaker = CircuitBreaker.ofDefaults();
    breaker.recordFailure();
    Thread.sleep(100);
    assertTrue(breaker.isOpen());
  }

  public void shouldGetSuccessAndFailureStats() {
    // Given
    CircuitBreaker<Object> breaker = CircuitBreaker.builder()
      .withFailureThreshold(5, 10)
      .withSuccessThreshold(15, 20)
      .build();

    // When
    for (int i = 0; i < 7; i++)
      if (i % 2 == 0)
        breaker.recordSuccess();
      else
        breaker.recordFailure();

    // Then
    assertEquals(breaker.getFailureCount(), 3);
    assertEquals(breaker.getFailureRate(), 43);
    assertEquals(breaker.getSuccessCount(), 4);
    assertEquals(breaker.getSuccessRate(), 57);

    // When
    for (int i = 0; i < 15; i++)
      if (i % 4 == 0)
        breaker.recordFailure();
      else
        breaker.recordSuccess();

    // Then
    assertEquals(breaker.getFailureCount(), 2);
    assertEquals(breaker.getFailureRate(), 20);
    assertEquals(breaker.getSuccessCount(), 8);
    assertEquals(breaker.getSuccessRate(), 80);

    // When
    breaker.halfOpen();
    for (int i = 0; i < 15; i++)
      if (i % 3 == 0)
        breaker.recordFailure();
      else
        breaker.recordSuccess();

    // Then
    assertEquals(breaker.getFailureCount(), 5);
    assertEquals(breaker.getFailureRate(), 33);
    assertEquals(breaker.getSuccessCount(), 10);
    assertEquals(breaker.getSuccessRate(), 67);
  }
}
