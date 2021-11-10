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

import net.jodah.failsafe.CircuitBreaker;
import net.jodah.failsafe.CircuitBreaker.State;
import org.testng.annotations.Test;

import java.time.Duration;

import static org.testng.Assert.*;

@Test
public class OpenStateTest {
  public void testAllowsExecution() throws Throwable {
    // Given
    CircuitBreakerImpl<Object> breaker = (CircuitBreakerImpl<Object>) CircuitBreaker.builder()
      .withDelay(Duration.ofMillis(100))
      .build();
    breaker.open();
    OpenState<Object> state = new OpenState<>(breaker, new ClosedState<>(breaker), breaker.getConfig().getDelay());
    assertTrue(breaker.isOpen());
    assertFalse(state.allowsExecution());

    // When
    Thread.sleep(110);

    // Then
    assertTrue(state.allowsExecution());
    assertEquals(breaker.getState(), State.HALF_OPEN);
  }

  public void testRemainingDelay() throws Throwable {
    // Given
    CircuitBreakerImpl<Object> breaker = (CircuitBreakerImpl<Object>) CircuitBreaker.builder()
      .withDelay(Duration.ofSeconds(1))
      .build();
    OpenState<Object> state = new OpenState<>(breaker, new ClosedState<>(breaker), breaker.getConfig().getDelay());

    // When / Then
    long remainingDelayMillis = state.getRemainingDelay().toMillis();
    assertTrue(remainingDelayMillis < 1000);
    assertTrue(remainingDelayMillis > 0);

    Thread.sleep(110);
    remainingDelayMillis = state.getRemainingDelay().toMillis();
    assertTrue(remainingDelayMillis < 900);
    assertTrue(remainingDelayMillis > 0);
  }

  public void testNoRemainingDelay() throws Throwable {
    // Given
    CircuitBreakerImpl<Object> breaker = (CircuitBreakerImpl<Object>) CircuitBreaker.builder()
      .withDelay(Duration.ofMillis(10))
      .build();
    assertEquals(breaker.getRemainingDelay(), Duration.ZERO);

    // When
    OpenState<Object> state = new OpenState<>(breaker, new ClosedState<>(breaker), breaker.getConfig().getDelay());
    Thread.sleep(50);

    // Then
    assertEquals(state.getRemainingDelay().toMillis(), 0);
  }
}
