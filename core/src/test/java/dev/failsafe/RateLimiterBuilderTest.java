/*
 * Copyright 2021 the original author or authors.
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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

@Test
public class RateLimiterBuilderTest {
  public void shouldCreateBuilderFromExistingConfig() {
    RateLimiterConfig<Object> initialConfig = RateLimiter.smoothBuilder(Duration.ofMillis(10))
      .withMaxWaitTime(Duration.ofSeconds(10))
      .onSuccess(e -> {
      }).config;
    RateLimiterConfig<Object> newConfig = RateLimiter.builder(initialConfig).config;
    assertEquals(newConfig.maxRate, Duration.ofMillis(10));
    assertEquals(newConfig.maxWaitTime, Duration.ofSeconds(10));
    assertNotNull(newConfig.successListener);
  }

  /**
   * Asserts that the smooth rate limiter factory methods are equal.
   */
  public void shouldBuildEqualSmoothLimiters() {
    Duration maxRate1 = RateLimiter.smoothBuilder(100, Duration.ofSeconds(1)).config.getMaxRate();
    Duration maxRate2 = RateLimiter.smoothBuilder(Duration.ofMillis(10)).config.getMaxRate();
    assertEquals(maxRate1, maxRate2);

    maxRate1 = RateLimiter.smoothBuilder(20, Duration.ofMillis(300)).config.getMaxRate();
    maxRate2 = RateLimiter.smoothBuilder(Duration.ofMillis(15)).config.getMaxRate();
    assertEquals(maxRate1, maxRate2);
  }
}
