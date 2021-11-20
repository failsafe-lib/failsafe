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
public class FallbackBuilderTest {
  public void shouldCreateBuilderFromExistingConfig() throws Throwable {
    FallbackConfig<Integer> initialConfig = Fallback.builder(10).withAsync().onFailedAttempt(e -> {
    }).config;
    FallbackConfig<Integer> newConfig = Fallback.builder(initialConfig).config;
    assertEquals(newConfig.fallback.apply(null), Integer.valueOf(10));
    assertTrue(newConfig.async);
    assertNotNull(newConfig.failedAttemptListener);
  }
}
