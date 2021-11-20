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

import static org.testng.Assert.*;

@Test
public class CircuitBreakerBuilderTest {
  public void shouldCreateBuilderFromExistingConfig() {
    CircuitBreakerConfig<Object> initialConfig = CircuitBreaker.builder()
      .withDelay(Duration.ofMillis(55))
      .withFailureThreshold(10, 15)
      .withSuccessThreshold(20, 30)
      .onClose(e -> {
      }).config;
    CircuitBreakerConfig<Object> newConfig = CircuitBreaker.builder(initialConfig).config;
    assertEquals(newConfig.delay, Duration.ofMillis(55));
    assertEquals(newConfig.failureThreshold, 10);
    assertEquals(newConfig.failureThresholdingCapacity, 15);
    assertEquals(newConfig.successThreshold, 20);
    assertEquals(newConfig.successThresholdingCapacity, 30);
    assertNotNull(newConfig.closeListener);
  }
}
